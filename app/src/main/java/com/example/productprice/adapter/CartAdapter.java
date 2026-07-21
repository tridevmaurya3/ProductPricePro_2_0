package com.example.productprice.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.R;
import com.example.productprice.model.CartItem;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {
    public interface OnRemoveListener {
        void onRemove(int position);
    }

    private final List<CartItem> items;
    private final OnRemoveListener listener;

    public CartAdapter(List<CartItem> items, OnRemoveListener listener) {
        this.items = items;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        CartItem item = items.get(position);
        return (item.getProduct().getId() * 31L) + item.getTier().hashCode();
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem item = items.get(position);
        holder.name.setText(item.getProduct().getName());
        holder.meta.setText(String.format(Locale.getDefault(), "%s • Qty %d • %.2f VP • ₹%d each",
                item.getTier(), item.getQuantity(), item.getTotalVp(), item.getUnitPrice()));
        holder.total.setText("₹" + item.getTotalPrice());
        holder.remove.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) listener.onRemove(adapterPosition);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView total;
        final MaterialButton remove;

        CartViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_cart_name);
            meta = itemView.findViewById(R.id.text_cart_meta);
            total = itemView.findViewById(R.id.text_cart_total);
            remove = itemView.findViewById(R.id.button_remove_cart);
        }
    }
}
