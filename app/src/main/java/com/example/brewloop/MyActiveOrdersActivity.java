package com.example.brewloop;

import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

/**
 * Lists the customer's active orders only.
 */
public class MyActiveOrdersActivity extends AppCompatActivity {
    private LinearLayout listContainer;
    private TextView messageText;
    private Query myOrdersQuery;
    private ValueEventListener myOrdersListener;
    private String customerId;

    public static void open(Context context, String userID) {
        Intent intent = new Intent(context, MyActiveOrdersActivity.class);
        intent.putExtra("customerID", userID);
        context.startActivity(intent);
    }

    /**
     * Creates the active-orders page and gets the logged-in customer's UID.
     *
     * @param savedInstanceState previous Activity state, if any
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_active_orders);

        listContainer = findViewById(R.id.activeOrdersList);
        messageText = findViewById(R.id.textActiveOrdersMessage);

        customerId = getIntent().getStringExtra("customerID");
        if (customerId == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Only the current customer's orders belong in this list.
        DatabaseReference orders = FirebaseDatabase.getInstance().getReference("Orders");
        myOrdersQuery = orders.orderByChild("userID").equalTo(customerId);
    }

    /** Starts listening for changes to the customer's orders. */
    @Override
    protected void onStart() {
        super.onStart();
        if (myOrdersQuery == null) return;

        myOrdersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listContainer.removeAllViews();
                int active = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String status = child.child("status").getValue(String.class);
                    String orderId = child.getKey();
                    if (orderId == null || !OrderStatus.isActive(status)) continue;
                    active++;
                    addOrderButton(orderId, status);
                }
                messageText.setText(active == 0
                        ? "You have no active orders. Past orders are in Order History."
                        : "Tap an order to see its live progress.");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                messageText.setText("Unable to load orders: " + error.getMessage());
            }
        };
        myOrdersQuery.addValueEventListener(myOrdersListener);
    }

    /** Removes the listener to avoid duplicate callbacks when returning to the screen. */
    @Override
    protected void onStop() {
        if (myOrdersQuery != null && myOrdersListener != null) {
            myOrdersQuery.removeEventListener(myOrdersListener);
            myOrdersListener = null;
        }
        super.onStop();
    }

    /**
     * Adds one simple button for an active order.
     * Tapping it opens the detailed tracking screen.
     *
     * @param orderId Firebase order key
     * @param status current order status
     */
    private void addOrderButton(String orderId, String status) {
        Button button = new Button(this);
        button.setText("Order #" + orderId + "  •  "
                + OrderStatus.displayName(status));
        button.setAllCaps(false);
        button.setOnClickListener(v -> OrderTrackingActivity.open(this, orderId));
        listContainer.addView(button);
    }
}
