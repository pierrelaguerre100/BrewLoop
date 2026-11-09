package com.doubleshotdevs.brewloop;

/**
 * Names of the top-level Firebase Realtime Database nodes shared by every BrewLoop feature.
 *
 * <p>All database access should reference these constants instead of repeating string
 * literals, so a node name is defined in exactly one place. The names match the revised
 * team data contract and must not be renamed, because existing data lives under them.</p>
 */
public final class FirebaseNodes {

    /** Customer, staff, and manager profiles, keyed by Firebase Authentication UID. */
    public static final String USERS = "Users";

    /** Menu items in the revised data contract. */
    public static final String MENU_ITEMS = "MenuItems";

    /** Customization options that can be applied to customizable menu items. */
    public static final String CUSTOMIZATION_OPTION = "CustomizationOption";

    /** Customer orders. */
    public static final String ORDERS = "Orders";

    /** Line items belonging to orders. */
    public static final String ORDER_ITEMS = "OrderItems";

    /** History of loyalty points earned and redeemed. */
    public static final String POINTS_TRANSACTION = "PointsTransaction";

    /**
     * Starter project's placeholder menu node, which holds plain text values such as
     * {@code "Latte, Hot Coffee, $4.50"}.
     *
     * <p>This is not part of the revised contract; new menu features use
     * {@link #MENU_ITEMS}. It is kept only so the existing starter screen can still read
     * the data already stored there.</p>
     */
    public static final String LEGACY_MENU = "Menu";

    private FirebaseNodes() {
        // Constants only.
    }
}
