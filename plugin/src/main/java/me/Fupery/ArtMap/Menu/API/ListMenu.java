package me.Fupery.ArtMap.Menu.API;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;

import me.Fupery.ArtMap.ArtMap;
import me.Fupery.ArtMap.Menu.Button.Button;
import me.Fupery.ArtMap.Menu.Button.CloseButton;
import me.Fupery.ArtMap.Menu.Button.LinkedButton;
import me.Fupery.ArtMap.Menu.Event.MenuCloseReason;
import me.Fupery.ArtMap.Menu.Event.MenuFactory;
import me.Fupery.ArtMap.Menu.Handler.CacheableMenu;
import me.Fupery.ArtMap.api.Config.Lang;

public abstract class ListMenu extends CacheableMenu {

    private static final char LEFT_ARROW = '\u2B05';
    private static final char RIGHT_ARROW = '\u27A1';
    private static final int PAGE_SIZE = 25;
    // How far a shift-click on a page arrow moves.
    private static final int PAGE_JUMP = 10;

    protected int page;
    protected List<Button> cachedButtons;
    protected Optional<MenuFactory> parent = Optional.empty();

    protected ListMenu(String heading, int page) {
        super(heading, InventoryType.CHEST);
        this.page = page;
        this.cachedButtons = null;
    }

    protected ListMenu(String heading, MenuFactory parent, int page) {
        super(heading, InventoryType.CHEST);
        this.page = page;
        this.cachedButtons = null;
        this.parent = Optional.of(parent);
    }

    public String getHeading() {
        return this.heading;
    }

    @Override
    public void onMenuOpenEvent(Player viewer) {
    }

    @Override
    public void onMenuRefreshEvent(Player viewer) {
    }

    @Override
    public void onMenuClickEvent(Player viewer, int slot, ClickType click) {
    }

    @Override
    public void onMenuCloseEvent(Player viewer, MenuCloseReason reason) {
    }

    @Override
    public Future<Button[]> getButtons() {
        Button[] buttons = new Button[PAGE_SIZE + 2];

        if(cachedButtons == null) {
            try {
                cachedButtons = getListItems().get();
            } catch (InterruptedException | ExecutionException e) {
                ArtMap.instance().getLogger().log(Level.SEVERE,"Interrupted creating menu buttons!",e);
            }
        }

        if (page < 1) {
            if (this.parent.isPresent()) {
                String[] back = { ChatColor.RED.toString() + ChatColor.BOLD + LEFT_ARROW };
                buttons[0] = new LinkedButton(this.parent.get(), Material.MAGENTA_GLAZED_TERRACOTTA, back);
            } else {
                buttons[0] = new CloseButton();
            }
        } else {
            buttons[0] = new PageButton(false, page - 1);
        }

        List<Button> listItems = cachedButtons;
        
        int start = page * PAGE_SIZE;
        int pageLength = listItems.size() - start;

        if (pageLength > 0) {
            int end = (pageLength >= PAGE_SIZE) ? PAGE_SIZE : pageLength;

            System.arraycopy(listItems.toArray(), start, buttons, 1, end);

            if (listItems.size()> (PAGE_SIZE + start)) {
                buttons[PAGE_SIZE + 1] = new PageButton(true, page + 1);
            } else {
                buttons[PAGE_SIZE + 1] = null;
            }
        }
        return CompletableFuture.completedFuture(buttons);
    }

    // Zero-based index of the last page.
    private int lastPage() {
        return (cachedButtons == null || cachedButtons.isEmpty()) ? 0 : (cachedButtons.size() - 1) / PAGE_SIZE;
    }

    protected void changePage(Player player, boolean forward) {
        changePage(player, forward, 1);
    }

    protected void changePage(Player player, boolean forward, int pages) {
        int target = forward ? page + pages : page - pages;
        page = Math.max(0, Math.min(target, lastPage()));
        refresh(player);
    }

    protected abstract Future<List<Button>> getListItems();

    private class PageButton extends Button {

        boolean forward;

        // Named after the page it leads to ("Page 66 of 78"). The stack size shows that page number too, capped at
        // the max stack size: past it the stack is invalid (the back arrow reached 65 on page 66, where players
        // reported paging stopping).
        private PageButton(boolean forward, int targetPage) {
			super(forward ? Material.MAGENTA_GLAZED_TERRACOTTA : Material.BARRIER, new String[] {
					ChatColor.GREEN.toString() + ChatColor.BOLD + (forward ? RIGHT_ARROW : LEFT_ARROW) + " "
							+ Lang.SEARCH_PAGE.get() + " " + (targetPage + 1) + Lang.SEARCH_PAGE_SEPERATOR.get() + (lastPage() + 1),
					ChatColor.GRAY + String.format(Lang.MENU_PAGE_JUMP.get(), PAGE_JUMP) });
            this.forward = forward;
            setAmount(Math.max(1, Math.min(targetPage + 1, getMaxStackSize())));
        }

        @Override
        public void onClick(Player player, ClickType clickType) {
            SoundCompat.UI_BUTTON_CLICK.play(player);
            changePage(player, forward, clickType.isShiftClick() ? PAGE_JUMP : 1);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + getEnclosingInstance().hashCode();
            result = prime * result + (forward ? 1231 : 1237);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj))
                return false;
            if (getClass() != obj.getClass())
                return false;
            PageButton other = (PageButton) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
                return false;
            return forward == other.forward;
        }

        private ListMenu getEnclosingInstance() {
            return ListMenu.this;
        }

        
    }
}
