package com.doubleshotdevs.brewloop;

/**
 * One line in the customer's cart: a menu item with its chosen options.
 */
public class CartItem {
    private final String itemId;
    private final String name;
    private final double price;
    private int quantity;

    /**
     * Creates a cart line.
     *
     * @param itemId   Firebase key of the menu item
     * @param name     display name
     * @param price    price of one unit including any options
     * @param quantity number of units
     */
    public CartItem(String itemId, String name, double price, int quantity) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /** @return price multiplied by quantity */
    public double getLineTotal() { return price * quantity; }
}