package com.doubleshotdevs.brewloop;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

/**
 * Fills the menu list with one row per {@link MenuProduct}. Sold-out items
 * are grayed out and cannot be selected.
 */
public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.Holder> {

    /** Called when the customer taps an available item. */
    public interface OnItemClick {
        void onClick(MenuProduct item);
    }

    private final List<MenuProduct> items;
    private final OnItemClick listener;

    /**
     * @param items    the items to show
     * @param listener called when an available item is tapped
     */
    public MenuAdapter(List<MenuProduct> items, OnItemClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_menu, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MenuProduct item = items.get(position);
        holder.name.setText(item.getName());
        holder.description.setText(item.getDescription());
        holder.price.setText(String.format(Locale.US, "$%.2f", item.getBasePrice()));
        if (item.isAvailable()) {
            holder.itemView.setAlpha(1f);
            holder.soldOut.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> listener.onClick(item));
        } else {
            holder.itemView.setAlpha(0.4f);
            holder.soldOut.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** Holds the views of one menu row. */
    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView description;
        final TextView price;
        final TextView soldOut;

        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.itemName);
            description = view.findViewById(R.id.itemDescription);
            price = view.findViewById(R.id.itemPrice);
            soldOut = view.findViewById(R.id.itemSoldOut);
        }
    }
}
