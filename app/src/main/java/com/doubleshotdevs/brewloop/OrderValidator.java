package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Checks order lines before an order is written, and computes totals without
 * floating-point drift.
 *
 * <p>Prices are stored as {@code double} dollar amounts, as in the team's data contract.
 * To avoid rounding errors such as 0.1 + 0.2 = 0.30000000000000004, every amount is
 * converted to whole cents once, all arithmetic is done in cents, and the result is
 * converted back to dollars only when it is stored. This class has no Android dependencies,
 * so it is covered by local unit tests.</p>
 */
public final class OrderValidator {

    /** Largest quantity accepted for one order line. */
    public static final int MAX_QUANTITY_PER_LINE = 20;

    /** Largest number of lines accepted in one order. */
    public static final int MAX_LINES = 50;

    /** Menu item field holding the price before customization. */
    static final String MENU_FIELD_BASE_PRICE = "basePrice";

    /** Menu item field that is {@code false} when the item is sold out. */
    static final String MENU_FIELD_AVAILABLE = "isAvailable";

    /** A reason an order cannot be submitted. */
    public enum Problem {
        /** The order has no lines. */
        EMPTY,
        /** The order has more than {@link #MAX_LINES} lines. */
        TOO_MANY_LINES,
        /** A line has no usable menu item ID. */
        INVALID_ITEM,
        /** A line's quantity is below 1 or above {@link #MAX_QUANTITY_PER_LINE}. */
        INVALID_QUANTITY,
        /** A line's total is missing, negative, zero, or not a valid amount. */
        INVALID_PRICE,
        /** A line refers to an item that is no longer on the menu. */
        ITEM_NOT_ON_MENU,
        /** A line refers to an item that is sold out. */
        ITEM_UNAVAILABLE,
        /**
         * A line's total is lower than the item's current base price times its quantity,
         * usually because the price went up after the item was added.
         */
        PRICE_CHANGED
    }

    private OrderValidator() {
        // Static helpers only.
    }

    /**
     * Converts a dollar amount to whole cents, rounding to the nearest cent.
     *
     * @param dollars the amount in dollars
     * @return the amount in cents
     */
    public static long toCents(double dollars) {
        return Math.round(dollars * 100.0);
    }

    /**
     * Converts whole cents to a dollar amount for storage.
     *
     * @param cents the amount in cents
     * @return the amount in dollars
     */
    public static double toDollars(long cents) {
        return cents / 100.0;
    }

    /**
     * Checks the shape of the order lines without contacting the database.
     *
     * @param lines the order lines
     * @return the first problem found, or {@code null} if every line is acceptable
     */
    @Nullable
    public static Problem checkLines(@NonNull List<OrderItem> lines) {
        if (lines.isEmpty()) {
            return Problem.EMPTY;
        }
        if (lines.size() > MAX_LINES) {
            return Problem.TOO_MANY_LINES;
        }
        for (OrderItem line : lines) {
            if (line == null || !UserProfileParser.isValidUid(line.itemID)) {
                return Problem.INVALID_ITEM;
            }
            if (line.quantity < 1 || line.quantity > MAX_QUANTITY_PER_LINE) {
                return Problem.INVALID_QUANTITY;
            }
            if (Double.isNaN(line.itemTotal) || Double.isInfinite(line.itemTotal)
                    || toCents(line.itemTotal) <= 0) {
                return Problem.INVALID_PRICE;
            }
        }
        return null;
    }

    /**
     * Checks the order lines against the current menu.
     *
     * @param lines     order lines that already passed {@link #checkLines}
     * @param menuItems the raw value of the {@code MenuItems} node, keyed by item ID, as
     *                  returned by {@code DataSnapshot.getValue()}; may be {@code null}
     * @return the first problem found, or {@code null} if every item is available and no
     *         line costs less than the item's current base price
     */
    @Nullable
    public static Problem checkAgainstMenu(@NonNull List<OrderItem> lines,
                                           @Nullable Object menuItems) {
        Map<?, ?> menu = menuItems instanceof Map ? (Map<?, ?>) menuItems : null;
        for (OrderItem line : lines) {
            Object raw = menu == null ? null : menu.get(line.itemID);
            if (!(raw instanceof Map)) {
                return Problem.ITEM_NOT_ON_MENU;
            }
            Map<?, ?> item = (Map<?, ?>) raw;
            // Items without the flag are available, matching MenuProduct's default.
            if (Boolean.FALSE.equals(item.get(MENU_FIELD_AVAILABLE))) {
                return Problem.ITEM_UNAVAILABLE;
            }
            Object basePrice = item.get(MENU_FIELD_BASE_PRICE);
            if (!(basePrice instanceof Number)) {
                return Problem.ITEM_NOT_ON_MENU;
            }
            long minimumCents = toCents(((Number) basePrice).doubleValue()) * line.quantity;
            // Customization can only add to the base price, so a lower line total is stale.
            if (toCents(line.itemTotal) < minimumCents) {
                return Problem.PRICE_CHANGED;
            }
        }
        return null;
    }

    /**
     * Adds up the line totals.
     *
     * @param lines the order lines
     * @return the order total in cents
     */
    public static long totalCents(@NonNull List<OrderItem> lines) {
        long total = 0;
        for (OrderItem line : lines) {
            total += toCents(line.itemTotal);
        }
        return total;
    }
}
