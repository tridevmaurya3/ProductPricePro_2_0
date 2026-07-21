package com.example.productprice;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.adapter.CategoryAdapter;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.Category;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public class CategoryManagerActivity extends AppCompatActivity
        implements CategoryAdapter.CategoryActionListener {

    private MaterialToolbar toolbar;

    private RecyclerView recyclerCategories;

    private View emptyState;

    private TextView textCategoryCount;

    private TextInputEditText editSearchCategory;

    private MaterialButton buttonAddCategoryTop;
    private MaterialButton buttonAddFirstCategory;

    private ExtendedFloatingActionButton fabAddCategory;

    private ProductDbHelper databaseHelper;
    private CategoryAdapter categoryAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_manager);

        initializeViews();
        setupToolbar();
        setupDatabase();
        setupRecyclerView();
        setupSearch();
        setupButtons();

        loadCategories();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (databaseHelper != null
                && categoryAdapter != null) {
            loadCategories();
        }
    }

    private void initializeViews() {
        toolbar = findViewById(
                R.id.category_manager_toolbar
        );

        recyclerCategories = findViewById(
                R.id.recycler_categories
        );

        emptyState = findViewById(
                R.id.category_empty_state
        );

        textCategoryCount = findViewById(
                R.id.text_category_count
        );

        editSearchCategory = findViewById(
                R.id.edit_search_category
        );

        buttonAddCategoryTop = findViewById(
                R.id.button_add_category_top
        );

        buttonAddFirstCategory = findViewById(
                R.id.button_add_first_category
        );

        fabAddCategory = findViewById(
                R.id.fab_add_category
        );
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(
                view -> finish()
        );

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(
                    "Category Manager"
            );
        }
    }

    private void setupDatabase() {
        databaseHelper = ProductDbHelper.getInstance(
                this
        );

        databaseHelper.initialize();
    }

    private void setupRecyclerView() {
        categoryAdapter = new CategoryAdapter(
                this,
                this,
                categoryName ->
                        databaseHelper.getCategoryProductCount(
                                categoryName
                        )
        );

        recyclerCategories.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recyclerCategories.setHasFixedSize(false);
        recyclerCategories.setAdapter(categoryAdapter);
    }

    private void setupSearch() {
        editSearchCategory.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence text,
                            int start,
                            int count,
                            int after
                    ) {
                        // No action required.
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence text,
                            int start,
                            int before,
                            int count
                    ) {
                        categoryAdapter.filter(
                                text == null
                                        ? ""
                                        : text.toString()
                        );

                        updateScreenState();
                    }

                    @Override
                    public void afterTextChanged(
                            Editable editable
                    ) {
                        // No action required.
                    }
                }
        );
    }

    private void setupButtons() {
        View.OnClickListener addCategoryListener =
                view -> showCategoryDialog(null);

        buttonAddCategoryTop.setOnClickListener(
                addCategoryListener
        );

        buttonAddFirstCategory.setOnClickListener(
                addCategoryListener
        );

        fabAddCategory.setOnClickListener(
                addCategoryListener
        );
    }

    private void loadCategories() {
        List<Category> categories =
                databaseHelper.getAllCategories(false);

        categoryAdapter.submitList(categories);

        updateScreenState();
    }

    private void updateScreenState() {
        int totalCount = categoryAdapter.getTotalCount();
        int visibleCount = categoryAdapter.getVisibleCount();

        String searchText = editSearchCategory
                .getText() == null
                ? ""
                : editSearchCategory
                .getText()
                .toString()
                .trim();

        if (searchText.isEmpty()) {
            if (totalCount == 1) {
                textCategoryCount.setText(
                        "1 category"
                );
            } else {
                textCategoryCount.setText(
                        totalCount + " categories"
                );
            }

        } else {
            textCategoryCount.setText(
                    visibleCount
                            + " of "
                            + totalCount
                            + " categories"
            );
        }

        boolean listEmpty = visibleCount == 0;

        recyclerCategories.setVisibility(
                listEmpty
                        ? View.GONE
                        : View.VISIBLE
        );

        emptyState.setVisibility(
                listEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        /*
         * Empty search result में Add First Category button
         * दिखाने की जरूरत नहीं है।
         */
        if (buttonAddFirstCategory != null) {
            buttonAddFirstCategory.setVisibility(
                    totalCount == 0
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    private void showCategoryDialog(
            @Nullable Category categoryToEdit
    ) {
        boolean editing = categoryToEdit != null;

        View dialogView = LayoutInflater
                .from(this)
                .inflate(
                        R.layout.dialog_category,
                        null,
                        false
                );

        TextView textDialogTitle = dialogView.findViewById(
                R.id.text_category_dialog_title
        );

        TextView textDialogSubtitle = dialogView.findViewById(
                R.id.text_category_dialog_subtitle
        );

        TextInputLayout layoutCategoryName =
                dialogView.findViewById(
                        R.id.layout_category_name
                );

        TextInputEditText editCategoryName =
                dialogView.findViewById(
                        R.id.edit_category_name
                );

        SwitchMaterial switchCategoryActive =
                dialogView.findViewById(
                        R.id.switch_category_active
                );

        MaterialButton buttonCancel =
                dialogView.findViewById(
                        R.id.button_cancel_category
                );

        MaterialButton buttonSave =
                dialogView.findViewById(
                        R.id.button_save_category
                );

        if (editing) {
            textDialogTitle.setText(
                    "Edit Category"
            );

            textDialogSubtitle.setText(
                    "Update category name and status."
            );

            editCategoryName.setText(
                    categoryToEdit.getName()
            );

            editCategoryName.setSelection(
                    editCategoryName
                            .getText() == null
                            ? 0
                            : editCategoryName
                            .getText()
                            .length()
            );

            switchCategoryActive.setChecked(
                    categoryToEdit.isActive()
            );

            buttonSave.setText(
                    "Update Category"
            );

        } else {
            textDialogTitle.setText(
                    "Add Category"
            );

            textDialogSubtitle.setText(
                    "Create a category for organizing products."
            );

            switchCategoryActive.setChecked(true);

            buttonSave.setText(
                    "Save Category"
            );
        }

        updateSwitchText(switchCategoryActive);

        switchCategoryActive.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        updateSwitchText(
                                switchCategoryActive
                        )
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialog.setOnShowListener(unused -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams
                                .SOFT_INPUT_ADJUST_RESIZE
                );
            }

            editCategoryName.requestFocus();

            editCategoryName.postDelayed(
                    () -> showKeyboard(
                            editCategoryName
                    ),
                    250
            );
        });

        buttonCancel.setOnClickListener(
                view -> dialog.dismiss()
        );

        buttonSave.setOnClickListener(view -> {
            layoutCategoryName.setError(null);

            String categoryName =
                    editCategoryName.getText() == null
                            ? ""
                            : editCategoryName
                            .getText()
                            .toString()
                            .trim()
                            .replaceAll(
                                    "\\s+",
                                    " "
                            );

            if (categoryName.isEmpty()) {
                layoutCategoryName.setError(
                        "Category name required"
                );

                editCategoryName.requestFocus();
                return;
            }

            if (categoryName.length() < 2) {
                layoutCategoryName.setError(
                        "Enter at least 2 characters"
                );

                editCategoryName.requestFocus();
                return;
            }

            Category existingCategory =
                    databaseHelper.getCategoryByName(
                            categoryName
                    );

            if (existingCategory != null
                    && (!editing
                    || existingCategory.getId()
                    != categoryToEdit.getId())) {
                layoutCategoryName.setError(
                        "This category already exists"
                );

                editCategoryName.requestFocus();
                return;
            }

            Category category;

            if (editing) {
                category = new Category(
                        categoryToEdit.getId(),
                        categoryName,
                        switchCategoryActive.isChecked(),
                        categoryToEdit.getCreatedAt(),
                        System.currentTimeMillis()
                );

            } else {
                category = new Category(
                        categoryName
                );

                category.setActive(
                        switchCategoryActive.isChecked()
                );
            }

            long result =
                    databaseHelper.saveCategory(
                            category
                    );

            if (result == -2) {
                layoutCategoryName.setError(
                        "This category already exists"
                );

                return;
            }

            if (result <= 0) {
                Toast.makeText(
                        this,
                        "Category save नहीं हो सकी",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            dialog.dismiss();

            Toast.makeText(
                    this,
                    editing
                            ? "Category updated"
                            : "Category added",
                    Toast.LENGTH_SHORT
            ).show();

            loadCategories();
        });

        dialog.show();
    }

    private void updateSwitchText(
            SwitchMaterial switchMaterial
    ) {
        if (switchMaterial.isChecked()) {
            switchMaterial.setText("Active");
            switchMaterial.setTextColor(
                    android.graphics.Color.parseColor(
                            "#107C10"
                    )
            );

        } else {
            switchMaterial.setText("Inactive");
            switchMaterial.setTextColor(
                    android.graphics.Color.parseColor(
                            "#616161"
                    )
            );
        }
    }

    private void showDeleteConfirmation(
            Category category
    ) {
        int productCount =
                databaseHelper.getCategoryProductCount(
                        category.getName()
                );

        if (productCount > 0) {
            new AlertDialog.Builder(this)
                    .setTitle(
                            "Category cannot be deleted"
                    )
                    .setMessage(
                            "\""
                                    + category.getName()
                                    + "\" contains "
                                    + productCount
                                    + (productCount == 1
                                    ? " product."
                                    : " products.")
                                    + "\n\nMove or delete those products first. "
                                    + "You can also mark this category inactive."
                    )
                    .setPositiveButton(
                            "Mark Inactive",
                            (dialog, which) -> {
                                boolean updated =
                                        databaseHelper
                                                .setCategoryActive(
                                                        category.getId(),
                                                        false
                                                );

                                if (updated) {
                                    Toast.makeText(
                                            this,
                                            "Category marked inactive",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    loadCategories();

                                } else {
                                    Toast.makeText(
                                            this,
                                            "Category update failed",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                    )
                    .setNegativeButton(
                            "Cancel",
                            null
                    )
                    .show();

            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage(
                        "Delete \""
                                + category.getName()
                                + "\"?\n\nThis action cannot be undone."
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> {
                            int result =
                                    databaseHelper
                                            .deleteCategory(
                                                    category.getId()
                                            );

                            if (result == 1) {
                                Toast.makeText(
                                        this,
                                        "Category deleted",
                                        Toast.LENGTH_SHORT
                                ).show();

                                loadCategories();

                            } else if (result == -1) {
                                Toast.makeText(
                                        this,
                                        "Category contains products",
                                        Toast.LENGTH_SHORT
                                ).show();

                            } else {
                                Toast.makeText(
                                        this,
                                        "Category delete failed",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void showKeyboard(View view) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(
                    view,
                    InputMethodManager.SHOW_IMPLICIT
            );
        }
    }

    @Override
    public void onEditCategory(Category category) {
        showCategoryDialog(category);
    }

    @Override
    public void onDeleteCategory(Category category) {
        showDeleteConfirmation(category);
    }

    @Override
    public void onCategoryClick(Category category) {
        showCategoryDialog(category);
    }
}