package com.doubleshotdevs.brewloop;

public class Order {
    public String orderID;
    public String userID, status, pickupTime;
    public double totalPrice;
    public int pointsEarned;
    public boolean rewardApplied, pointsAwarded;
    public Order() {
    }

    public Order(String userID, String status, String pickupTime, double totalPrice, int pointsEarned, boolean rewardApplied, boolean pointsAwarded ) {
        this.userID = userID;
        this.status = status;
        this.pickupTime = pickupTime;
        this.totalPrice = totalPrice;
        this.pointsEarned = pointsEarned;
        this.rewardApplied = rewardApplied;
        this.pointsAwarded = pointsAwarded;
    }
}

