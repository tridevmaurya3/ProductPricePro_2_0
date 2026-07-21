package com.example.productprice.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.R;
import com.example.productprice.model.Product;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {
    public interface ProductActionListener {
        void onEdit(Product product);
        void onDelete(Product product);
    }

    private final List<Product> products = new ArrayList<>();
    private final ProductActionListener listener;

    public ProductAdapter(ProductActionListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<Product> newProducts) {
        products.clear();
        if (newProducts != null) products.addAll(newProducts);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return products.get(position).getId();
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_management, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.name.setText(product.getName());
        holder.category.setText(product.getCategory() + (product.isActive() ? "" : " • Inactive"));
        holder.name.setAlpha(product.isActive() ? 1f : 0.55f);
        holder.name.setTypeface(null, product.isActive() ? Typeface.BOLD : Typeface.NORMAL);
        holder.vp.setText(String.format(Locale.getDefault(), "VP %.2f", product.getVp()));
        holder.full.setText("Full ₹" + product.getFullPrice());
        holder.price50.setText("@50 ₹" + product.getPrice50());
        holder.edit.setOnClickListener(v -> listener.onEdit(product));
        holder.delete.setOnClickListener(v -> listener.onDelete(product));
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView category;
        final TextView vp;
        final TextView full;
        final TextView price50;
        final MaterialButton edit;
        final MaterialButton delete;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_product_name_item);
            category = itemView.findViewById(R.id.text_product_category_item);
            vp = itemView.findViewById(R.id.text_product_vp_item);
            full = itemView.findViewById(R.id.text_product_full_item);
            price50 = itemView.findViewById(R.id.text_product_price50_item);
            edit = itemView.findViewById(R.id.button_edit_product);
            delete = itemView.findViewById(R.id.button_delete_product);
        }
    }
}
