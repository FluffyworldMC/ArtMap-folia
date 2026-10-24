package me.Fupery.ArtMap.Menu.Button;

import java.util.function.BiConsumer;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** A button whose click behaviour comes from the menu configuration. */
public class ActionButton extends Button {
    private final BiConsumer<Player, ClickType> action;

    public ActionButton(ItemStack item, BiConsumer<Player, ClickType> action) {
        super(item);
        setAmount(item.getAmount());
        this.action = action;
    }

    @Override
    public void onClick(Player player, ClickType clickType) {
        if (action != null) action.accept(player, clickType);
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
