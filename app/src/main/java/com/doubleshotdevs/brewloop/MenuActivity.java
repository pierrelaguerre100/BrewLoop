package com.doubleshotdevs.brewloop;

import android.os.Bundle;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shows the shop menu to customers. Items are read live from the
 * "MenuItems" node in Firebase and can be filtered by category.
 */
public class MenuActivity extends AppCompatActivity {

    private final List<MenuProduct> allItems = new ArrayList<>();
    private final List<MenuProduct> shownItems = new ArrayList<>();
    private MenuAdapter adapter;
    private TabLayout tabs;
    private ImageView categoryImage;
    private TextView categoryTitle;
    private TextView categorySubtitle;
    private DatabaseReference menuRef;
    private ValueEventListener menuListener;
    private String selectedCategory;
    private boolean rebuildingTabs = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        RecyclerView list = findViewById(R.id.menuList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MenuAdapter(shownItems, item -> {
            Intent intent = new Intent(this, CustomizationActivity.class);
            intent.putExtra(CustomizationActivity.EXTRA_ITEM_NAME, item.getName());
            intent.putExtra(CustomizationActivity.EXTRA_ITEM_CATEGORY, item.getCategory());
            intent.putExtra(CustomizationActivity.EXTRA_ITEM_PRICE, item.getBasePrice());
            intent.putExtra(CustomizationActivity.EXTRA_IS_CUSTOMIZABLE, item.isCustomizable());
            if (item.getCustomizationOptions() != null) {
                intent.putStringArrayListExtra(CustomizationActivity.EXTRA_OPTIONS,
                        new java.util.ArrayList<>(item.getCustomizationOptions()));
            }
            startActivity(intent);
        });
        list.setAdapter(adapter);

        tabs = findViewById(R.id.categoryTabs);
        categoryImage = findViewById(R.id.categoryImage);
        categoryTitle = findViewById(R.id.categoryTitle);
        categorySubtitle = findViewById(R.id.categorySubtitle);
        findViewById(R.id.menuNavHome).setOnClickListener(view -> finish());
        findViewById(R.id.menuNavFavorites).setOnClickListener(view ->
                Toast.makeText(this, "Favorites coming soon", Toast.LENGTH_SHORT).show());
        findViewById(R.id.menuNavCart).setOnClickListener(view ->
                Toast.makeText(this, "Cart coming soon", Toast.LENGTH_SHORT).show());
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (rebuildingTabs) {
                    return;
                }
                selectedCategory = String.valueOf(tab.getText());
                applyFilter();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });

        if (FirebaseApp.getApps(this).isEmpty()) {
            showPreviewMenu();
            return;
        }

        menuRef = FirebaseDatabase.getInstance().getReference("MenuItems");
        menuListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allItems.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    MenuProduct item = child.getValue(MenuProduct.class);
                    if (item != null) {
                        item.setItemId(child.getKey());
                        allItems.add(item);
                    }
                }
                rebuildTabs();
                applyFilter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MenuActivity.this, "Could not load menu", Toast.LENGTH_SHORT).show();
            }
        };
        menuRef.addValueEventListener(menuListener);
    }

    /** Supplies sample products so the app remains usable without private Firebase credentials. */
    private void showPreviewMenu() {
        allItems.clear();
        allItems.add(new MenuProduct("Espresso", "Coffee", "Bold, rich, and freshly pulled", 3.00, true, true));
        allItems.add(new MenuProduct("Cappuccino", "Coffee", "Espresso with silky steamed milk", 4.00, true, true));
        allItems.add(new MenuProduct("Latte", "Coffee", "Smooth espresso and steamed milk", 4.50, true, true));
        allItems.add(new MenuProduct("Hot Milk", "Milk", "Creamy steamed milk", 2.50, true, true));
        allItems.add(new MenuProduct("Chocolate Milk", "Milk", "Warm chocolate topped with foam", 3.50, true, true));
        allItems.add(new MenuProduct("Matcha Milk", "Milk", "Earthy matcha with steamed milk", 3.50, true, true));
        allItems.add(new MenuProduct("Black Tea", "Tea", "A fragrant classic cup of tea", 3.00, true, false));
        allItems.add(new MenuProduct("Green Tea", "Tea", "Delicate green tea leaves", 3.00, true, false));
        allItems.add(new MenuProduct("Earl Grey Tea", "Tea", "Citrus-forward black tea", 3.50, true, false));
        rebuildTabs();
        applyFilter();
        Toast.makeText(this, "Showing sample menu. Add Firebase configuration to load the live menu.",
                Toast.LENGTH_LONG).show();
    }

    /** Rebuilds the category tabs from the categories found in the data. */
    private void rebuildTabs() {
        Set<String> categories = new LinkedHashSet<>();
        for (MenuProduct item : allItems) {
            if (item.getCategory() != null) {
                categories.add(item.getCategory());
            }
        }
        if (!categories.contains(selectedCategory)) {
            selectedCategory = categories.isEmpty() ? null : categories.iterator().next();
        }
        rebuildingTabs = true;
        tabs.removeAllTabs();
        for (String category : categories) {
            TabLayout.Tab tab = tabs.newTab().setText(category);
            tabs.addTab(tab, category.equals(selectedCategory));
        }
        rebuildingTabs = false;
    }

    /** Shows the items for the selected visual category. */
    private void applyFilter() {
        shownItems.clear();
        for (MenuProduct item : allItems) {
            if (selectedCategory != null && selectedCategory.equals(item.getCategory())) {
                shownItems.add(item);
            }
        }
        updateCategoryHeader();
        adapter.notifyDataSetChanged();
    }

    /** Keeps the category card aligned with the active tab. */
    private void updateCategoryHeader() {
        String category = selectedCategory == null ? "Hot Drinks" : selectedCategory;
        String normalized = category.toLowerCase(Locale.US);
        categoryTitle.setText(category.toUpperCase(Locale.US));
        categorySubtitle.setText("Freshly prepared just for you");
        if (normalized.contains("milk")) {
            categoryImage.setImageResource(R.drawable.menu_category_milk);
        } else if (normalized.contains("tea")) {
            categoryImage.setImageResource(R.drawable.menu_category_tea);
        } else {
            categoryImage.setImageResource(R.drawable.menu_category_coffee);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (menuRef != null && menuListener != null) {
            menuRef.removeEventListener(menuListener);
        }
    }
}
