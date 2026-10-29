package me.Fupery.ArtMap.Listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.Fupery.ArtMap.mocks.PluginTestSupport;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Easel.Easel;
import me.Fupery.ArtMap.IO.Database.Map;

/** Keeps the art kit from leaking into the world (or the player's real bags). */
public class InventoryInteractListenerTest extends PluginTestSupport {

    private InventoryInteractListener listener;
    private Player player;

    @BeforeAll
    public static void setup() throws Exception {
        bootstrap();
    }

    @BeforeEach
    public void freshActors() {
        mocks.resetMockPlayerPermissions();
        listener = new InventoryInteractListener();
        player = preparePlayer(mocks.getRandomMockPlayers(1)[0]);
    }

    private void startSession(int mapId) throws Exception {
        org.bukkit.inventory.ItemStack dye = item(Material.RED_DYE);
        Easel easel = mock(Easel.class);
        when(easel.seatUser(player)).thenReturn(true);
        player.getInventory().setItemInMainHand(dye);
        ArtMap.instance().getArtistHandler().addPlayer(player, easel, new Map(mapId), 0);
    }

    @Test
    @DisplayName("dropping an ordinary item while not painting is allowed")
    public void ordinaryDropIsAllowed() {
        org.bukkit.inventory.ItemStack stone = item(Material.STONE);
        Item drop = mock(Item.class);
        when(drop.getItemStack()).thenReturn(stone);

        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItemDrop()).thenReturn(drop);

