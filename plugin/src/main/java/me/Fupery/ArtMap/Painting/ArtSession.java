package me.Fupery.ArtMap.Painting;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.api.Colour.ArtDye;
import me.Fupery.ArtMap.api.Colour.DyeType;
import me.Fupery.ArtMap.api.Colour.Palette;
import me.Fupery.ArtMap.api.Config.Lang;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.Event.PlayerMountEaselEvent;
import me.Fupery.ArtMap.IO.Database.Map;
import me.Fupery.ArtMap.Painting.Brushes.Dropper;
import me.Fupery.ArtMap.Painting.Brushes.Dye;
import me.Fupery.ArtMap.Painting.Brushes.Fill;
import me.Fupery.ArtMap.Painting.Brushes.Flip;
import me.Fupery.ArtMap.Painting.Artkit.ArtkitColor;
import me.Fupery.ArtMap.Painting.Artkit.ArtkitItems;
import me.Fupery.ArtMap.Painting.Artkit.ArtkitPalette;
import me.Fupery.ArtMap.Painting.Artkit.ArtkitShapeBrush;
import me.Fupery.ArtMap.Painting.Artkit.ArtkitState;
import me.Fupery.ArtMap.Painting.Artkit.ArtkitTool;
import me.Fupery.ArtMap.Painting.Artkit.SelectedDye;
import me.Fupery.ArtMap.Recipe.ArtMaterial;
import me.Fupery.ArtMap.Utils.ItemUtils;
import me.Fupery.ArtMap.api.Painting.IArtSession;

public class ArtSession implements IArtSession {
    private final CanvasRenderer canvas;
    private final Brush DYE;
    private final Brush FILL;
    private final Brush FLIP;
    private final Brush DROPPER;
    private final SelectedDye SELECTED_DYE;
    private final ArtkitShapeBrush SHAPES;
    private final Easel easel;
    private final Map map;
    private final Player player;
    private final ArtkitState artkit;
    private Brush currentBrush;
    private ArtkitTool activeTool;
    private boolean freehandActive;
    private int strokeX;
    private int strokeY;
    private int shapeX = -1;
    private int shapeY = -1;
    private long lastStroke;
    private long lastFreehandUpdate;
    private ItemStack[] inventory;
    private static final HashMap<UUID, ItemStack[]> artkitHotbars = new HashMap<>();

    private boolean active = false;
    private boolean dirty = true;
    private int artkitPage = 0;

    ArtSession(Player player, Easel easel, Map map, int yawOffset) {
        this.easel = easel;
        this.player = player;
        this.artkit = new ArtkitState();
        canvas = new CanvasRenderer(map, yawOffset);
        currentBrush = null;
        lastStroke = System.currentTimeMillis();
        DYE = new Dye(canvas, player);
        DROPPER = new Dropper(canvas, player);
        FILL = new Fill(canvas, player, (Dropper) DROPPER);
        FLIP = new Flip(canvas, player);
        SELECTED_DYE = new SelectedDye(canvas, player);
        SHAPES = new ArtkitShapeBrush(canvas);
        this.map = map;
    }

