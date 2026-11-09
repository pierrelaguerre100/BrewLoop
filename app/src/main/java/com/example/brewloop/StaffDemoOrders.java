package com.example.brewloop;

import com.example.brewloop.Order;
import com.example.brewloop.OrderItem;
import com.example.brewloop.OrderStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local test orders from the sample data in our revised Phase 2.
 * This is Week 1 UI demo. Nothing here writes to Firebase.
 */
public final class StaffDemoOrders {
    private StaffDemoOrders() { }

    /** Returns three sample orders, with active orders shown first. */
    public static List<Order> createOrders() {
        return new ArrayList<>(Arrays.asList(
                new Order("ord102", "u001", OrderStatus.RECEIVED, "09:20", 3.00, false, 3, false),
                new Order("ord101", "u001", OrderStatus.PREPARING, "09:05", 5.00, false, 5, false),
                new Order("ord100", "u001", OrderStatus.COMPLETED, "08:15", 7.50, false, 7, true)
        ));
    }

    /** Returns the sample OrderItems grouped by their parent order ID. */
    public static Map<String, List<OrderItem>> createItemsByOrder() {
        Map<String, List<OrderItem>> result = new HashMap<>();
        result.put("ord100", Arrays.asList(
                new OrderItem("oir001", "ord100", "m001", "Small", "Whole", 0, 1, 4.50),
                new OrderItem("oir002", "ord100", "m003", "n/a", "n/a", 0, 1, 3.00)
        ));
        result.put("ord101", Arrays.asList(
                new OrderItem("oir003", "ord101", "m002", "Small", "Whole", 0, 1, 5.00)
        ));
        result.put("ord102", Arrays.asList(
                new OrderItem("oir004", "ord102", "m003", "n/a", "n/a", 0, 1, 3.00)
        ));
        return result;
    }

    /** Resolves a sample menu ID to a readable name for the demo screen. */
    public static String menuName(String itemID) {
        if ("m001".equals(itemID)) return "Latte";
        if ("m002".equals(itemID)) return "Cold Brew";
        if ("m003".equals(itemID)) return "Blueberry Muffin";
        return itemID == null ? "Unknown item" : itemID;
    }
}
