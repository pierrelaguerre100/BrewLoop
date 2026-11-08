package com.doubleshotdevs.brewloop;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows the shop menu to customers. Items are read live from the
 * "MenuItems" node in Firebase and can be filtered by category.
 */
public class MenuActivity extends AppCompatActivity {

    private static final String ALL = "All";

    private final List<MenuProduct> allItems = new ArrayList<>();
    private final List<MenuProduct> shownItems = new ArrayList<>();
    private MenuAdapter adapter;
    private TabLayout tabs;
    private DatabaseReference menuRef;
    private ValueEventListener menuListener;
    private String selectedCategory = ALL;
    private boolean rebuildingTabs = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        RecyclerView list = findViewById(R.id.menuList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MenuAdapter(shownItems, item ->
                Toast.makeText(this, item.getName() + " selected", Toast.LENGTH_SHORT).show());
        list.setAdapter(adapter);

        tabs = findViewById(R.id.categoryTabs);
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

    /** Rebuilds the category tabs from the categories found in the data. */
    private void rebuildTabs() {
        Set<String> categories = new LinkedHashSet<>();
        categories.add(ALL);
        for (MenuProduct item : allItems) {
            if (item.getCategory() != null) {
                categories.add(item.getCategory());
            }
        }
        if (!categories.contains(selectedCategory)) {
            selectedCategory = ALL;
        }
        rebuildingTabs = true;
        tabs.removeAllTabs();
        for (String category : categories) {
            TabLayout.Tab tab = tabs.newTab().setText(category);
            tabs.addTab(tab, category.equals(selectedCategory));
        }
        rebuildingTabs = false;
    }

    /** Shows only the items in the selected category. */
    private void applyFilter() {
        shownItems.clear();
        for (MenuProduct item : allItems) {
            if (ALL.equals(selectedCategory) || selectedCategory.equals(item.getCategory())) {
                shownItems.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        menuRef.removeEventListener(menuListener);
    }
}
