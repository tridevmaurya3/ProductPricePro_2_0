package com.example.productprice.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductAdapter
        extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface ProductActionListener {

        void onEdit(Product product);

        void onDelete(Product product);
    }

    private final List<Product> products = new ArrayList<>();
    private final ProductActionListener listener;

    private final NumberFormat currencyFormat;

    public ProductAdapter(ProductActionListener listener) {
        this.listener = listener;

        setHasStableIds(true);

        currencyFormat = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );

        currencyFormat.setMaximumFractionDigits(0);
        currencyFormat.setMinimumFractionDigits(0);
    }

    public void submitList(List<Product> newProducts) {
        products.clear();

        if (newProducts != null) {
            products.addAll(newProducts);
        }

        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return products.get(position).getId();
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(
                        R.layout.item_product_management,
                        parent,
                        false
                );

        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ProductViewHolder holder,
            int position
    ) {
        Product product = products.get(position);

        holder.bind(product);
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    private String formatCurrency(int value) {
        return currencyFormat.format(
                Math.max(0, value)
        );
    }

    private String formatVp(double value) {
        if (value == Math.rint(value)) {
            return String.format(
                    Locale.getDefault(),
                    "%.0f VP",
                    value
            );
        }

        return String.format(
                Locale.getDefault(),
                "%.2f VP",
                value
        );
    }

    class ProductViewHolder
            extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView category;
        private final TextView status;

        private final TextView vp;
        private final TextView fullPrice;
        private final TextView price15;
        private final TextView price25;
        private final TextView price35;
        private final TextView price42;
        private final TextView price50;

        private final MaterialButton editButton;
        private final MaterialButton deleteButton;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);

            name = itemView.findViewById(
                    R.id.text_product_name_item
            );

            category = itemView.findViewById(
                    R.id.text_product_category_item
            );

            status = itemView.findViewById(
                    R.id.text_product_status_item
            );

            vp = itemView.findViewById(
                    R.id.text_product_vp_item
            );

            fullPrice = itemView.findViewById(
                    R.id.text_product_full_item
            );

            price15 = itemView.findViewById(
                    R.id.text_product_price15_item
            );

            price25 = itemView.findViewById(
                    R.id.text_product_price25_item
            );

            price35 = itemView.findViewById(
                    R.id.text_product_price35_item
            );

            price42 = itemView.findViewById(
                    R.id.text_product_price42_item
            );

            price50 = itemView.findViewById(
                    R.id.text_product_price50_item
            );

            editButton = itemView.findViewById(
                    R.id.button_edit_product
            );

            deleteButton = itemView.findViewById(
                    R.id.button_delete_product
            );
        }

        void bind(Product product) {
            name.setText(
                    safeText(product.getName())
            );

            category.setText(
                    safeText(product.getCategory())
            );

            vp.setText(
                    formatVp(product.getVp())
            );

            fullPrice.setText(
                    formatCurrency(
                            product.getFullPrice()
                    )
            );

            price15.setText(
                    formatCurrency(
                            product.getPrice15()
                    )
            );

            price25.setText(
                    formatCurrency(
                            product.getPrice25()
                    )
            );

            price35.setText(
                    formatCurrency(
                            product.getPrice35()
                    )
            );

            price42.setText(
                    formatCurrency(
                            product.getPrice42()
                    )
            );

            price50.setText(
                    formatCurrency(
                            product.getPrice50()
                    )
            );

            updateProductStatus(
                    product.isActive()
            );

            float contentAlpha = product.isActive()
                    ? 1.0f
                    : 0.58f;

            name.setAlpha(contentAlpha);
            category.setAlpha(contentAlpha);

            vp.setAlpha(contentAlpha);
            fullPrice.setAlpha(contentAlpha);
            price15.setAlpha(contentAlpha);
            price25.setAlpha(contentAlpha);
            price35.setAlpha(contentAlpha);
            price42.setAlpha(contentAlpha);
            price50.setAlpha(contentAlpha);

            name.setTypeface(
                    null,
                    product.isActive()
                            ? Typeface.BOLD
                            : Typeface.NORMAL
            );

            editButton.setOnClickListener(view -> {
                if (listener != null) {
                    listener.onEdit(product);
                }
            });

            deleteButton.setOnClickListener(view -> {
                if (listener != null) {
                    listener.onDelete(product);
                }
            });

            /*
             * पूरे card पर click करने से product edit खुलेगा।
             */
            itemView.setOnClickListener(view -> {
                if (listener != null) {
                    listener.onEdit(product);
                }
            });
        }

        private void updateProductStatus(boolean active) {
            if (active) {
                status.setText("Active");

                status.setTextColor(
                        Color.parseColor("#107C10")
                );

                status.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor("#E8F3E8")
                        )
                );

            } else {
                status.setText("Inactive");

                status.setTextColor(
                        Color.parseColor("#616161")
                );

                status.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor("#F0F0F0")
                        )
                );
            }
        }

        private String safeText(String value) {
            if (value == null) {
                return "";
            }

            return value.trim();
        }
    }
}