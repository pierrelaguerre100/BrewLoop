package com.example.brewloop;

import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays live incoming coffee orders and allows staff to advance statuses.
 */
public class StaffOrdersFragment extends Fragment {
    private final List<Order> orders = new ArrayList<>();
    private final Map<String, List<OrderItem>> itemsByOrder = new HashMap<>();
    private final Map<String, String> menuNames = new HashMap<>();

    private StaffOrderAdapter adapter;
    private TextView activeCount, readyCount, connectionText;
    private DatabaseReference ordersRef, itemsRef, menuRef;
    private ValueEventListener ordersListener, itemsListener, menuListener;

    /** Displays the staff dashboard XML layout. */
    public StaffOrdersFragment() {

        super(R.layout.fragment_staff_orders);
    }

    /** Creates the RecyclerView and loads our Phase 2 sample orders. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        activeCount = view.findViewById(R.id.textStaffActiveCount);
        readyCount = view.findViewById(R.id.textStaffReadyCount);
        connectionText = view.findViewById(R.id.textStaffConnection);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerStaffOrders);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new StaffOrderAdapter(this::updateOrderStatus);
        recyclerView.setAdapter(adapter);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        ordersRef = database.getReference("Orders");
        itemsRef = database.getReference("OrderItems");
        menuRef = database.getReference("MenuItems");

        loadOrders();
        loadOrderItems();
        loadMenuNames();
    }
    /** Reads the Orders node and refreshes the list when an order changes. */
    private void loadOrders() {
        ordersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || adapter == null) return;
                orders.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Order order = child.getValue(Order.class);
                    if (order != null) {
                        if (order.getOrderID() == null) {
                            order.setOrderID(child.getKey());
                        }
                        orders.add(order);
                    }
                }
                refreshScreen();
                connectionText.setText("Connected: " + orders.size() + " orders");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showError("Unable to read orders: " + error.getMessage());
            }
        };
        ordersRef.addValueEventListener(ordersListener);
    }

    /** Reads each order's items, including quantities and customization choices. */
    private void loadOrderItems() {
        itemsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || adapter == null) return;
                itemsByOrder.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    OrderItem item = child.getValue(OrderItem.class);
                    if (item == null || item.getOrderID() == null) continue;

                    String orderId = item.getOrderID();
                    if (!itemsByOrder.containsKey(orderId)) {
                        itemsByOrder.put(orderId, new ArrayList<>());
                    }
                    itemsByOrder.get(orderId).add(item);
                }
                refreshScreen();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showError("Unable to read order items: " + error.getMessage());
            }
        };
        itemsRef.addValueEventListener(itemsListener);
    }

    /** Reads menu names so the order cards show Latte instead of item ID m001. */
    private void loadMenuNames() {
        menuListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || adapter == null) return;
                menuNames.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String name = child.child("name").getValue(String.class);
                    if (name != null && child.getKey() != null) {
                        menuNames.put(child.getKey(), name);
                    }
                }
                refreshScreen();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showError("Unable to read menu: " + error.getMessage());
            }
        };
        menuRef.addValueEventListener(menuListener);
    }

    /** Updates the RecyclerView and the two counts at the top of the dashboard. */
    private void refreshScreen() {
        if (adapter == null) return;
        adapter.setData(orders, itemsByOrder, menuNames);

        int active = 0;
        int ready = 0;
        for (Order order : orders) {
            if (OrderStatus.isActive(order.getStatus())) active++;
            if (OrderStatus.READY.equals(order.getStatus())) ready++;
        }
        activeCount.setText(String.valueOf(active));
        readyCount.setText(String.valueOf(ready));
    }

    /** Advances an order by exactly one step in the shared Firebase record. */
    private void updateOrderStatus(Order order) {
        if (ordersRef == null || order.getOrderID() == null) return;
        String next = OrderStatus.next(order.getStatus());
        if (next == null) return;

        ordersRef.child(order.getOrderID()).child("status")
                .runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData current) {
                        String latest = current.getValue(String.class);
                        // Only advance if the current value still matches the button.
                        if (!order.getStatus().equals(latest)) return Transaction.abort();
                        current.setValue(next);
                        return Transaction.success(current);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error,
                                           boolean committed,
                                           @Nullable DataSnapshot snapshot) {
                        if (!isAdded()) return;
                        if (error != null) {
                            showError("Status update failed: " + error.getMessage());
                        } else if (committed) {
                            Toast.makeText(requireContext(), "Changed to " + next,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(),
                                    "Order changed elsewhere. Try again.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }


    /** Displays a database error on the screen. */
    private void showError(String message) {
        if (connectionText != null) connectionText.setText(message);
    }

    /** Stops Firebase listeners when the dashboard screen is closed. */
    @Override
    public void onDestroyView() {
        if (ordersRef != null && ordersListener != null) ordersRef.removeEventListener(ordersListener);
        if (itemsRef != null && itemsListener != null) itemsRef.removeEventListener(itemsListener);
        if (menuRef != null && menuListener != null) menuRef.removeEventListener(menuListener);
        adapter = null;
        activeCount = null;
        readyCount = null;
        connectionText = null;
        super.onDestroyView();
    }
}