    public boolean start(Player player) throws SQLException, IOException {
        PlayerMountEaselEvent event = new PlayerMountEaselEvent(player, easel);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        boolean seated = easel.seatUser(player);
        if (!seated) {
            return false;
        }

        // Run tasks
        try {
            ArtMap.instance().getArtDatabase().restoreMap(map, true, false);
            ArtMap.instance().getScheduler().SYNC.runLater(player, () -> {
                if (player.getVehicle() != null)
                    Lang.ActionBar.PAINTING.send(player);
            }, 30);
            map.setRenderer(canvas);
            persistMap(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            player.sendMessage(Lang.EASEL_RESTORE_ERROR.get());
            ArtMap.instance().getLogger().log(Level.SEVERE, "Error restoring painting on easel.", e);
            event.setCancelled(true);
            return false;
        }
        return true;
    }

    void paint(ItemStack brush, Brush.BrushAction action) {
        if (ArtkitItems.isBrush(brush)) {
            paintArtkit(brush, action);
            return;
        }
        dirty = true;
        if (currentBrush == null || !currentBrush.checkMaterial(brush)) {
            if (currentBrush != null)
                currentBrush.clean();
            currentBrush = getBrushType(brush);
        }
        if (currentBrush == null || canvas.isOffCanvas())
            return;

        long currentTime = System.currentTimeMillis();
        long strokeTime = currentTime - lastStroke;
        if (strokeTime > currentBrush.getCooldown()) {
            currentBrush.paint(action, brush, strokeTime);
            lastStroke = currentTime;
        }
    }

    private void paintArtkit(ItemStack brush, Brush.BrushAction action) {
        if (canvas.isOffCanvas()) return;
        dirty = true;
        long currentTime = System.currentTimeMillis();
        long strokeTime = currentTime - lastStroke;
        if (strokeTime <= 100) return;
        lastStroke = currentTime;

        byte colour = artkit.getColor().getByte();
        if (artkit.getTool() != activeTool) {
            activeTool = artkit.getTool();
            freehandActive = false;
            shapeX = -1;
        }
        switch (artkit.getTool()) {
            case FREEHAND:
                paintFreehandClick(colour);
                break;
            case LINE:
            case RECTANGLE:
            case RECTANGLE_FILLED:
            case CIRCLE:
            case CIRCLE_FILLED:
                paintShapeClick(colour);
                break;
            case FILL:
                if (action == Brush.BrushAction.LEFT_CLICK) {
                    ((Fill) FILL).paintColor(colour);
                }
                break;
            case FLIP:
                FLIP.paint(action, brush, strokeTime);
                break;
            case SHADE_DARK:
                shade(false);
                break;
            case SHADE_LIGHT:
                shade(true);
                break;
            case DROPPER:
                if (action == Brush.BrushAction.LEFT_CLICK) {
                    pickColour();
                }
                break;
            case DYE:
            default:
                SELECTED_DYE.setColour(colour);
                SELECTED_DYE.paint(action, brush, strokeTime);
                break;
        }
    }

    private void paintFreehandClick(byte colour) {
        byte[] pixel = canvas.getCurrentPixel();
        if (pixel == null) return;
        if (!freehandActive) {
            freehandActive = true;
            strokeX = pixel[0];
            strokeY = pixel[1];
            SHAPES.point(pixel[0], pixel[1], colour);
            return;
        }
        SHAPES.line(strokeX, strokeY, pixel[0], pixel[1], colour);
        freehandActive = false;
    }

    private void paintFreehandLook(byte colour) {
        if (!freehandActive) return;
        byte[] pixel = canvas.getCurrentPixel();
        if (pixel == null) return;
        if (pixel[0] == strokeX && pixel[1] == strokeY) return;
        SHAPES.line(strokeX, strokeY, pixel[0], pixel[1], colour);
        strokeX = pixel[0];
        strokeY = pixel[1];
        dirty = true;
        long now = System.currentTimeMillis();
        if (now - lastFreehandUpdate < 50) return;
        lastFreehandUpdate = now;
        ArtMap.instance().getScheduler().SYNC.run(player, () -> sendMap(player));
    }

    private void paintShapeClick(byte colour) {
        byte[] pixel = canvas.getCurrentPixel();
        if (pixel == null) return;
        if (shapeX < 0) {
            shapeX = pixel[0];
            shapeY = pixel[1];
            SHAPES.point(pixel[0], pixel[1], colour);
            return;
        }
        switch (artkit.getTool()) {
            case LINE:
                SHAPES.line(shapeX, shapeY, pixel[0], pixel[1], colour);
                break;
            case RECTANGLE:
                SHAPES.rectangle(shapeX, shapeY, pixel[0], pixel[1], colour, false);
                break;
            case RECTANGLE_FILLED:
                SHAPES.rectangle(shapeX, shapeY, pixel[0], pixel[1], colour, true);
                break;
            case CIRCLE:
                SHAPES.circle(shapeX, shapeY, pixel[0], pixel[1], colour, false);
                break;
            case CIRCLE_FILLED:
                SHAPES.circle(shapeX, shapeY, pixel[0], pixel[1], colour, true);
                break;
            default:
                break;
        }
        shapeX = -1;
    }

    /** Cancels a half-finished freehand stroke or shape when the ArtKit is opened. */
    public void cancelStroke() {
        freehandActive = false;
        shapeX = -1;
    }

    private void shade(boolean lighten) {
        byte[] pixel = canvas.getCurrentPixel();
        if (pixel == null) return;
        int colour = canvas.getPixel(pixel[0], pixel[1]) & 0xFF;
        int shade = colour % 4;
        if (lighten && shade < 3) {
            canvas.addPixel(pixel[0], pixel[1], (byte) (colour + 1));
        } else if (!lighten && shade > 0) {
            canvas.addPixel(pixel[0], pixel[1], (byte) (colour - 1));
        }
    }

    private void pickColour() {
        byte[] pixel = canvas.getCurrentPixel();
        if (pixel == null) return;
        ArtkitColor picked = ArtkitPalette.get(canvas.getPixel(pixel[0], pixel[1]) & 0xFF);
        artkit.setColor(picked);
        artkit.setTool(ArtkitTool.DYE);
        ArtkitItems.updateBrush(player, artkit);
        Lang.ITEM_SPONGE_MESSAGE.send(player);
    }

    public ArtkitState getArtkit() {
        return artkit;
    }

    private Brush getBrushType(ItemStack item) {
        for (Brush brush : new Brush[] { DYE, FILL, FLIP, DROPPER }) {
            if (brush.checkMaterial(item)) {
                return brush;
            }
        }
        return null;
    }

    public void updatePosition(float yaw, float pitch) {
        canvas.setYaw(yaw);
        canvas.setPitch(pitch);
        if (artkit.getTool() == ArtkitTool.FREEHAND) {
            paintFreehandLook(artkit.getColor().getByte());
        }
    }

    private void addKit(Player player) {
        PlayerInventory pInv = player.getInventory();
        this.inventory = pInv.getContents();
        pInv.setStorageContents(this.getArtKit(0));
        // restore hotbar
        if (artkitHotbars.containsKey(player.getUniqueId())) {
            ItemStack[] hotbar = artkitHotbars.get(player.getUniqueId());
            for (int i = 0; i < 9; i++) {
                player.getInventory().setItem(i, hotbar[i]);
            }
            player.getInventory().setItemInOffHand(hotbar[9]);
        }
		else {
            player.getInventory().setItemInOffHand(null);
		}
    }

    public void nextKitPage(Player player) {
        if (this.artkitPage < this.numPages()-1) {
            this.artkitPage++;
            this.updateKitPage(player);
        }
    }

    public void prevKitPage(Player player) {
        if (this.artkitPage > 0) {
            this.artkitPage--;
            this.updateKitPage(player);
        }
    }

    /* Set the contents of the inventory without replacing the hotkey bar. */
    private void updateKitPage(Player player) {
        ItemStack[] kit = this.getArtKit(this.artkitPage);
        ItemStack[] current = player.getInventory().getStorageContents();
        System.arraycopy(current, 0, kit, 0, 9);
        player.getInventory().setStorageContents(kit);
    }

    public boolean removeKit(Player player) {
        if (inventory == null) {
            return false;
        }
        // save hotbar + offhand
        ItemStack[] hotbar = new ItemStack[10];
        for (int i = 0; i < 9; i++) {
            hotbar[i] = player.getInventory().getItem(i);
        }
        hotbar[9] = player.getInventory().getItemInOffHand();
        artkitHotbars.put(player.getUniqueId(), hotbar);
        // clear item on cursor
        player.getOpenInventory().setCursor(null);
        
        player.getInventory().setContents(inventory);
        inventory = null;
        return true;
    }

    /**
     * Clear a players hotbar save. For instance on logout.
     * 
     * @param player The player to remove.
     */
    public static void clearHotbar(Player player) {
        artkitHotbars.remove(player.getUniqueId());
    }

    /**
     * @return True if the artsession has the artkit in use.
     */
    public boolean isInArtKit() {
        return this.inventory != null;
    }

    public Easel getEasel() {
        return easel;
    }

    public void end(Player player) throws SQLException, IOException {
        // isolate dismount housekeeping from the save so it can't block persisting
        try {
            //player.leaveVehicle();
            teleportAsync(player, player.getLocation().add(0, 0.25, 0));
            removeKit(player);
            easel.removeUser();
        } catch (Exception e) {
            ArtMap.instance().getLogger().log(Level.SEVERE, "Error dismounting player from easel.", e);
        }
        try {
            canvas.stop();
            persistMap(true);
            active = false;
        } catch (Exception e) {
            player.sendMessage(Lang.EASEL_SAVE_ERROR.get());
            ArtMap.instance().getLogger().log(Level.SEVERE, "Error saving painting on easel.", e);
        }
        // todo map renderer getting killed after save
    }

    public void persistMap(boolean resetRenderer) throws SQLException, IOException, NoSuchFieldException,
            IllegalAccessException {
        if (!dirty) return; //no caching required
        byte[] mapData = canvas.getMap();
        map.setMap(mapData, resetRenderer);
        ArtMap.instance().getArtDatabase().saveInProgressArt(this.map, mapData);
        dirty = false;
    }

    public boolean isActive() {
        return active;
    }

    private static void teleportAsync(Player player, org.bukkit.Location location) {
        try {
            java.lang.reflect.Method method = player.getClass().getMethod("teleportAsync", org.bukkit.Location.class);
            method.invoke(player, location);
        } catch (ReflectiveOperationException | RuntimeException e) {
            player.teleport(location);
        }
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    void sendMap(Player player) {
        if (dirty) map.update(player);
    }

    /**
     * Clear the current map on the easel.
     * 
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws IOException
     * @throws SQLException
     */
    public void clearMap() throws NoSuchFieldException, IllegalAccessException, SQLException, IOException {
        canvas.clear();
        dirty = true; // persistMap skips clean sessions, and a clear with no stroke after it must still be saved
        this.persistMap(true);
       //map.clear();
    }

    /**
     * Check if the last brush stroke is within the specified amount of time.
     * 
     * @param time The count of the time measurement.
     * @param unit The unit of the time measurement. ex: TimeInit.SECONDS
     * @return True if the last brush stroke is within the specified time.
     */
    public boolean lastPaintActionWithin(int time, TimeUnit unit) {
        return System.currentTimeMillis() < unit.toMillis(time) + lastStroke;
    }

    /* Artkit */
    private static WeakReference<List<ItemStack[]>> kitReference = new WeakReference<>(new LinkedList<>());

    private int numPages() {
        int numDyes = ArtMap.instance().getDyePalette().getDyes(DyeType.DYE).length;
        return (int) Math.ceil(numDyes / 18d);
    }

	// 27 inv slots + 9 hotbar slots = 36 slots
	private ItemStack[] getArtKit(int page) {
		// check the cache
		if (kitReference.get() != null && !kitReference.get().isEmpty()) {
			return kitReference.get().get(page).clone();
		}
		synchronized(kitReference) {
			if (kitReference.get() != null && !kitReference.get().isEmpty()) {
				return kitReference.get().get(page).clone();
			}
			// Hold the list strongly while it is built: the weak reference alone can be cleared mid-build.
			List<ItemStack[]> kit = new LinkedList<>();
			kitReference = new WeakReference<>(kit);
			Palette palette = ArtMap.instance().getDyePalette();
			int numDyes = palette.getDyes(DyeType.DYE).length;
			int pages = (int) Math.ceil(numDyes / 18d);
			for (int pg = 0; pg < pages; pg++) {
				ItemStack[] itemStack = new ItemStack[36]; // 27 inv slots
				Arrays.fill(itemStack, new ItemStack(Material.AIR));

				for (int j = 0; j < 18; j++) {
					if (((pg * 18) + j) >= numDyes) {
						break;
					}
					ArtDye dye = palette.getDyes(DyeType.DYE)[(pg * 18) + j];
					itemStack[j + 9] = ItemUtils.markArtkit(dye.toItem());
				}

				// if not first page add back button
				if (pg != 0) {
					ItemStack back = new ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA);
					ItemMeta meta = back.getItemMeta();
					meta.setDisplayName(Lang.ARTKIT_PREV.get());
					back.setItemMeta(meta);
					itemStack[27] = ItemUtils.markArtkitBack(back);
				}
				// if not last page add next button
				if (pg < pages - 1) {
					ItemStack next = new ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA);
					ItemMeta meta = next.getItemMeta();
					meta.setDisplayName(Lang.ARTKIT_NEXT.get());
					next.setItemMeta(meta);
					itemStack[35] = ItemUtils.markArtkitNext(next);
				}

				itemStack[29] = ArtMaterial.FEATHER.getItem();
				itemStack[30] = ArtMaterial.COAL.getItem();
				itemStack[31] = ArtMaterial.COMPASS.getItem();
				itemStack[32] = ArtMaterial.PAINTBUCKET.getItem();
				itemStack[33] = ArtMaterial.SPONGE.getItem();
				if(!ArtMap.instance().getConfiguration().DISABLE_PAINTBRUSH) {
					itemStack[34] = ArtMaterial.PAINT_BRUSH.getItem();
				}
				kit.add(itemStack);
			}
			return kit.get(page).clone();
		}
    }
}
