package com.doubleshotdevs.brewloop;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Lets the customer choose options supported by the selected menu item. */
public class CustomizationActivity extends AppCompatActivity {
    public static final String EXTRA_ITEM_NAME = "item_name";
    public static final String EXTRA_ITEM_CATEGORY = "item_category";
    public static final String EXTRA_ITEM_PRICE = "item_price";
    public static final String EXTRA_IS_CUSTOMIZABLE = "is_customizable";
    public static final String EXTRA_OPTIONS = "customization_options";

    private double basePrice;
    private int extraShots;
    private TextView priceView;
    private TextView shotCountView;
    private RadioGroup sizeOptions;
    private Spinner milkOptions;
    private String itemName;
    private boolean customizable;
    private List<String> options;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customization);

        Intent intent = getIntent();
        itemName = intent.getStringExtra(EXTRA_ITEM_NAME);
        if (itemName == null) itemName = "Menu item";
        String category = intent.getStringExtra(EXTRA_ITEM_CATEGORY);
        basePrice = intent.getDoubleExtra(EXTRA_ITEM_PRICE, 0.0);
        customizable = intent.getBooleanExtra(EXTRA_IS_CUSTOMIZABLE, false);
        ArrayList<String> configuredOptions = intent.getStringArrayListExtra(EXTRA_OPTIONS);
        options = configuredOptions != null ? configuredOptions
                : (customizable ? Arrays.asList("size", "milk", "shots") : new ArrayList<>());

        ((TextView) findViewById(R.id.customizationItemName)).setText(itemName);
        TextView description = findViewById(R.id.customizationDescription);
        description.setText(!options.isEmpty() ? "Choose from the options available for this item." :
                (category == null ? "This item has no customization options." : category + " · No custom options for this item."));

        sizeOptions = findViewById(R.id.sizeOptions);
        milkOptions = findViewById(R.id.milkOptions);
        priceView = findViewById(R.id.customizationPrice);
        shotCountView = findViewById(R.id.shotCount);
        findViewById(R.id.sizeSection).setVisibility(hasOption("size") ? View.VISIBLE : View.GONE);
        findViewById(R.id.milkSection).setVisibility(hasOption("milk") ? View.VISIBLE : View.GONE);
        findViewById(R.id.shotsSection).setVisibility(hasOption("shots") ? View.VISIBLE : View.GONE);

        ArrayAdapter<String> milkAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Whole milk", "Oat milk · +$0.60", "Almond milk · +$0.60", "Soy milk · +$0.60", "No milk"});
        milkAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        milkOptions.setAdapter(milkAdapter);

        sizeOptions.setOnCheckedChangeListener((group, checkedId) -> updatePrice());
        milkOptions.setOnItemSelectedListener(new SimpleItemSelectedListener(this::updatePrice));
        Button removeShot = findViewById(R.id.removeShot);
        Button addShot = findViewById(R.id.addShot);
        removeShot.setOnClickListener(v -> { if (extraShots > 0) extraShots--; updateShotCount(); updatePrice(); });
        addShot.setOnClickListener(v -> { if (extraShots < 5) extraShots++; updateShotCount(); updatePrice(); });

        findViewById(R.id.addToCartButton).setOnClickListener(v -> addToCart());
        updateShotCount();
        updatePrice();
    }

    private boolean hasOption(String option) { return options != null && options.contains(option); }

    private void updateShotCount() { shotCountView.setText(String.valueOf(extraShots)); }

    private double getItemPrice() {
        double sizeExtra = hasOption("size") && sizeOptions.getCheckedRadioButtonId() == R.id.sizeLarge ? 1.00
                : hasOption("size") && sizeOptions.getCheckedRadioButtonId() == R.id.sizeMedium ? 0.50 : 0.0;
        double milkExtra = hasOption("milk") && milkOptions.getSelectedItemPosition() > 0 && milkOptions.getSelectedItemPosition() < 4 ? 0.60 : 0.0;
        return basePrice + sizeExtra + milkExtra + (hasOption("shots") ? extraShots * 0.75 : 0.0);
    }

    private void updatePrice() {
        if (priceView != null) priceView.setText(String.format(Locale.US, "$%.2f", getItemPrice()));
    }

    private void addToCart() {
        String size = "Standard";
        String milk = "";
        List<String> selectedOptions = new ArrayList<>();
        if (hasOption("size")) {
            int selected = sizeOptions.getCheckedRadioButtonId();
            size = selected == R.id.sizeLarge ? "Large" : selected == R.id.sizeMedium ? "Medium" : "Small";
            selectedOptions.add(size);
        }
        if (hasOption("milk")) {
            milk = String.valueOf(milkOptions.getSelectedItem());
            selectedOptions.add(milk);
        }
        if (hasOption("shots") && extraShots > 0) selectedOptions.add(extraShots + " extra shot(s)");
        CheckoutActivity.addCartItem(new CheckoutActivity.CartItem(
                itemName, getItemPrice(), android.text.TextUtils.join(" · ", selectedOptions)));
        Toast.makeText(this, itemName + " added to cart", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, CheckoutActivity.class));
    }

    /** Small listener adapter to keep the price display in sync with milk selection. */
    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable onChanged;
        SimpleItemSelectedListener(Runnable onChanged) { this.onChanged = onChanged; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { onChanged.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { onChanged.run(); }
    }
}
