package me.getsouf.bookhunt.manager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Снимок инвентаря игрока (содержимое + броня + вторая рука).
 */
public class InventorySnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final int heldSlot;

    public InventorySnapshot(Player player) {
        PlayerInventory inv = player.getInventory();
        this.contents = cloneArray(inv.getContents());
        this.armor = cloneArray(inv.getArmorContents());
        this.offhand = cloneItem(inv.getItemInOffHand());
        this.heldSlot = inv.getHeldItemSlot();
    }

    public void restore(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setContents(cloneArray(contents));
        inv.setArmorContents(cloneArray(armor));
        inv.setItemInOffHand(cloneItem(offhand));
        inv.setHeldItemSlot(heldSlot);
        player.updateInventory();
    }

    private static ItemStack[] cloneArray(ItemStack[] src) {
        if (src == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            copy[i] = cloneItem(src[i]);
        }
        return copy;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}