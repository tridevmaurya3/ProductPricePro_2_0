package com.example.productprice.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.R;
import com.example.productprice.model.Category;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryAdapter
        extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface CategoryActionListener {

        void onEditCategory(Category category);

        void onDeleteCategory(Category category);

        void onCategoryClick(Category category);
    }

    public interface ProductCountProvider {

        int getProductCount(String categoryName);
    }

    private final Context context;
    private final CategoryActionListener actionListener;
    private final ProductCountProvider productCountProvider;

    private final List<Category> allCategories = new ArrayList<>();
    private final List<Category> visibleCategories = new ArrayList<>();

    private String currentSearchQuery = "";

    public CategoryAdapter(
            Context context,
            CategoryActionListener actionListener,
            ProductCountProvider productCountProvider
    ) {
        this.context = context;
        this.actionListener = actionListener;
        this.productCountProvider = productCountProvider;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(
                        R.layout.item_category,
                        parent,
                        false
                );

        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull CategoryViewHolder holder,
            int position
    ) {
        Category category = visibleCategories.get(position);

        holder.bind(category);
    }

    @Override
    public int getItemCount() {
        return visibleCategories.size();
    }

    public void submitList(List<Category> categories) {
        allCategories.clear();

        if (categories != null) {
            allCategories.addAll(categories);
        }

        applyFilter();
    }

    public void filter(String query) {
        currentSearchQuery = query == null
                ? ""
                : query.trim();

        applyFilter();
    }

    public int getVisibleCount() {
        return visibleCategories.size();
    }

    public int getTotalCount() {
        return allCategories.size();
    }

    private void applyFilter() {
        visibleCategories.clear();

        String normalizedQuery = currentSearchQuery
                .toLowerCase(Locale.ROOT);

        if (normalizedQuery.isEmpty()) {
            visibleCategories.addAll(allCategories);

        } else {
            for (Category category : allCategories) {
                String categoryName = category.getName() == null
                        ? ""
                        : category.getName();

                if (categoryName
                        .toLowerCase(Locale.ROOT)
                        .contains(normalizedQuery)) {
                    visibleCategories.add(category);
                }
            }
        }

        notifyDataSetChanged();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {

        private final TextView textCategoryName;
        private final TextView textProductCount;
        private final TextView textCategoryStatus;

        private final MaterialButton buttonEdit;
        private final MaterialButton buttonDelete;

        public CategoryViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            textCategoryName = itemView.findViewById(
                    R.id.text_category_name
            );

            textProductCount = itemView.findViewById(
                    R.id.text_category_product_count
            );

            textCategoryStatus = itemView.findViewById(
                    R.id.text_category_status
            );

            buttonEdit = itemView.findViewById(
                    R.id.button_edit_category
            );

            buttonDelete = itemView.findViewById(
                    R.id.button_delete_category
            );
        }

        void bind(Category category) {
            String categoryName = category.getName() == null
                    ? ""
                    : category.getName();

            textCategoryName.setText(categoryName);

            int productCount = 0;

            if (productCountProvider != null) {
                productCount = productCountProvider
                        .getProductCount(categoryName);
            }

            if (productCount == 1) {
                textProductCount.setText("1 product");
            } else {
                textProductCount.setText(
                        productCount + " products"
                );
            }

            updateStatusAppearance(category.isActive());

            itemView.setOnClickListener(view -> {
                if (actionListener != null) {
                    actionListener.onCategoryClick(category);
                }
            });

            buttonEdit.setOnClickListener(view -> {
                if (actionListener != null) {
                    actionListener.onEditCategory(category);
                }
            });

            buttonDelete.setOnClickListener(view -> {
                if (actionListener != null) {
                    actionListener.onDeleteCategory(category);
                }
            });
        }

        private void updateStatusAppearance(boolean active) {
            if (active) {
                textCategoryStatus.setText("Active");

                textCategoryStatus.setTextColor(
                        Color.parseColor("#107C10")
                );

                textCategoryStatus.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor("#E8F3E8")
                        )
                );

            } else {
                textCategoryStatus.setText("Inactive");

                textCategoryStatus.setTextColor(
                        Color.parseColor("#616161")
                );

                textCategoryStatus.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor("#F0F0F0")
                        )
                );
            }
        }
    }
}