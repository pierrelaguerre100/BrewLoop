package com.doubleshotdevs.brewloop;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Cart screen with pickup date/time validation before order submission. */
public class CheckoutActivity extends AppCompatActivity {
    private static final int OPEN_HOUR = 8;
    private static final int CLOSE_HOUR = 20; // Closing time is not a valid pickup time.
    private static final List<CartItem> CART = new ArrayList<>();

    private final Calendar pickup = Calendar.getInstance();
    private TextView validationMessage;

    public static synchronized void addCartItem(CartItem item) { CART.add(item); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        validationMessage = findViewById(R.id.pickupValidationMessage);
        Calendar suggested = Calendar.getInstance();
        suggested.add(Calendar.MINUTE, 30);
        if (suggested.get(Calendar.HOUR_OF_DAY) < OPEN_HOUR) suggested.set(Calendar.HOUR_OF_DAY, OPEN_HOUR);
        if (suggested.get(Calendar.HOUR_OF_DAY) >= CLOSE_HOUR) {
            suggested.add(Calendar.DAY_OF_YEAR, 1);
            suggested.set(Calendar.HOUR_OF_DAY, OPEN_HOUR);
        }
        suggested.set(Calendar.MINUTE, 0);
        suggested.set(Calendar.SECOND, 0);
        suggested.set(Calendar.MILLISECOND, 0);
        pickup.setTimeInMillis(suggested.getTimeInMillis());

        Button dateButton = findViewById(R.id.pickupDateButton);
        Button timeButton = findViewById(R.id.pickupTimeButton);
        dateButton.setOnClickListener(v -> chooseDate());
        timeButton.setOnClickListener(v -> chooseTime());
        findViewById(R.id.placeOrderButton).setOnClickListener(v -> placeOrder());
        updatePickupLabels();
        renderCart();
    }

    private void renderCart() {
        LinearLayout cartItems = findViewById(R.id.cartItems);
        cartItems.removeAllViews();
        double total = 0;
        synchronized (CheckoutActivity.class) {
            for (CartItem item : CART) {
                TextView row = new TextView(this);
                String suffix = item.options.isEmpty() ? "" : "\n" + item.options;
                row.setText(String.format(Locale.US, "%s%s\n$%.2f", item.name, suffix, item.price));
                row.setTextSize(16);
                row.setPadding(0, 12, 0, 12);
                cartItems.addView(row);
                total += item.price;
            }
        }
        ((TextView) findViewById(R.id.cartTotal)).setText(String.format(Locale.US, "Total: $%.2f", total));
        if (total == 0) {
            TextView empty = new TextView(this);
            empty.setText("Your cart is empty.");
            empty.setTextSize(16);
            cartItems.addView(empty, 0);
        }
    }

    private void chooseDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            pickup.set(Calendar.YEAR, year);
            pickup.set(Calendar.MONTH, month);
            pickup.set(Calendar.DAY_OF_MONTH, day);
            updatePickupLabels();
            validatePickup(false);
        }, pickup.get(Calendar.YEAR), pickup.get(Calendar.MONTH), pickup.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void chooseTime() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            pickup.set(Calendar.HOUR_OF_DAY, hour);
            pickup.set(Calendar.MINUTE, minute);
            pickup.set(Calendar.SECOND, 0);
            updatePickupLabels();
            validatePickup(false);
        }, pickup.get(Calendar.HOUR_OF_DAY), pickup.get(Calendar.MINUTE), false).show();
    }

    private void updatePickupLabels() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        ((Button) findViewById(R.id.pickupDateButton)).setText("Date: " + dateFormat.format(pickup.getTime()));
        ((Button) findViewById(R.id.pickupTimeButton)).setText("Time: " + timeFormat.format(pickup.getTime()));
    }

    private boolean validatePickup(boolean showSuccess) {
        Calendar now = Calendar.getInstance();
        if (pickup.getTimeInMillis() <= now.getTimeInMillis()) {
            validationMessage.setText("Choose a pickup time in the future.");
            return false;
        }
        int hour = pickup.get(Calendar.HOUR_OF_DAY);
        if (hour < OPEN_HOUR || hour >= CLOSE_HOUR) {
            validationMessage.setText("Pickup must be between 8:00 AM and 8:00 PM.");
            return false;
        }
        validationMessage.setText("");
        return true;
    }

    private void placeOrder() {
        if (CART.isEmpty()) {
            Toast.makeText(this, "Add an item before checking out", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validatePickup(false)) return;
        Toast.makeText(this, "Pickup time confirmed. Payment can be completed at the counter.", Toast.LENGTH_LONG).show();
        synchronized (CheckoutActivity.class) { CART.clear(); }
        finish();
    }

    /** One cart row; in-memory until a persistent order backend is connected. */
    public static final class CartItem {
        final String name;
        final double price;
        final String options;
        public CartItem(String name, double price, String options) {
            this.name = name;
            this.price = price;
            this.options = options == null ? "" : options;
        }
    }
}
