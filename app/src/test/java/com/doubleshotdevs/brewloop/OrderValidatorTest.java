package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests order line checks, menu checks, cent-based totals, and the order record that is
 * written.
 */
public class OrderValidatorTest {

    private static OrderItem line(String itemId, int quantity, double total) {
        return new OrderItem(null, itemId, "Small", "Whole", 0, quantity, total);
    }

    private static Map<String, Object> menuItem(double basePrice, Boolean available) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", "Latte");
        item.put("basePrice", basePrice);
        if (available != null) {
            item.put("isAvailable", available);
        }
        return item;
    }

    private static Map<String, Object> menu() {
        Map<String, Object> menu = new HashMap<>();
        menu.put("m001", menuItem(4.50, true));
        menu.put("m002", menuItem(5.00, false));
        // Firebase returns whole numbers as Long.
        menu.put("m003", menuItemLong());
        return menu;
    }

    private static Map<String, Object> menuItemLong() {
        Map<String, Object> item = new HashMap<>();
        item.put("basePrice", 3L);
        return item;
    }

    @Test
    public void emptyOrder_isRejected() {
        assertEquals(OrderValidator.Problem.EMPTY,
                OrderValidator.checkLines(Collections.emptyList()));
    }

    @Test
    public void quantityLimits() {
        assertEquals(OrderValidator.Problem.INVALID_QUANTITY,
                OrderValidator.checkLines(Collections.singletonList(line("m001", 0, 4.5))));
        assertEquals(OrderValidator.Problem.INVALID_QUANTITY, OrderValidator.checkLines(
                Collections.singletonList(line("m001", OrderValidator.MAX_QUANTITY_PER_LINE + 1, 4.5))));
        assertNull(OrderValidator.checkLines(Collections.singletonList(
                line("m001", OrderValidator.MAX_QUANTITY_PER_LINE, 90.0))));
    }

    @Test
    public void invalidItemsAndPrices() {
        assertEquals(OrderValidator.Problem.INVALID_ITEM,
                OrderValidator.checkLines(Collections.singletonList(line(null, 1, 4.5))));
        assertEquals(OrderValidator.Problem.INVALID_ITEM,
                OrderValidator.checkLines(Collections.singletonList(line("bad/id", 1, 4.5))));
        assertEquals(OrderValidator.Problem.INVALID_ITEM,
                OrderValidator.checkLines(Collections.singletonList(null)));
        assertEquals(OrderValidator.Problem.INVALID_PRICE,
                OrderValidator.checkLines(Collections.singletonList(line("m001", 1, 0))));
        assertEquals(OrderValidator.Problem.INVALID_PRICE,
                OrderValidator.checkLines(Collections.singletonList(line("m001", 1, Double.NaN))));
    }

    @Test
    public void tooManyLines_isRejected() {
        List<OrderItem> lines = new ArrayList<>();
        for (int i = 0; i <= OrderValidator.MAX_LINES; i++) {
            lines.add(line("m001", 1, 4.5));
        }
        assertEquals(OrderValidator.Problem.TOO_MANY_LINES, OrderValidator.checkLines(lines));
    }

    @Test
    public void menuChecks() {
        assertEquals(OrderValidator.Problem.ITEM_NOT_ON_MENU, OrderValidator.checkAgainstMenu(
                Collections.singletonList(line("m999", 1, 4.5)), menu()));
        assertEquals(OrderValidator.Problem.ITEM_NOT_ON_MENU, OrderValidator.checkAgainstMenu(
                Collections.singletonList(line("m001", 1, 4.5)), null));
        assertEquals(OrderValidator.Problem.ITEM_UNAVAILABLE, OrderValidator.checkAgainstMenu(
                Collections.singletonList(line("m002", 1, 5.0)), menu()));
        // Two lattes at $4.50 must cost at least $9.00.
        assertEquals(OrderValidator.Problem.PRICE_CHANGED, OrderValidator.checkAgainstMenu(
                Collections.singletonList(line("m001", 2, 8.99)), menu()));
        assertNull(OrderValidator.checkAgainstMenu(
                Arrays.asList(line("m001", 2, 9.00), line("m003", 1, 3.0)), menu()));
        // Customization adds to the base price, which is accepted.
        assertNull(OrderValidator.checkAgainstMenu(
                Collections.singletonList(line("m001", 1, 5.50)), menu()));
    }

    @Test
    public void totals_useWholeCents() {
        List<OrderItem> lines = Arrays.asList(line("m001", 1, 0.10), line("m001", 1, 0.20));
        assertEquals(30L, OrderValidator.totalCents(lines));
        assertEquals(0.30, OrderValidator.toDollars(OrderValidator.totalCents(lines)), 0.0);
        assertEquals(450L, OrderValidator.toCents(4.4999999));
    }

    @Test
    public void copyLines_setsIdsDefaultsAndLeavesCallerLinesUnchanged() {
        OrderItem original = new OrderItem(null, "m003", null, " ", -2, 1, 3.0);
        List<OrderItem> copies = OrderRepository.copyLines("ord1",
                Arrays.asList(line("m001", 1, 4.5), original));

        assertEquals("ord1_1", copies.get(0).orderItemID);
        assertEquals("ord1_2", copies.get(1).orderItemID);
        assertEquals("ord1", copies.get(1).orderID);
        assertEquals(OrderRepository.NOT_APPLICABLE, copies.get(1).size);
        assertEquals(OrderRepository.NOT_APPLICABLE, copies.get(1).milkType);
        assertEquals(0, copies.get(1).extraShots);
        assertNull(original.orderItemID);
        assertNull(original.orderID);
    }

    @Test
    public void orderUpdate_isOneAtomicWriteWithReceivedStatusAndNoPointsAwarded() {
        List<OrderItem> lines = OrderRepository.copyLines("ord1",
                Arrays.asList(line("m001", 2, 9.0), line("m003", 1, 3.0)));
        Map<String, Object> update =
                OrderRepository.buildOrderUpdate("ord1", "uid1", "08:15", lines);

        assertEquals(3, update.size());
        Order order = (Order) update.get("Orders/ord1");
        assertNotNull(order);
        assertEquals("ord1", order.orderID);
        assertEquals("uid1", order.userID);
        assertEquals(OrderRepository.STATUS_RECEIVED, order.status);
        assertEquals("08:15", order.pickupTime);
        assertEquals(12.0, order.totalPrice, 0.0);
        assertEquals(RewardsHelper.calculatePoints(12.0), order.pointsEarned);
        assertFalse(order.pointsAwarded);
        assertFalse(order.rewardApplied);
        assertTrue(update.containsKey("OrderItems/ord1_1"));
        assertTrue(update.containsKey("OrderItems/ord1_2"));
    }

    @Test
    public void sameOrderId_alwaysProducesTheSamePaths() {
        List<OrderItem> lines = Collections.singletonList(line("m001", 1, 4.5));
        Map<String, Object> first = OrderRepository.buildOrderUpdate("ord1", "uid1", "08:15",
                OrderRepository.copyLines("ord1", lines));
        Map<String, Object> retry = OrderRepository.buildOrderUpdate("ord1", "uid1", "08:15",
                OrderRepository.copyLines("ord1", lines));
        assertEquals(first.keySet(), retry.keySet());
    }

    @Test
    public void newCustomerRecord_isAlwaysCustomerWithZeroPoints() {
        Map<String, Object> record =
                UserRepository.newCustomerRecord("uid1", "Sam Rivera", "sam@example.com");
        assertEquals("customer", record.get(User.FIELD_ROLE));
        assertEquals(0, record.get(User.FIELD_POINTS_BALANCE));
        assertEquals("uid1", record.get(User.FIELD_USER_ID));
        assertEquals(5, record.size());
        // The record passes the same validation used when profiles are loaded.
        assertTrue(UserProfileParser.parse("uid1", record).isSuccess());
    }
}
