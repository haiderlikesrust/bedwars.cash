package cash.bedwars.game.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks plugin shop GUIs so click/drag handlers can lock them without relying on titles. */
public final class ShopInventoryHolder implements InventoryHolder {
    public enum Kind { ITEM, UPGRADE }

    private Inventory inventory;
    private final Kind kind;

    private ShopInventoryHolder(Kind kind) {
        this.kind = kind;
    }

    public static ShopInventoryHolder item() {
        return new ShopInventoryHolder(Kind.ITEM);
    }

    public static ShopInventoryHolder upgrade() {
        return new ShopInventoryHolder(Kind.UPGRADE);
    }

    public Kind kind() {
        return kind;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
