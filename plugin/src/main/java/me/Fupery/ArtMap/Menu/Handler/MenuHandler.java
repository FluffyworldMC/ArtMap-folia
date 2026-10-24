package me.Fupery.ArtMap.Menu.Handler;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.API.ChildMenu;
import me.Fupery.ArtMap.Menu.Config.ConfiguredMenu;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Menu.Event.MenuFactory;
import me.Fupery.ArtMap.Menu.Event.MenuListener;
import me.Fupery.ArtMap.Menu.HelpMenu.*;
import me.Fupery.ArtMap.Painting.Artkit.ArtKitMenu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuHandler {
    public final MenuList MENU = new MenuList();
    private final ConcurrentHashMap<UUID, CacheableMenu> openMenus = new ConcurrentHashMap<>();

    public MenuHandler(JavaPlugin plugin) {
        new MenuListener(this, plugin);
    }

    public void reload() {
        MENU.reset();
    }

    public void openMenuById(Player viewer, String id) {
        CacheableMenu menu = createMenu(id, viewer);
        if (menu != null) {
            openMenu(viewer, menu);
        }
    }

    public CacheableMenu createMenu(String id, Player viewer) {
        if (id == null) return null;
        switch (id.toLowerCase()) {
            case "help":
            case "tools":
                return new ConfiguredMenu(id.toLowerCase());
            case "recipes":
            case "recipe":
                return new RecipeMenu(viewer.hasPermission("artmap.admin"));
            case "artists":
                return new ArtistMenu(viewer);
            case "artkit":
                return new ArtKitMenu(viewer, 0);
            default:
                return null;
        }
    }

    public boolean isTrackingPlayer(Player player) {
        return player != null && openMenus.containsKey(player.getUniqueId());
    }

    public void openMenu(Player viewer, CacheableMenu menu) {
        if (isTrackingPlayer(viewer)) {
            closeMenu(viewer, MenuCloseReason.SWITCH);
        } else {
            viewer.closeInventory();//todo check if this works
        }
        openMenus.put(viewer.getUniqueId(),menu);
        menu.open(viewer);
    }

    public void fireClickEvent(Player viewer, int slot, ClickType clickType) {
        if (!isTrackingPlayer(viewer)) return;
        this.openMenus.get(viewer.getUniqueId()).click(viewer, slot, clickType);
    }

    public void refreshMenu(Player viewer) {
        if (!isTrackingPlayer(viewer)) return;
        this.openMenus.get(viewer.getUniqueId()).refresh(viewer);
    }

    public void closeMenu(Player viewer, MenuCloseReason reason) {
        if (!isTrackingPlayer(viewer)) return;
        CacheableMenu menu = openMenus.get(viewer.getUniqueId());
        if(menu != null) {
            menu.close(viewer, reason);
        }
        if (menu instanceof ChildMenu && reason == MenuCloseReason.BACK) {
            CacheableMenu parent = ((ChildMenu) menu).getParent(viewer);
            if (parent != null) {
                openMenu(viewer, parent);
                return;
            }
        }
        openMenus.remove(viewer.getUniqueId());
    }

    public void closeAll() {
        for (UUID uuid : openMenus.keySet()) closeMenu(Bukkit.getPlayer(uuid), MenuCloseReason.SYSTEM);
        openMenus.clear();
    }

    public static class MenuList {
        public MenuFactory HELP = viewer -> new ConfiguredMenu("help");
        public MenuFactory TOOLS = viewer -> new ConfiguredMenu("tools");
        public MenuFactory ARTIST = new DynamicMenuFactory(ArtistMenu::new);
        public MenuFactory RECIPE = new ConditionalMenuFactory(new ConditionalMenuFactory.ConditionalGenerator() {
            @Override
            public CacheableMenu getConditionTrue() {
                return new RecipeMenu(true);
            }

            @Override
            public CacheableMenu getConditionFalse() {
                return new RecipeMenu(false);
            }

            @Override
            public boolean evaluateCondition(Player viewer) {
                return viewer.hasPermission("artmap.admin");
            }
        });

        /** Re-creates the factory fields after a configuration reload. */
        public void reset() {
            HELP = viewer -> new ConfiguredMenu("help");
            TOOLS = viewer -> new ConfiguredMenu("tools");
            ARTIST = new DynamicMenuFactory(ArtistMenu::new);
            RECIPE = new ConditionalMenuFactory(new ConditionalMenuFactory.ConditionalGenerator() {
                @Override
                public CacheableMenu getConditionTrue() {
                    return new RecipeMenu(true);
                }

                @Override
                public CacheableMenu getConditionFalse() {
                    return new RecipeMenu(false);
                }

                @Override
                public boolean evaluateCondition(Player viewer) {
                    return viewer.hasPermission("artmap.admin");
                }
            });
        }
    }
}
