package com.doubleshotdevs.brewloop;

public class OrderItem {
    public String orderItemID;
    public String orderID, itemID, size, milkType;
    public int extraShots, quantity;
    public double itemTotal;
    public OrderItem() {
    }

    public OrderItem(String orderID, String itemID, String size, String milkType, int extraShots, int quantity, double itemTotal) {
        this.orderID = orderID;
        this.itemID = itemID;
        this.size = size;
        this.milkType = milkType;
        this.extraShots = extraShots;
        this.quantity = quantity;
        this.itemTotal = itemTotal;
    }
}

