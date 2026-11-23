package com.doubleshotdevs.brewloop;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the items the customer has chosen, shared by the menu and checkout screens.
 */
public class Cart {

    /** Value stored when an item has no size or milk choice. */
    public static final String NOT_APPLICABLE = "n/a";

    private static final Cart INSTANCE = new Cart();
    private final List<CartItem> items = new ArrayList<>();

    private Cart() { }

    /** @return the one shared cart */
    public static Cart getInstance() {
        return INSTANCE;
    }

    /**
     * Adds an item with no options, such as a pastry.
     *
     * @param itemId Firebase key of the menu item
     * @param name   display name
     * @param price  price of one unit
     */
    public void add(String itemId, String name, double price) {
        add(itemId, name, price, NOT_APPLICABLE, NOT_APPLICABLE, 0);
    }

    /**
     * Adds a customized item, or increases the quantity if the same item with
     * the same options is already in the cart.
     *
     * @param itemId     Firebase key of the menu item
     * @param name       display name
     * @param unitPrice  price of one unit including the options
     * @param size       chosen size
     * @param milkType   chosen milk
     * @param extraShots number of extra shots
     */
    public void add(String itemId, String name, double unitPrice, String size,
                    String milkType, int extraShots) {
        for (CartItem item : items) {
            if (item.matches(itemId, size, milkType, extraShots)) {
                item.setQuantity(item.getQuantity() + 1);
                return;
            }
        }
        items.add(new CartItem(itemId, name, unitPrice, size, milkType, extraShots, 1));

    }

    /** @return the cart lines */
    public List<CartItem> getItems() {
        return items;
    }

    /** @return the total of all lines before any reward */
    public double getTotal() {
        double total = 0;

        for (CartItem item : items) {
            total += item.getLineTotal();
        }

        return total;
    }

    /** Empties the cart after an order is placed. */
    public void clear() {
        items.clear();
    }

    /**
     * Removes the line at the given position, if it exists.
     *
     * @param position index of the line to remove
     */
    public void remove(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
        }
    }
}
