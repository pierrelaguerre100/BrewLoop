package com.doubleshotdevs.brewloop;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Shows the finished orders of one customer.
 * Tapping an order shows the items in it.
 */
public class OrderHistoryActivity extends AppCompatActivity {

    /** The key used to send the user id to this screen. */
    public static final String EXTRA_USER_ID = "userID";

    /** The user id used until login is finished. */
    private static final String DEFAULT_USER_ID = "u001";

    private TextView textViewEmptyOrders;
    private OrderAdapter orderAdapter;
    private FirebaseDatabase database;
    private String userID;

    private ArrayList<Order> orders = new ArrayList<>();
    private HashMap<String, List<OrderItem>> itemsByOrder = new HashMap<>();
    private HashMap<String, String> itemNames = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        findViewById(R.id.buttonBackOrders).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        textViewEmptyOrders = findViewById(R.id.textViewEmptyOrders);
        RecyclerView recyclerOrders = findViewById(R.id.recyclerOrders);
        recyclerOrders.setLayoutManager(new LinearLayoutManager(this));
        orderAdapter = new OrderAdapter(orders, itemsByOrder, itemNames);
        recyclerOrders.setAdapter(orderAdapter);

        userID = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userID == null) {
            userID = DEFAULT_USER_ID;
        }

        database = FirebaseDatabase.getInstance();
        loadMenuNames();
    }

    /** Reads the menu item names, then loads the order items. */
    private void loadMenuNames() {
        database.getReference("MenuItems").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemNames.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String name = child.child("name").getValue(String.class);
                    if (name != null) {
                        itemNames.put(child.getKey(), name);
                    }
                }
                loadOrderItems();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(OrderHistoryActivity.this, "Could not load menu", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Reads all order items and groups them by order number, then loads the orders. */
    private void loadOrderItems() {
        database.getReference("OrderItems").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                itemsByOrder.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    OrderItem item = child.getValue(OrderItem.class);
                    if (item == null) {
                        continue;
                    }
                    item.orderItemID = child.getKey();

                    List<OrderItem> list = itemsByOrder.get(item.orderID);
                    if (list == null) {
                        list = new ArrayList<>();
                        itemsByOrder.put(item.orderID, list);
                    }
                    list.add(item);
                }
                loadOrders();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(OrderHistoryActivity.this, "Could not load items", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Reads the completed orders of this user, newest first, and shows them. */
    private void loadOrders() {
        database.getReference("Orders").orderByChild("userID").equalTo(userID)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        orders.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Order order = child.getValue(Order.class);
                            if (order != null && "Completed".equals(order.status)) {
                                order.orderID = child.getKey();
                                orders.add(order);
                            }
                        }

                        Collections.sort(orders, new Comparator<Order>() {
                            @Override
                            public int compare(Order a, Order b) {
                                return b.orderID.compareTo(a.orderID);
                            }
                        });

                        orderAdapter.notifyDataSetChanged();
                        if (orders.isEmpty()) {
                            textViewEmptyOrders.setVisibility(View.VISIBLE);
                        } else {
                            textViewEmptyOrders.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(OrderHistoryActivity.this, "Could not load orders", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
