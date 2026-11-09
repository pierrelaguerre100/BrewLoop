package com.example.brewloop;

/**
 * Stores one coffee order.
 * The field names follow the Orders table in our revised Phase 2 database design.
 * The empty constructor and getters/setters also support future Firebase reads.
 */
public class Order {
    private String orderID;
    private String userID;
    private String status;
    private String pickupTime;
    private double totalPrice;
    private boolean rewardApplied;
    private int pointsEarned;
    private boolean pointsAwarded;

    /** Required empty constructor for Firebase object mapping. */
    public Order() { }

    /** Creates a complete order for local sample data or future order submission. */
    public Order(String orderID, String userID, String status, String pickupTime,
                 double totalPrice, boolean rewardApplied, int pointsEarned,
                 boolean pointsAwarded) {
        this.orderID = orderID;
        this.userID = userID;
        this.status = status;
        this.pickupTime = pickupTime;
        this.totalPrice = totalPrice;
        this.rewardApplied = rewardApplied;
        this.pointsEarned = pointsEarned;
        this.pointsAwarded = pointsAwarded;
    }

    public String getOrderID() { return orderID; }
    public void setOrderID(String orderID) { this.orderID = orderID; }
    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPickupTime() { return pickupTime; }
    public void setPickupTime(String pickupTime) { this.pickupTime = pickupTime; }
    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }
    public boolean isRewardApplied() { return rewardApplied; }
    public void setRewardApplied(boolean rewardApplied) { this.rewardApplied = rewardApplied; }
    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
    public boolean isPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(boolean pointsAwarded) { this.pointsAwarded = pointsAwarded; }
}
