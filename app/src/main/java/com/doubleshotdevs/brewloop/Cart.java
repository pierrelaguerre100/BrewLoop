package com.doubleshotdevs.brewloop;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the items the customer has chosen, shared by the menu and checkout screens.
 */
public class Cart {
    private static final Cart INSTANCE = new Cart();
    private final List<CartItem> items = new ArrayList<>();

    private Cart() { }

    /** @return the one shared cart */
    public static Cart getInstance() { return INSTANCE; }

    /** Adds a menu item, or increases its quantity if it is already in the cart. */
    public void add(String itemId, String name, double price) {
        for (CartItem item : items) {
            if (item.getItemId().equals(itemId) && item.getPrice() == price) {
                item.setQuantity(item.getQuantity() + 1);
                return;
            }
        }
        items.add(new CartItem(itemId, name, price, 1));
    }

    /** @return the cart lines */
    public List<CartItem> getItems() { return items; }

    /** @return the total of all lines before any reward */
    public double getTotal() {
        double total = 0;
        for (CartItem item : items) {
            total += item.getLineTotal();
        }
        return total;
    }

    /** Empties the cart after an order is placed. */
    public void clear() { items.clear(); }
        /**
         * Removes the line at the given position.
         *
         * @param position index of the line to remove
         */
        public void remove(int position) {
            items.remove(position);
    }
}
