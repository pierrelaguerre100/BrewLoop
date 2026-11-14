package com.doubleshotdevs.brewloop;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.PropertyName;

/**
 * One item on the shop menu, as stored under the "MenuItems" node in Firebase.
 */
public class MenuProduct {
    private String itemId;
    private String name;
    private String category;
    private String description;
    private double basePrice;
    private boolean available = true;
    private boolean customizable;

    /** Required by Firebase for reading data. */
    public MenuProduct() { }

    /**
     * Creates a menu item.
     *
     * @param name         display name
     * @param category     category such as "Hot Coffee"
     * @param description short description
     * @param basePrice    price before customization
     * @param available    false if the item is sold out
     * @param customizable true if size, milk, and shots can be chosen
     */
    public MenuProduct(String name, String category, String description,
                       double basePrice, boolean available, boolean customizable) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.basePrice = basePrice;
        this.available = available;
        this.customizable = customizable;
    }

    /** @return the Firebase key of this item, such as "m001" */
    @Exclude
    public String getItemId() { return itemId; }

    /** @param itemId the Firebase key of this item */
    @Exclude
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }

    /** @return false if the item is sold out (stored as "isAvailable") */
    @PropertyName("isAvailable")
    public boolean isAvailable() { return available; }

    @PropertyName("isAvailable")
    public void setAvailable(boolean available) { this.available = available; }

    /** @return true if the customer can choose size, milk, and shots */
    @PropertyName("isCustomizable")
    public boolean isCustomizable() { return customizable; }

    @PropertyName("isCustomizable")
    public void setCustomizable(boolean customizable) { this.customizable = customizable; }
}