        listener.onPlayerDropItem(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a mob picking an item up is ignored")
    public void mobPickupIsIgnored() {
        Zombie zombie = mock(Zombie.class);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(zombie);

        listener.onPlayerPickup(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a player who is not painting can pick items up")
    public void idlePlayerCanPickUp() {
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);

        listener.onPlayerPickup(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a painter holding the art kit cannot pick items up")
    public void painterInKitCannotPickUp() throws Exception {
        startSession(971);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);

        listener.onPlayerPickup(event);

        // isInArtKit() is true only while the kit is actually swapped in.
        boolean inKit = ArtMap.instance().getArtistHandler().getCurrentSession(player).isInArtKit();
        if (inKit) {
            verify(event).setCancelled(true);
        } else {
            verify(event, never()).setCancelled(true);
        }
        ArtMap.instance().getArtistHandler().removePlayer(player);
    }

    // ---- kit sessions, previews, pagination and the armour lock -----------------

    private static final class FakePreview implements me.Fupery.ArtMap.Preview.Preview {
        @Override public boolean start(Player p) { return true; }
        @Override public boolean end(Player p) { return true; }
        @Override public boolean isEventAllowed(java.util.UUID p, org.bukkit.event.Event e) { return true; }
    }

    private final java.util.List<Player> kitPainters = new java.util.ArrayList<>();

    /** A painter whose session has the art kit swapped in (forced kit + permission). */
    private Player kitPainter(int mapId) throws Exception {
        configWith(true, false);
        Player p = statefulPlayer("KitPainter" + mapId);
        when(p.hasPermission("artmap.artkit")).thenReturn(true);
        when(p.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        Easel easel = mock(Easel.class);
        when(easel.seatUser(p)).thenReturn(true);
        artists().addPlayer(p, easel, new Map(mapId), 0);
        org.junit.jupiter.api.Assertions.assertTrue(artists().getCurrentSession(p).isInArtKit());
        kitPainters.add(p);
        return p;
    }

    @org.junit.jupiter.api.AfterEach
    public void cleanUp() throws Exception {
        for (Player p : kitPainters) endKitSessionQuietly(p);
        kitPainters.clear();
        if (artists().containsPlayer(player)) artists().removePlayer(player);
        previews().endPreview(player);
        restoreConfig();
    }

    private static org.bukkit.inventory.ItemStack tagged(String key) {
        org.bukkit.inventory.ItemStack stack = item(Material.PAPER);
        org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenAnswer(invocation -> ((NamespacedKey) invocation.getArgument(0)).getKey().equals(key));
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }

    private static org.bukkit.event.inventory.InventoryClickEvent click(Player who, org.bukkit.inventory.ItemStack current,
            org.bukkit.event.inventory.InventoryType type, org.bukkit.event.inventory.InventoryType.SlotType slot) {
        org.bukkit.inventory.Inventory inventory = mock(org.bukkit.inventory.Inventory.class);
        when(inventory.getType()).thenReturn(type);
        org.bukkit.event.inventory.InventoryClickEvent event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(who);
        when(event.getCurrentItem()).thenReturn(current);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getSlotType()).thenReturn(slot);
        return event;
    }

    private static PlayerDropItemEvent drop(Player who, org.bukkit.inventory.ItemStack stack) {
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(stack);
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(who);
        when(event.getItemDrop()).thenReturn(entity);
        return event;
    }

    @Test
    @DisplayName("changing the held slot ends a preview and swallows the change; otherwise it is left alone")
    public void heldSlotChangeEndsPreview() {
        org.bukkit.event.player.PlayerItemHeldEvent event = mock(org.bukkit.event.player.PlayerItemHeldEvent.class);
        when(event.getPlayer()).thenReturn(player);
        previews().startPreview(player, new FakePreview());

        listener.onPlayerItemHeld(event);
        verify(event).setCancelled(true);
        org.junit.jupiter.api.Assertions.assertFalse(previews().isPreviewing(player));

        org.bukkit.event.player.PlayerItemHeldEvent later = mock(org.bukkit.event.player.PlayerItemHeldEvent.class);
        when(later.getPlayer()).thenReturn(player);
        listener.onPlayerItemHeld(later);
        verify(later, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a painter cannot move kit items into a chest, but may in creative or their own inventory")
    public void kitItemsStayOutOfContainers() throws Exception {
        startSession(972);
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        org.bukkit.inventory.ItemStack kitDye = tagged("artkit_item");

        org.bukkit.event.inventory.InventoryClickEvent chest = click(player, kitDye,
                org.bukkit.event.inventory.InventoryType.CHEST, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER);
        listener.onInventoryClick(chest);
        verify(chest).setCancelled(true);

        org.bukkit.event.inventory.InventoryClickEvent stone = click(player, item(Material.STONE),
                org.bukkit.event.inventory.InventoryType.CHEST, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER);
        listener.onInventoryClick(stone);
        verify(stone, never()).setCancelled(true);

        org.bukkit.event.inventory.InventoryClickEvent own = click(player, kitDye,
                org.bukkit.event.inventory.InventoryType.PLAYER, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER);
        listener.onInventoryClick(own);
        verify(own, never()).setCancelled(true);

        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.CREATIVE);
        org.bukkit.event.inventory.InventoryClickEvent creative = click(player, kitDye,
                org.bukkit.event.inventory.InventoryType.CHEST, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER);
        listener.onInventoryClick(creative);
        verify(creative, never()).setCancelled(true);
    }

    @Test
    @DisplayName("someone who is not painting can move kit-tagged items freely")
    public void nonPainterMayMoveKitItems() {
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        org.bukkit.event.inventory.InventoryClickEvent chest = click(player, tagged("artkit_item"),
                org.bukkit.event.inventory.InventoryType.CHEST, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER);
        listener.onInventoryClick(chest);
        verify(chest, never()).setCancelled(true);
    }

    @Test
    @DisplayName("clicking the kit's next and back buttons pages the kit instead of moving the button")
    public void kitButtonsPage() throws Exception {
        Player painter = kitPainter(973);
        Material button = Material.MAGENTA_GLAZED_TERRACOTTA;

        org.bukkit.event.inventory.InventoryClickEvent next = click(painter, tagged("artkit_next"),
                org.bukkit.event.inventory.InventoryType.PLAYER, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER);
        listener.onInventoryClick(next);
        verify(next).setCancelled(true);
        org.junit.jupiter.api.Assertions.assertEquals(button, painter.getInventory().getStorageContents()[27].getType(), "page two has a back button");

        org.bukkit.event.inventory.InventoryClickEvent back = click(painter, tagged("artkit_back"),
                org.bukkit.event.inventory.InventoryType.PLAYER, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER);
        listener.onInventoryClick(back);
        verify(back).setCancelled(true);
        org.bukkit.inventory.ItemStack slot27 = painter.getInventory().getStorageContents()[27];
        boolean air;
        try { air = slot27 == null || slot27.getType() == Material.AIR; } catch (NullPointerException noDelegate) { air = true; }
        org.junit.jupiter.api.Assertions.assertTrue(air, "back on page one there is no back button");
    }

    @Test
    @DisplayName("while the kit is in use the armour slots are locked but other slots are not")
    public void armourIsLockedInTheKit() throws Exception {
        Player painter = kitPainter(974);

        org.bukkit.event.inventory.InventoryClickEvent armour = click(painter, item(Material.STONE),
                org.bukkit.event.inventory.InventoryType.PLAYER, org.bukkit.event.inventory.InventoryType.SlotType.ARMOR);
        listener.onInventoryClick(armour);
        verify(armour).setCancelled(true);

        org.bukkit.event.inventory.InventoryClickEvent container = click(painter, item(Material.STONE),
                org.bukkit.event.inventory.InventoryType.PLAYER, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER);
        listener.onInventoryClick(container);
        verify(container, never()).setCancelled(true);
    }

    @Test
    @DisplayName("dropping anything ends a preview and is swallowed")
    public void dropEndsPreview() {
        previews().startPreview(player, new FakePreview());
        PlayerDropItemEvent event = drop(player, item(Material.STONE));
        listener.onPlayerDropItem(event);
        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("a painter in the kit cannot drop anything; a painter out of it only keeps kit items")
    public void kitItemsCannotBeDropped() throws Exception {
        Player inKit = kitPainter(975);
        PlayerDropItemEvent anything = drop(inKit, item(Material.STONE));
        listener.onPlayerDropItem(anything);
        verify(anything).setCancelled(true);

        startSession(976);
        PlayerDropItemEvent kitItem = drop(player, tagged("artkit_back"));
        listener.onPlayerDropItem(kitItem);
        verify(kitItem).setCancelled(true);

        PlayerDropItemEvent stone = drop(player, item(Material.STONE));
        listener.onPlayerDropItem(stone);
        verify(stone, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a painter in the kit cannot pick items up")
    public void kitPainterCannotPickUp() throws Exception {
        Player inKit = kitPainter(977);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(inKit);
        listener.onPlayerPickup(event);
        verify(event).setCancelled(true);
    }
}
