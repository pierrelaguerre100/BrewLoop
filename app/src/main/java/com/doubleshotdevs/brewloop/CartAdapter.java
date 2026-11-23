package com.doubleshotdevs.brewloop;

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
 * Shows one row per {@link CartItem} with its quantity, line total, and a remove button.
 */
public class CartAdapter extends RecyclerView.Adapter<CartAdapter.Holder> {

    /** Called when the customer removes a line. */
    public interface OnRemove {
        void onRemove(int position);
    }

    private final List<CartItem> items;
    private final OnRemove listener;

    /**
     * @param items    the cart lines to show
     * @param listener called with the position of a line to remove
     */
    public CartAdapter(List<CartItem> items, OnRemove listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CartItem item = items.get(position);

        holder.name.setText(item.getName());

        String priceDetails = String.format(Locale.US,
                "%d x $%.2f = $%.2f",
                item.getQuantity(), item.getPrice(), item.getLineTotal());

        String options = describeOptions(item);

        if (!options.isEmpty()) {
            holder.details.setText(
                    holder.itemView.getContext().getString(
                            R.string.cart_item_details,
                            priceDetails,
                            options
                    )
            );
        } else {
            holder.details.setText(priceDetails);
        }

        holder.remove.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION) {
                listener.onRemove(current);
            }
        });
    }
    private String describeOptions(CartItem item) {
        StringBuilder options = new StringBuilder();

        if (item.getSize() != null
                && !item.getSize().isEmpty()
                && !item.getSize().equalsIgnoreCase("n/a")) {
            options.append(item.getSize());
        }

        if (item.getMilkType() != null
                && !item.getMilkType().isEmpty()
                && !item.getMilkType().equalsIgnoreCase("n/a")) {
            if (options.length() > 0) {
                options.append(" • ");
            }
            options.append(item.getMilkType());
        }

        if (item.getExtraShots() > 0) {
            if (options.length() > 0) {
                options.append(" • ");
            }
            options.append("+")
                    .append(item.getExtraShots())
                    .append(" extra shots");
        }

        return options.toString();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** Holds the views of one cart row. */
    public static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView details;
        final Button remove;

        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.cartItemName);
            details = view.findViewById(R.id.cartItemDetails);
            remove = view.findViewById(R.id.cartItemRemove);
        }
    }
}
