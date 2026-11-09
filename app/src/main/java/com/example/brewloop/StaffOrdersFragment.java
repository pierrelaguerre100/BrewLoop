package com.example.brewloop;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brewloop.R;
import com.example.brewloop.StaffOrderAdapter;
import com.example.brewloop.StaffDemoOrders;
import com.example.brewloop.Order;
import com.example.brewloop.OrderStatus;

import java.util.List;

/**
 * Staff order queue.
 * Future weeks will replace the sample list with Firebase Realtime Database.
 */
public class StaffOrdersFragment extends Fragment {
    private List<Order> orders;
    private StaffOrderAdapter adapter;
    private TextView textActiveCount;
    private TextView textReadyCount;

    /** Displays the staff dashboard XML layout. */
    public StaffOrdersFragment() {
        super(R.layout.fragment_staff_orders);
    }

    /** Creates the RecyclerView and loads our Phase 2 sample orders. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        orders = StaffDemoOrders.createOrders();
        if (savedInstanceState != null) {
            // Keep local status changes when rotating the emulator.
            for (Order order : orders) {
                String saved = savedInstanceState.getString("status_" + order.getOrderID());
                if (saved != null) order.setStatus(saved);
            }
        }

        textActiveCount = view.findViewById(R.id.textStaffActiveCount);
        textReadyCount = view.findViewById(R.id.textStaffReadyCount);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerStaffOrders);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new StaffOrderAdapter(orders, StaffDemoOrders.createItemsByOrder(),
                order -> advanceLocalStatus(order));
        recyclerView.setAdapter(adapter);
        updateCounters();
    }

    /** Advances one sample order by exactly one step (not saved to Firebase). */
    private void advanceLocalStatus(Order order) {
        String next = OrderStatus.next(order.getStatus());
        if (next == null) return;
        order.setStatus(next);
        adapter.notifyOrderChanged(order);
        updateCounters();
        Toast.makeText(requireContext(), "Order " + order.getOrderID() + ": " + next,
                Toast.LENGTH_SHORT).show();
    }

    /** Shows how many sample orders are active and ready for pickup. */
    private void updateCounters() {
        int active = 0;
        int ready = 0;
        for (Order order : orders) {
            if (OrderStatus.isActive(order.getStatus())) active++;
            if (OrderStatus.READY.equals(order.getStatus())) ready++;
        }
        textActiveCount.setText(String.valueOf(active));
        textReadyCount.setText(String.valueOf(ready));
    }

    /** Saves local statuses across screen rotation; app restarts reset sample orders. */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (orders != null) {
            for (Order order : orders) {
                outState.putString("status_" + order.getOrderID(), order.getStatus());
            }
        }
    }
}
