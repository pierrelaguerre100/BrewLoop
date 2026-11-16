package com.doubleshotdevs.brewloop;

import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
/**
 * Lets a manager add, edit, delete, and mark menu items as sold out. Changes
 * are written to the "MenuItems" node in Firebase and appear live on the
 * customer menu.
 */
public class ManagerMenuActivity extends AppCompatActivity {

    private RecyclerView managerMenuList;
    private ManagerMenuAdapter adapter;

    private final List<MenuProduct> products = new ArrayList<>();

    private DatabaseReference menuRef;
    private ValueEventListener menuListener;

    private Button btnAddProduct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_menu);

        managerMenuList = findViewById(R.id.managerMenuList);
        btnAddProduct = findViewById(R.id.btnAddProduct);

        managerMenuList.setLayoutManager(
                new LinearLayoutManager(this));

        adapter = new ManagerMenuAdapter(products,
                new ManagerMenuAdapter.ManagerItemClick() {

                    @Override
                    public void onEdit(MenuProduct item) {
                        showProductDialog(item);
                    }

                    @Override
                    public void onDelete(MenuProduct item) {
                        confirmDelete(item);
                    }
                });

        managerMenuList.setAdapter(adapter);

        menuRef = FirebaseDatabase.getInstance()
                .getReference("MenuItems");

        btnAddProduct.setOnClickListener(v ->
                showProductDialog(null));

        loadProducts();
    }

    private void loadProducts() {

        menuListener = new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                products.clear();

                for (DataSnapshot child : snapshot.getChildren()) {

                    MenuProduct item =
                            child.getValue(MenuProduct.class);

                    if (item != null) {
                        item.setItemId(child.getKey());
                        products.add(item);
                    }
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

                Toast.makeText(
                        ManagerMenuActivity.this,
                        "Could not load menu: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        };

        menuRef.addValueEventListener(menuListener);
    }
    /**
     * Shows the add/edit dialog.
     *
     * @param existingItem the item to edit, or null to add a new item
     */
    private void showProductDialog(MenuProduct existingItem) {

        boolean isEditing = existingItem != null;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        EditText nameInput = createInput("Product Name");

        EditText categoryInput = createInput("Category");

        EditText descriptionInput =
                createInput("Description");

        descriptionInput.setMinLines(2);
        descriptionInput.setGravity(
                android.view.Gravity.TOP);

        EditText priceInput = createInput("Price");

        priceInput.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        CheckBox availableCheck =
                new CheckBox(this);

        availableCheck.setText("Available");

        CheckBox customizableCheck =
                new CheckBox(this);

        customizableCheck.setText("Customizable");

        layout.addView(nameInput);
        layout.addView(categoryInput);
        layout.addView(descriptionInput);
        layout.addView(priceInput);
        layout.addView(availableCheck);
        layout.addView(customizableCheck);

        if (isEditing) {

            nameInput.setText(existingItem.getName());
            categoryInput.setText(existingItem.getCategory());
            descriptionInput.setText(
                    existingItem.getDescription());

            priceInput.setText(
                    String.valueOf(existingItem.getBasePrice()));

            availableCheck.setChecked(
                    existingItem.isAvailable());

            customizableCheck.setChecked(
                    existingItem.isCustomizable());

        } else {

            availableCheck.setChecked(true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEditing ?
                        "Edit Product" : "Add New Product")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        isEditing ? "Save Changes" : "Add",
                        null)
                .create();

        dialog.setOnShowListener(d -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        String name = nameInput.getText()
                                .toString().trim();

                        String category = categoryInput.getText()
                                .toString().trim();

                        String description = descriptionInput.getText()
                                .toString().trim();

                        String priceText = priceInput.getText()
                                .toString().trim();

                        if (name.isEmpty() ||
                                category.isEmpty() ||
                                description.isEmpty() ||
                                priceText.isEmpty()) {

                            Toast.makeText(
                                    this,
                                    "Please fill in all fields",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        double price;

                        try {
                            price = Double.parseDouble(priceText);

                            if (!Double.isFinite(price) || price < 0) {
                                throw new NumberFormatException();
                            }

                        } catch (NumberFormatException e) {

                            Toast.makeText(
                                    this,
                                    "Please enter a valid price",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        MenuProduct item = new MenuProduct(
                                name,
                                category,
                                description,
                                price,
                                availableCheck.isChecked(),
                                customizableCheck.isChecked()
                        );

                        if (isEditing) {

                            item.setItemId(existingItem.getItemId());

                            updateProduct(item);

                        } else {

                            addProduct(item);
                        }

                        dialog.dismiss();
                    });
        });

        dialog.show();
    }

    private EditText createInput(String hint) {

        EditText input = new EditText(this);

        input.setHint(hint);
        input.setTextColor(Color.BLACK);
        input.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        return input;
    }

    private void addProduct(MenuProduct item) {

        DatabaseReference newItemRef = menuRef.push();

        newItemRef.setValue(item)
                .addOnSuccessListener(unused ->

                        Toast.makeText(
                                this,
                                "Product added successfully",
                                Toast.LENGTH_SHORT
                        ).show()
                )
                .addOnFailureListener(e ->

                        Toast.makeText(
                                this,
                                "Could not add product: " +
                                        e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void updateProduct(MenuProduct item) {

        if (item.getItemId() == null) {
            return;
        }

        menuRef.child(item.getItemId())
                .setValue(item)
                .addOnSuccessListener(unused ->

                        Toast.makeText(
                                this,
                                "Product updated successfully",
                                Toast.LENGTH_SHORT
                        ).show()
                )
                .addOnFailureListener(e ->

                        Toast.makeText(
                                this,
                                "Could not update product: " +
                                        e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void confirmDelete(MenuProduct item) {

        new AlertDialog.Builder(this)
                .setTitle("Delete Product")
                .setMessage(
                        "Are you sure you want to delete " +
                                item.getName() + "?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {

                    if (item.getItemId() == null) {
                        return;
                    }

                    menuRef.child(item.getItemId())
                            .removeValue()
                            .addOnSuccessListener(unused ->

                                    Toast.makeText(
                                            this,
                                            "Product deleted",
                                            Toast.LENGTH_SHORT
                                    ).show()
                            )
                            .addOnFailureListener(e ->

                                    Toast.makeText(
                                            this,
                                            "Could not delete product: " +
                                                    e.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show()
                            );
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (menuRef != null && menuListener != null) {
            menuRef.removeEventListener(menuListener);
        }
    }
}
