package com.doubleshotdevs.brewloop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link Cart} and {@link CartItem}.
 */
public class CartTest {

    private static final double DELTA = 0.001;

    private Cart cart;

    /** Starts every test with an empty cart, since the cart is shared. */
    @Before
    public void setUp() {
        cart = Cart.getInstance();
        cart.clear();
    }

    @Test
    public void emptyCart_hasNoItemsAndZeroTotal() {
        assertTrue(cart.getItems().isEmpty());
        assertEquals(0.0, cart.getTotal(), DELTA);
    }

    @Test
    public void addItem_usesNotApplicableOptions() {
        cart.add("m003", "Blueberry Muffin", 3.00);

        CartItem item = cart.getItems().get(0);
        assertEquals(Cart.NOT_APPLICABLE, item.getSize());
        assertEquals(Cart.NOT_APPLICABLE, item.getMilkType());
        assertEquals(0, item.getExtraShots());
        assertEquals(1, item.getQuantity());
    }

    @Test
    public void addSameItemTwice_increasesQuantity() {
        cart.add("m003", "Blueberry Muffin", 3.00);
        cart.add("m003", "Blueberry Muffin", 3.00);

        assertEquals(1, cart.getItems().size());
        assertEquals(2, cart.getItems().get(0).getQuantity());
    }

    @Test
    public void addDifferentItems_createsSeparateLines() {
        cart.add("m001", "Latte", 4.50);
        cart.add("m003", "Blueberry Muffin", 3.00);

        assertEquals(2, cart.getItems().size());
    }

    @Test
    public void sameItemWithSameOptions_mergesIntoOneLine() {
        cart.add("m001", "Latte", 5.00, "Medium", "Oat", 1);
        cart.add("m001", "Latte", 5.00, "Medium", "Oat", 1);

        assertEquals(1, cart.getItems().size());
        assertEquals(2, cart.getItems().get(0).getQuantity());
    }

    @Test
    public void sameItemWithDifferentSize_createsSeparateLines() {
        cart.add("m001", "Latte", 4.50, "Small", "Whole", 0);
        cart.add("m001", "Latte", 5.50, "Large", "Whole", 0);

        assertEquals(2, cart.getItems().size());
    }

    @Test
    public void sameItemWithDifferentShots_createsSeparateLines() {
        cart.add("m001", "Latte", 4.50, "Small", "Whole", 0);
        cart.add("m001", "Latte", 5.00, "Small", "Whole", 1);

        assertEquals(2, cart.getItems().size());
    }

    @Test
    public void getTotal_addsAllLines() {
        cart.add("m001", "Latte", 4.50);
        cart.add("m001", "Latte", 4.50);
        cart.add("m003", "Blueberry Muffin", 3.00);

        assertEquals(12.00, cart.getTotal(), DELTA);
    }

    @Test
    public void lineTotal_isPriceTimesQuantity() {
        cart.add("m002", "Cold Brew", 5.00);
        cart.add("m002", "Cold Brew", 5.00);
        cart.add("m002", "Cold Brew", 5.00);

        assertEquals(15.00, cart.getItems().get(0).getLineTotal(), DELTA);
    }

    @Test
    public void remove_deletesTheLineAtThatPosition() {
        cart.add("m001", "Latte", 4.50);
        cart.add("m003", "Blueberry Muffin", 3.00);

        cart.remove(0);

        assertEquals(1, cart.getItems().size());
        assertEquals("m003", cart.getItems().get(0).getItemId());
        assertEquals(3.00, cart.getTotal(), DELTA);
    }

    @Test
    public void remove_ignoresAnInvalidPosition() {
        cart.add("m001", "Latte", 4.50);

        cart.remove(5);
        cart.remove(-1);

        assertEquals(1, cart.getItems().size());
    }

    @Test
    public void clear_emptiesTheCart() {
        cart.add("m001", "Latte", 4.50);

        cart.clear();

        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    public void matches_comparesItemAndAllOptions() {
        CartItem item = new CartItem("m001", "Latte", 5.00,"Medium", "Oat", 1, 1);

        assertTrue(item.matches("m001", "Medium", "Oat", 1));
        assertFalse(item.matches("m001", "Large", "Oat", 1));
        assertFalse(item.matches("m001", "Medium", "Whole", 1));
        assertFalse(item.matches("m001", "Medium", "Oat", 2));
        assertFalse(item.matches("m002", "Medium", "Oat", 1));
    }
}
