package com.example.brewloop;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brewloop.R;
import com.example.brewloop.StaffDemoOrders;
import com.example.brewloop.Order;
import com.example.brewloop.OrderItem;
import com.example.brewloop.OrderStatus;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Connects the orders in memory to RecyclerView cards on the staff dashboard.
 */
public class StaffOrderAdapter extends RecyclerView.Adapter<StaffOrderAdapter.OrderViewHolder> {

    /** Lets the Fragment decide what should happen when staff tap the action button. */
    public interface OnStatusActionListener {
        void onAdvanceStatus(Order order);
    }

    private final List<Order> orders;
    private final Map<String, List<OrderItem>> itemsByOrder;
    private final OnStatusActionListener listener;

    public StaffOrderAdapter(List<Order> orders, Map<String, List<OrderItem>> itemsByOrder,
                             OnStatusActionListener listener) {
        this.orders = orders;
        this.itemsByOrder = itemsByOrder;
        this.listener = listener;
    }

    /** Inflates one order card from its XML layout. */
    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_staff_order, parent, false);
        return new OrderViewHolder(view);
    }

    /** Populates a card with the order details and its next possible action. */
    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.orderId.setText("Order " + order.getOrderID());
        holder.customerId.setText("Customer: " + order.getUserID());
        holder.pickup.setText("Pickup " + order.getPickupTime());
        holder.total.setText(String.format(Locale.US, "$%.2f", order.getTotalPrice()));
        holder.status.setText(order.getStatus());
        holder.items.setText(createItemSummary(itemsByOrder.getOrDefault(
                order.getOrderID(), Collections.emptyList())));

        // Change the status pill color so the order's progress is easy to see.
        int color;
        if (OrderStatus.RECEIVED.equals(order.getStatus())) color = Color.rgb(105, 90, 77);
        else if (OrderStatus.PREPARING.equals(order.getStatus())) color = Color.rgb(163, 99, 31);
        else if (OrderStatus.READY.equals(order.getStatus())) color = Color.rgb(35, 111, 99);
        else color = Color.rgb(81, 102, 85);
        holder.status.setBackgroundTintList(ColorStateList.valueOf(color));

        String next = OrderStatus.next(order.getStatus());
        holder.advance.setText(OrderStatus.nextAction(order.getStatus()));
        holder.advance.setEnabled(next != null);
        holder.advance.setOnClickListener(v -> {
            if (listener != null && OrderStatus.next(order.getStatus()) != null) {
                listener.onAdvanceStatus(order);
            }
        });
    }

    @Override
    public int getItemCount() { return orders.size(); }

    /** Redraws only the order whose status has changed. */
    public void notifyOrderChanged(Order order) {
        int index = orders.indexOf(order);
        if (index >= 0) notifyItemChanged(index);
    }

    /** Builds readable text for each item in a customer's order. */
    private String createItemSummary(List<OrderItem> items) {
        if (items.isEmpty()) return "No order items found";
        StringBuilder result = new StringBuilder();
        for (OrderItem item : items) {
            if (result.length() > 0) result.append("\n");
            result.append(item.getQuantity()).append("× ")
                    .append(StaffDemoOrders.menuName(item.getItemID()));

            StringBuilder options = new StringBuilder();
            if (item.getSize() != null && !"n/a".equalsIgnoreCase(item.getSize())) {
                options.append(item.getSize());
            }
            if (item.getMilkType() != null && !"n/a".equalsIgnoreCase(item.getMilkType())) {
                if (options.length() > 0) options.append(", ");
                options.append(item.getMilkType()).append(" milk");
            }
            if (item.getExtraShots() > 0) {
                if (options.length() > 0) options.append(", ");
                options.append(item.getExtraShots()).append(" extra shot(s)");
            }
            if (options.length() > 0) result.append(" ( ").append(options).append(" )");
        }
        return result.toString();
    }

    /** Stores references to the TextViews and button inside one order card. */
    static class OrderViewHolder extends RecyclerView.ViewHolder {
        final TextView orderId, customerId, pickup, total, status, items;
        final Button advance;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            orderId = itemView.findViewById(R.id.textStaffOrderId);
            customerId = itemView.findViewById(R.id.textStaffCustomerId);
            pickup = itemView.findViewById(R.id.textStaffPickup);
            total = itemView.findViewById(R.id.textStaffTotal);
            status = itemView.findViewById(R.id.textStaffStatus);
            items = itemView.findViewById(R.id.textStaffItems);
            advance = itemView.findViewById(R.id.buttonStaffNextStatus);
        }
    }
}

