package com.example.brewloop;

/**
 * One line item in an order.
 * Menu item names are resolved from MenuItems.
 */
public class OrderItem {
    private String orderItemID;
    private String orderID;
    private String itemID;
    private String size;
    private String milkType;
    private int extraShots;
    private int quantity;
    private double itemTotal;

    /** Required empty constructor for Firebase object mapping. */
    public OrderItem() { }

    /** Creates an order item with its customization and total price. */
    public OrderItem(String orderItemID, String orderID, String itemID, String size,
                     String milkType, int extraShots, int quantity, double itemTotal) {
        this.orderItemID = orderItemID;
        this.orderID = orderID;
        this.itemID = itemID;
        this.size = size;
        this.milkType = milkType;
        this.extraShots = extraShots;
        this.quantity = quantity;
        this.itemTotal = itemTotal;
    }

    public String getOrderItemID() { return orderItemID; }
    public void setOrderItemID(String orderItemID) { this.orderItemID = orderItemID; }
    public String getOrderID() { return orderID; }
    public void setOrderID(String orderID) { this.orderID = orderID; }
    public String getItemID() { return itemID; }
    public void setItemID(String itemID) { this.itemID = itemID; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getMilkType() { return milkType; }
    public void setMilkType(String milkType) { this.milkType = milkType; }
    public int getExtraShots() { return extraShots; }
    public void setExtraShots(int extraShots) { this.extraShots = extraShots; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getItemTotal() { return itemTotal; }
    public void setItemTotal(double itemTotal) { this.itemTotal = itemTotal; }
}
