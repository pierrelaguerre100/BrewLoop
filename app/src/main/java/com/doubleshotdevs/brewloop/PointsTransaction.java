package com.doubleshotdevs.brewloop;

public class PointsTransaction {
        public String transactionID;
        public String userID, orderID, type;
        public int points;
        public PointsTransaction() {
        }

        public PointsTransaction(String userID, String orderID, String type, int points) {
            this.userID = userID;
            this.orderID = orderID;
            this.type = type;
            this.points = points;
        }
    }
