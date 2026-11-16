package com.doubleshotdevs.brewloop;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shows each past order as in the order history list.
 *
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    /** The orders to show. */
    private List<Order> orders;

    /** The items of each order */
    private Map<String, List<OrderItem>> itemsByOrder;

    /** The menu item names. */
    private Map<String, String> itemNames;

    /** The order numbers that are open right now */
    private Set<String> openOrders = new HashSet<>();

    /** Saves the lists that the adapter needs. */
    public OrderAdapter(List<Order> orders, Map<String, List<OrderItem>> itemsByOrder,
                        Map<String, String> itemNames) {
        this.orders = orders;
        this.itemsByOrder = itemsByOrder;
        this.itemNames = itemNames;
    }

    /** Holds the views of one order card. */
    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView textOrderTitle, textOrderStatus, textOrderSummary, textOrderItems;

        /** Finds the views in one order card. */
        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            textOrderTitle = itemView.findViewById(R.id.textOrderTitle);
            textOrderStatus = itemView.findViewById(R.id.textOrderStatus);
            textOrderSummary = itemView.findViewById(R.id.textOrderSummary);
            textOrderItems = itemView.findViewById(R.id.textOrderItems);
        }
    }

    /** Makes a new card from item_order.xml. */
    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    /** Puts the order at this position into the card. */
    @Override
    public void onBindViewHolder(@NonNull final OrderViewHolder holder, int position) {
        final Order order = orders.get(position);

        holder.textOrderTitle.setText(order.orderID + " - Pickup " + order.pickupTime);
        holder.textOrderStatus.setText(order.status);

        String points;
        if (order.pointsAwarded) {
            points = "+" + order.pointsEarned + " points";
        } else {
            points = "Points pending";
        }
        holder.textOrderSummary.setText(
                String.format(Locale.US, "Total $%.2f - %s", order.totalPrice, points));

        holder.textOrderItems.setText(buildItemsText(order.orderID));
        if (openOrders.contains(order.orderID)) {
            holder.textOrderItems.setVisibility(View.VISIBLE);
        } else {
            holder.textOrderItems.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (openOrders.contains(order.orderID)) {
                    openOrders.remove(order.orderID);
                } else {
                    openOrders.add(order.orderID);
                }
                notifyItemChanged(holder.getBindingAdapterPosition());
            }
        });
    }

    /** Gives the number of orders in the list. */
    @Override
    public int getItemCount() {
        return orders.size();
    }

    /** Makes the text for the items of one order, one line for each item. */
    private String buildItemsText(String orderID) {
        List<OrderItem> items = itemsByOrder.get(orderID);
        if (items == null || items.isEmpty()) {
            return "No items found";
        }

        StringBuilder text = new StringBuilder();
        for (OrderItem item : items) {
            String name = itemNames.get(item.itemID);
            if (name == null) {
                name = item.itemID;
            }

            text.append(item.quantity).append(" x ");
            if (item.size != null && !item.size.equals("n/a")) {
                text.append(item.size).append(" ");
            }
            text.append(name);
            if (item.milkType != null && !item.milkType.equals("n/a")) {
                text.append(" - ").append(item.milkType);
            }
            if (item.extraShots > 0) {
                text.append(" - +").append(item.extraShots).append(" shot");
            }
            text.append("\n");
        }
        return text.toString().trim();
    }
}
