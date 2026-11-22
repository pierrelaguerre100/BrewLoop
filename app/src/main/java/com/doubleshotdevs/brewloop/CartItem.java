package com.doubleshotdevs.brewloop;

/**
 * One line in the customer's cart: a menu item with its chosen options.
 */
public class CartItem {
    private final String itemId;
    private final String name;
    private final double price;
    private final String size;
    private final String milkType;
    private final int extraShots;
    private int quantity;

    /**
     * Creates a cart line.
     *
     * @param itemId     Firebase key of the menu item
     * @param name       display name
     * @param price      price of one unit including the chosen options
     * @param size       chosen size, or "n/a"
     * @param milkType   chosen milk, or "n/a"
     * @param extraShots number of extra espresso shots
     * @param quantity   number of units
     */
    public CartItem(String itemId, String name, double price, String size,
                    String milkType, int extraShots, int quantity) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.size = size;
        this.milkType = milkType;
        this.extraShots = extraShots;
        this.quantity = quantity;
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getSize() { return size; }
    public String getMilkType() { return milkType; }
    public int getExtraShots() { return extraShots; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /** @return price multiplied by quantity */
    public double getLineTotal() { return price * quantity; }

    /**
     * Checks whether this line is the same item with the same options.
     *
     * @param otherItemId     the item key
     * @param otherSize       the size
     * @param otherMilkType   the milk
     * @param otherExtraShots the number of extra shots
     * @return true if all four match
     */
    public boolean matches(String otherItemId, String otherSize,
                           String otherMilkType, int otherExtraShots) {
        return itemId.equals(otherItemId)
                && size.equals(otherSize)
                && milkType.equals(otherMilkType)
                && extraShots == otherExtraShots;
    }
}
