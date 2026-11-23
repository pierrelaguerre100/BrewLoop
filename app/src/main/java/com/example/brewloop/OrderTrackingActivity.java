package com.example.brewloop;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

/**
 * Shows one customer's order and updates automatically when staff change it.
 * This screen only reads order data; customers cannot advance statuses.
 */
public class OrderTrackingActivity extends AppCompatActivity {
    public static final String EXTRA_ORDER_ID = "com.example.brewloop.ORDER_ID";

    private TextView orderIdText;
    private TextView pickupText;
    private TextView totalText;
    private TextView statusText;
    private TextView messageText;
    private TextView stepReceived;
    private TextView stepPreparing;
    private TextView stepReady;
    private TextView stepCompleted;

    private DatabaseReference orderRef;
    private ValueEventListener orderListener;
    private String customerId;

    /**
     * Opens the tracking page for a specific saved order.
     * Call after checkout successfully saves the order, or from order history.
     *
     * @param context the Activity opening the tracking screen
     * @param orderId Firebase key of the customer's order
     */
    public static void open(Context context, String orderId) {
        Intent intent = new Intent(context, OrderTrackingActivity.class);
        intent.putExtra(EXTRA_ORDER_ID, orderId);
        intent.putExtra("orderID", orderId);
        context.startActivity(intent);
    }

    /**
     * Finds the screen views and prepares the Firebase reference.
     *
     * @param savedInstanceState previous Activity state, if any
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_tracking);

        orderIdText = findViewById(R.id.textTrackingOrderId);
        pickupText = findViewById(R.id.textTrackingPickup);
        totalText = findViewById(R.id.textTrackingTotal);
        statusText = findViewById(R.id.textTrackingStatus);
        messageText = findViewById(R.id.textTrackingMessage);
        stepReceived = findViewById(R.id.textStepReceived);
        stepPreparing = findViewById(R.id.textStepPreparing);
        stepReady = findViewById(R.id.textStepReady);
        stepCompleted = findViewById(R.id.textStepCompleted);

        // The integrated customer app must supply the current customer ID.
        // This demo Intent extra does not securely authenticate the customer.
        customerId = getIntent().getStringExtra("customerID");
        if (customerId == null || customerId.trim().isEmpty()) {
            Toast.makeText(this, "Missing customer ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (orderId == null || orderId.trim().isEmpty()) {
            Toast.makeText(this, "Missing order ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Reject invalid Realtime Database path characters before calling child().
        if (orderId.contains(".") || orderId.contains("#") || orderId.contains("$")
                || orderId.contains("[") || orderId.contains("]") || orderId.contains("/")) {
            Toast.makeText(this, "Invalid order ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        orderIdText.setText("Order #" + orderId);
        messageText.setText("Loading order information...");
        orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId);
    }

    /** Starts listening whenever the tracking screen becomes visible. */
    @Override
    protected void onStart() {
        super.onStart();
        if (orderRef == null) return;

        orderListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    messageText.setText("Order not found. Check the order ID.");
                    return;
                }

                // Display only this customer's order
                String ownerId = snapshot.child("userID").getValue(String.class);
                if (!customerId.equals(ownerId)) {
                    messageText.setText("This order is not available to your account.");
                    pickupText.setText("—");
                    totalText.setText("—");
                    statusText.setText("Unavailable");
                    return;
                }

                String pickup = snapshot.child("pickupTime").getValue(String.class);
                String status = snapshot.child("status").getValue(String.class);
                Double total = snapshot.child("totalPrice").getValue(Double.class);

                pickupText.setText(pickup == null ? "Not set" : pickup);
                totalText.setText(total == null ? "Not set"
                        : String.format(Locale.US, "$%.2f", total));
                statusText.setText(OrderStatus.displayName(status));
                messageText.setText("Order updates automatically when staff make changes.");
                updateProgress(status);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                messageText.setText("Unable to load order: " + error.getMessage());
            }
        };
        orderRef.addValueEventListener(orderListener);
    }

    /** Stops the listener when the user leaves the tracking screen. */
    @Override
    protected void onStop() {
        if (orderRef != null && orderListener != null) {
            orderRef.removeEventListener(orderListener);
            orderListener = null;
        }
        super.onStop();
    }

    /**
     * Highlights every step the order has already reached.
     *
     * @param status latest status received from Firebase
     */
    private void updateProgress(String status) {
        int current = -1;
        if (OrderStatus.RECEIVED.equals(status)) current = 0;
        else if (OrderStatus.PREPARING.equals(status)) current = 1;
        else if (OrderStatus.READY.equals(status)) current = 2;
        else if (OrderStatus.COMPLETED.equals(status)) current = 3;

        updateStep(stepReceived, 0 <= current, "1. Received");
        updateStep(stepPreparing, 1 <= current, "2. Preparing");
        updateStep(stepReady, 2 <= current, "3. Ready for Pickup");
        updateStep(stepCompleted, 3 <= current, "4. Completed");
    }

    /**
     * Updates the text and color of an order tracking step.
     *
     * @param view the TextView to update
     * @param done true if the step is completed
     * @param label the name of the order status
     */
    private void updateStep(TextView view, boolean done, String label) {

        if (done == true) {

            // Display a check mark for a completed step.
            view.setText("✓  " + label);

            // Change the text color to green.
            view.setTextColor(Color.rgb(39, 92, 74));

        } else {

            // Display an empty circle for an incomplete step.
            view.setText("○  " + label);

            // Change the text color to gray.
            view.setTextColor(Color.rgb(131, 120, 111));
        }
    }
}
