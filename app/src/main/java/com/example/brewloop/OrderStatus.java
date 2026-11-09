package com.example.brewloop;

/**
 * Shared order status values used by the staff and customer screens.
 */
public final class OrderStatus {
    public static final String RECEIVED = "Received";
    public static final String PREPARING = "Preparing";
    public static final String READY = "Ready";
    public static final String COMPLETED = "Completed";

    private OrderStatus() { }

    /**
     * Finds the next allowed step in the order workflow.
     * @param current the current order status
     * @return the next status, or null when the order is finished or invalid
     */
    public static String next(String current) {
        if (RECEIVED.equals(current)) return PREPARING;
        if (PREPARING.equals(current)) return READY;
        if (READY.equals(current)) return COMPLETED;
        return null;
    }

    /** Returns true when an order is not yet Completed. */
    public static boolean isActive(String status) {
        return RECEIVED.equals(status) || PREPARING.equals(status) || READY.equals(status);
    }

    /** Returns the short text for the next staff action. */
    public static String nextAction(String status) {
        String next = next(status);
        return next == null ? "Order Completed" : "Mark " + next;
    }
}
