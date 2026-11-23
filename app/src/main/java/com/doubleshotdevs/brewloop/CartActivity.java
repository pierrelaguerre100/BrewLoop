package com.doubleshotdevs.brewloop;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Calendar;
import java.util.Locale;

/**
 * Shows the customer's cart and lets them remove items and choose a pickup
 * time. Saving the order to Firebase is added on top of this screen.
 */
public class CartActivity extends AppCompatActivity {

    private static final int OPEN_HOUR = 7;
    private static final int CLOSE_HOUR = 18;

    private CartAdapter adapter;
    private TextView totalView;
    private Button pickupButton;
    private String pickupTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The cart belongs to the signed-in account; another account's items are cleared.
        String uid = Session.requireSignedInUid(this);
        if (uid == null) {
            return;
        }
        Session.claimCartFor(uid);
        setContentView(R.layout.activity_cart);

        totalView = findViewById(R.id.cartTotal);
        pickupButton = findViewById(R.id.pickupTimeButton);

        RecyclerView list = findViewById(R.id.cartList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartAdapter(Cart.getInstance().getItems(), position -> {
            Cart.getInstance().remove(position);
            adapter.notifyDataSetChanged();
            showTotal();
        });
        list.setAdapter(adapter);

        pickupButton.setOnClickListener(v -> choosePickupTime());
        showTotal();
    }

    /** Updates the total shown under the list. */
    private void showTotal() {
        totalView.setText(String.format(Locale.US, "Total: $%.2f",
                Cart.getInstance().getTotal()));
    }

    /** Opens a time picker and keeps the chosen time if it is valid. */
    private void choosePickupTime() {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) -> {
            if (hour < OPEN_HOUR || hour >= CLOSE_HOUR) {
                Toast.makeText(this, "Pickup must be between " + OPEN_HOUR + ":00 and "
                        + CLOSE_HOUR + ":00", Toast.LENGTH_SHORT).show();
                return;
            }
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.HOUR_OF_DAY, hour);
            chosen.set(Calendar.MINUTE, minute);
            if (chosen.before(now)) {
                Toast.makeText(this, "Pickup time cannot be in the past",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            pickupTime = String.format(Locale.US, "%02d:%02d", hour, minute);
            pickupButton.setText("Pickup at " + pickupTime);
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
    }
}
