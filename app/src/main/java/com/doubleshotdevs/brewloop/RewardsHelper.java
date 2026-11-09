package com.doubleshotdevs.brewloop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class RewardsHelper {

    public static final int POINTS_PER_DOLLAR = 1;

    public static final int POINTS_FOR_FREE_DRINK = 50;

    public static final double MAX_REWARD_VALUE = 5.00;

    public interface RedeemListener {
        void onSuccess();

        void onFailure(String message);
    }

    public static int calculatePoints(double totalPrice) {
        return (int) (totalPrice * POINTS_PER_DOLLAR);
    }

    public static double rewardDiscount(double drinkPrice) {
        return Math.min(drinkPrice, MAX_REWARD_VALUE);
    }
    public static void awardPoints(String orderID) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        root.child("Orders").child(orderID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Order order = snapshot.getValue(Order.class);
                if (order == null || order.pointsAwarded) {
                    return;
                }
                int points = calculatePoints(order.totalPrice);
                PointsTransaction transaction =
                        new PointsTransaction(order.userID, orderID, "earn", points);

                Map<String, Object> updates = new HashMap<>();
                updates.put("PointsTransaction/" + orderID + "_earn", transaction);
                updates.put("Users/" + order.userID + "/pointsBalance", ServerValue.increment(points));
                updates.put("Orders/" + orderID + "/pointsEarned", points);
                updates.put("Orders/" + orderID + "/pointsAwarded", true);
                root.updateChildren(updates);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
    public static void redeemReward(String userID, String orderID, RedeemListener listener) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        root.child("Users").child(userID).child("pointsBalance")
                .runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        Integer balance = currentData.getValue(Integer.class);
                        if (balance == null) {
                            return Transaction.success(currentData);
                        }
                        if (balance < POINTS_FOR_FREE_DRINK) {
                            return Transaction.abort();
                        }
                        currentData.setValue(balance - POINTS_FOR_FREE_DRINK);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed,
                                           @Nullable DataSnapshot snapshot) {
                        if (error != null) {
                            listener.onFailure(error.getMessage());
                        } else if (!committed) {
                            listener.onFailure("You need " + POINTS_FOR_FREE_DRINK + " points.");
                        } else {
                            PointsTransaction transaction = new PointsTransaction(
                                    userID, orderID, "redeem", -POINTS_FOR_FREE_DRINK);
                            root.child("PointsTransaction").child(orderID + "_redeem")
                                    .setValue(transaction);
                            listener.onSuccess();
                        }
                    }
                });
    }
}
