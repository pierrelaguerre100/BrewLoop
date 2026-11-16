package com.doubleshotdevs.brewloop;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * Shows one row per {@link MenuProduct} for the manager, with its availability
 * and Edit and Delete buttons.
 */
public class ManagerMenuAdapter extends RecyclerView.Adapter<ManagerMenuAdapter.Holder> {

    /**
     * Called when the manager taps Edit or Delete on a row.
     */
    public interface ManagerItemClick {
        /**
         * @param item the product to edit
         */
        void onEdit(MenuProduct item);

        /**
         * @param item the product to delete
         */
        void onDelete(MenuProduct item);
    }

    private final List<MenuProduct> items;
    private final ManagerItemClick listener;

    /**
     * Creates the adapter.
     *
     * @param items    the products to show
     * @param listener called when the manager taps Edit or Delete
     */
    public ManagerMenuAdapter(List<MenuProduct> items, ManagerItemClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_manager_menu, parent, false);

        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MenuProduct item = items.get(position);

        holder.name.setText(item.getName());
        holder.description.setText(item.getDescription());

        holder.price.setText(String.format(
                Locale.US, "$%.2f", item.getBasePrice()));

        if (item.isAvailable()) {
            holder.availability.setText("Available");
            holder.availability.setTextColor(Color.rgb(46, 125, 50));
        } else {
            holder.availability.setText("Sold Out");
            holder.availability.setTextColor(Color.RED);
        }

        holder.editButton.setOnClickListener(v ->
                listener.onEdit(item));

        holder.deleteButton.setOnClickListener(v ->
                listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Holds the views of one manager menu row.
     */
    static class Holder extends RecyclerView.ViewHolder {

        TextView name;
        TextView description;
        TextView price;
        TextView availability;
        Button editButton;
        Button deleteButton;

        Holder(View view) {
            super(view);

            name = view.findViewById(R.id.managerItemName);
            description = view.findViewById(R.id.managerItemDescription);
            price = view.findViewById(R.id.managerItemPrice);
            availability = view.findViewById(R.id.managerItemAvailability);

            editButton = view.findViewById(R.id.btnEditProduct);
            deleteButton = view.findViewById(R.id.btnDeleteProduct);
        }
    }
}
