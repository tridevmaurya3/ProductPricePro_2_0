package com.example.productprice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.adapter.ProductAdapter;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.Product;
import com.example.productprice.util.ExportUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProductManagementActivity extends AppCompatActivity
        implements ProductAdapter.ProductActionListener {

    private static final int REQUEST_IMPORT_CSV = 401;
    private static final int REQUEST_EXPORT_XLSX = 402;
    private static final int REQUEST_EXPORT_PDF = 403;

    private static final int MENU_MANAGE_CATEGORIES = 901;

    private ProductDbHelper db;
    private ProductAdapter adapter;

    private TextInputEditText searchInput;
    private TextView countText;
    private TextView emptyText;

    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_management);

        db = ProductDbHelper.getInstance(this);
        db.initialize();

        setupToolbar();
        setupList();
        setupSearch();
        setupAddProductButton();

        refreshProducts();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (db != null && adapter != null) {
            refreshProducts();
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(
                R.id.toolbar_products
        );

        toolbar.setNavigationOnClickListener(
                view -> finish()
        );

        toolbar.inflateMenu(
                R.menu.menu_product_management
        );

        Menu toolbarMenu = toolbar.getMenu();

        MenuItem categoryMenuItem = toolbarMenu.add(
                Menu.NONE,
                MENU_MANAGE_CATEGORIES,
                Menu.NONE,
                "Manage Categories"
        );

        categoryMenuItem.setShowAsAction(
                MenuItem.SHOW_AS_ACTION_NEVER
        );

        toolbar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();

            if (itemId == MENU_MANAGE_CATEGORIES) {
                openCategoryManager();
                return true;
            }

            if (itemId == R.id.action_import_csv) {
                openCsvPicker();
                return true;
            }

            if (itemId == R.id.action_export_excel) {
                createCatalogFile(false);
                return true;
            }

            if (itemId == R.id.action_export_pdf) {
                createCatalogFile(true);
                return true;
            }

            return false;
        });
    }

    private void openCategoryManager() {
        Intent intent = new Intent(
                this,
                CategoryManagerActivity.class
        );

        startActivity(intent);
    }

    private void setupList() {
        RecyclerView recyclerView = findViewById(
                R.id.recycler_products
        );

        countText = findViewById(
                R.id.text_product_count
        );

        emptyText = findViewById(
                R.id.text_empty_products
        );

        adapter = new ProductAdapter(this);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recyclerView.setHasFixedSize(false);
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        searchInput = findViewById(
                R.id.input_search_product
        );

        searchInput.addTextChangedListener(
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
                        currentQuery = text == null
                                ? ""
                                : text.toString();

                        refreshProducts();
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

    private void setupAddProductButton() {
        ExtendedFloatingActionButton addButton =
                findViewById(
                        R.id.fab_add_product
                );

        addButton.setOnClickListener(view -> {
            List<String> categories =
                    db.getCategories();

            if (categories.isEmpty()) {
                showNoCategoryDialog();
            } else {
                showProductDialog(null);
            }
        });
    }

    private void showNoCategoryDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("No category available")
                .setMessage(
                        "Product जोड़ने से पहले कम से कम एक active category बनाना जरूरी है."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Manage Categories",
                        (dialog, which) ->
                                openCategoryManager()
                )
                .show();
    }

    private void refreshProducts() {
        List<Product> products;

        if (currentQuery.trim().isEmpty()) {
            products = db.getAllProducts(false);
        } else {
            products = db.searchProducts(
                    currentQuery
            );
        }

        adapter.submitList(products);

        if (products.size() == 1) {
            countText.setText("1 product");
        } else {
            countText.setText(
                    products.size() + " products"
            );
        }

        emptyText.setVisibility(
                products.isEmpty()
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    @Override
    public void onEdit(Product product) {
        showProductDialog(product);
    }

    @Override
    public void onDelete(Product product) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete product?")
                .setMessage(
                        product.getName()
                                + " will be permanently removed "
                                + "from the app database."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> {
                            boolean deleted =
                                    db.deleteProduct(
                                            product.getId()
                                    );

                            if (deleted) {
                                Toast.makeText(
                                        this,
                                        "Product deleted",
                                        Toast.LENGTH_SHORT
                                ).show();

                                refreshProducts();

                            } else {
                                Toast.makeText(
                                        this,
                                        "Product delete failed",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                )
                .show();
    }

    private void showProductDialog(
            @Nullable Product existing
    ) {
        List<String> activeCategories =
                new ArrayList<>(db.getCategories());

        if (existing == null
                && activeCategories.isEmpty()) {
            showNoCategoryDialog();
            return;
        }

        /*
         * यदि existing product inactive category में है,
         * तो edit करते समय वह category dropdown में भी दिखे।
         */
        if (existing != null
                && existing.getCategory() != null
                && !existing.getCategory().trim().isEmpty()
                && !containsIgnoreCase(
                activeCategories,
                existing.getCategory()
        )) {
            activeCategories.add(
                    0,
                    existing.getCategory()
            );
        }

        View view = LayoutInflater
                .from(this)
                .inflate(
                        R.layout.dialog_product,
                        null,
                        false
                );

        TextInputLayout categoryLayout =
                view.findViewById(
                        R.id.layout_product_category
                );

        MaterialAutoCompleteTextView categoryInput =
                view.findViewById(
                        R.id.input_product_category
                );

        MaterialButton manageCategoriesButton =
                view.findViewById(
                        R.id.button_manage_categories_from_product
                );

        TextInputEditText nameInput =
                view.findViewById(
                        R.id.input_product_name
                );

        TextInputEditText vpInput =
                view.findViewById(
                        R.id.input_product_vp
                );

        TextInputEditText fullPriceInput =
                view.findViewById(
                        R.id.input_product_full
                );

        TextInputEditText price15Input =
                view.findViewById(
                        R.id.input_product_15
                );

        TextInputEditText price25Input =
                view.findViewById(
                        R.id.input_product_25
                );

        TextInputEditText price35Input =
                view.findViewById(
                        R.id.input_product_35
                );

        TextInputEditText price42Input =
                view.findViewById(
                        R.id.input_product_42
                );

        TextInputEditText price50Input =
                view.findViewById(
                        R.id.input_product_50
                );

        MaterialCheckBox activeCheckBox =
                view.findViewById(
                        R.id.check_product_active
                );

        ArrayAdapter<String> categoryAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        activeCategories
                );

        categoryInput.setAdapter(
                categoryAdapter
        );

        categoryInput.setThreshold(0);

        categoryInput.setOnClickListener(
                clickedView ->
                        categoryInput.showDropDown()
        );

        categoryInput.setOnFocusChangeListener(
                (focusedView, hasFocus) -> {
                    if (hasFocus) {
                        categoryInput.showDropDown();
                    }
                }
        );

        categoryInput.setOnItemClickListener(
                (parent, selectedView, position, id) -> {
                    categoryLayout.setError(null);

                    String selectedCategory =
                            parent.getItemAtPosition(position)
                                    .toString();

                    categoryInput.setText(
                            selectedCategory,
                            false
                    );
                }
        );

        if (existing != null) {
            categoryInput.setText(
                    existing.getCategory(),
                    false
            );

            nameInput.setText(
                    existing.getName()
            );

            vpInput.setText(
                    formatDouble(existing.getVp())
            );

            fullPriceInput.setText(
                    String.valueOf(
                            existing.getFullPrice()
                    )
            );

            price15Input.setText(
                    String.valueOf(
                            existing.getPrice15()
                    )
            );

            price25Input.setText(
                    String.valueOf(
                            existing.getPrice25()
                    )
            );

            price35Input.setText(
                    String.valueOf(
                            existing.getPrice35()
                    )
            );

            price42Input.setText(
                    String.valueOf(
                            existing.getPrice42()
                    )
            );

            price50Input.setText(
                    String.valueOf(
                            existing.getPrice50()
                    )
            );

            activeCheckBox.setChecked(
                    existing.isActive()
            );

        } else {
            activeCheckBox.setChecked(true);

            if (!activeCategories.isEmpty()) {
                categoryInput.setText(
                        activeCategories.get(0),
                        false
                );
            }
        }

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(
                                existing == null
                                        ? "Add Product"
                                        : "Edit Product"
                        )
                        .setView(view)
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Save",
                                null
                        )
                        .create();

        manageCategoriesButton.setOnClickListener(
                buttonView -> {
                    dialog.dismiss();
                    openCategoryManager();
                }
        );

        dialog.setOnShowListener(ignored ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(buttonView -> {
                    categoryLayout.setError(null);
                    nameInput.setError(null);

                    String categoryValue =
                            getCategoryText(categoryInput);

                    String productName =
                            getInputText(nameInput);

                    if (categoryValue.isEmpty()) {
                        categoryLayout.setError(
                                "Select a category"
                        );

                        categoryInput.requestFocus();
                        categoryInput.showDropDown();
                        return;
                    }

                    if (!containsIgnoreCase(
                            activeCategories,
                            categoryValue
                    )) {
                        categoryLayout.setError(
                                "Select a category from the list"
                        );

                        categoryInput.requestFocus();
                        categoryInput.showDropDown();
                        return;
                    }

                    if (productName.isEmpty()) {
                        nameInput.setError(
                                "Product name is required"
                        );

                        nameInput.requestFocus();
                        return;
                    }

                    Product product = existing == null
                            ? new Product()
                            : existing;

                    product.setCategory(
                            getExactCategoryName(
                                    activeCategories,
                                    categoryValue
                            )
                    );

                    product.setName(
                            productName
                    );

                    product.setVp(
                            parseDouble(vpInput)
                    );

                    product.setFullPrice(
                            parseInt(fullPriceInput)
                    );

                    product.setPrice15(
                            parseInt(price15Input)
                    );

                    product.setPrice25(
                            parseInt(price25Input)
                    );

                    product.setPrice35(
                            parseInt(price35Input)
                    );

                    product.setPrice42(
                            parseInt(price42Input)
                    );

                    product.setPrice50(
                            parseInt(price50Input)
                    );

                    product.setActive(
                            activeCheckBox.isChecked()
                    );

                    product.setUpdatedAt(
                            System.currentTimeMillis()
                    );

                    try {
                        long result =
                                db.saveProduct(product);

                        if (result <= 0) {
                            Toast.makeText(
                                    this,
                                    "Product save नहीं हो सका",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        Toast.makeText(
                                this,
                                existing == null
                                        ? "Product added"
                                        : "Product updated",
                                Toast.LENGTH_SHORT
                        ).show();

                        dialog.dismiss();
                        refreshProducts();

                    } catch (Exception exception) {
                        Toast.makeText(
                                this,
                                "Could not save: "
                                        + exception.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
        );

        dialog.show();
    }

    private boolean containsIgnoreCase(
            List<String> values,
            String target
    ) {
        if (target == null) {
            return false;
        }

        for (String value : values) {
            if (value != null
                    && value.equalsIgnoreCase(
                    target.trim()
            )) {
                return true;
            }
        }

        return false;
    }

    private String getExactCategoryName(
            List<String> categories,
            String selectedCategory
    ) {
        for (String category : categories) {
            if (category != null
                    && category.equalsIgnoreCase(
                    selectedCategory.trim()
            )) {
                return category;
            }
        }

        return selectedCategory.trim();
    }

    private String getCategoryText(
            MaterialAutoCompleteTextView input
    ) {
        if (input == null
                || input.getText() == null) {
            return "";
        }

        return input.getText()
                .toString()
                .trim();
    }

    private void openCsvPicker() {
        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT
        );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType("text/*");

        startActivityForResult(
                intent,
                REQUEST_IMPORT_CSV
        );
    }

    private void createCatalogFile(boolean pdf) {
        String timeStamp =
                new SimpleDateFormat(
                        "yyyyMMdd_HHmm",
                        Locale.getDefault()
                ).format(new Date());

        Intent intent = new Intent(
                Intent.ACTION_CREATE_DOCUMENT
        );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        if (pdf) {
            intent.setType(
                    "application/pdf"
            );

            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    "Product_Price_List_"
                            + timeStamp
                            + ".pdf"
            );

            startActivityForResult(
                    intent,
                    REQUEST_EXPORT_PDF
            );

        } else {
            intent.setType(
                    "application/vnd.openxmlformats-officedocument"
                            + ".spreadsheetml.sheet"
            );

            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    "Product_Price_List_"
                            + timeStamp
                            + ".xlsx"
            );

            startActivityForResult(
                    intent,
                    REQUEST_EXPORT_XLSX
            );
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        try {
            if (requestCode == REQUEST_IMPORT_CSV) {
                ProductDbHelper.ImportResult result;

                try (
                        java.io.InputStream input =
                                getContentResolver()
                                        .openInputStream(uri)
                ) {
                    if (input == null) {
                        throw new IllegalStateException(
                                "Unable to open CSV file"
                        );
                    }

                    result = db.importCsv(
                            input,
                            true
                    );
                }

                Toast.makeText(
                        this,
                        "CSV imported: "
                                + result.inserted
                                + " added, "
                                + result.updated
                                + " updated, "
                                + result.skipped
                                + " skipped",
                        Toast.LENGTH_LONG
                ).show();

                refreshProducts();

            } else if (
                    requestCode == REQUEST_EXPORT_XLSX
            ) {
                ExportUtils.writeProductsXlsx(
                        getContentResolver(),
                        uri,
                        db.getAllProducts(false)
                );

                Toast.makeText(
                        this,
                        "Excel price list saved",
                        Toast.LENGTH_LONG
                ).show();

            } else if (
                    requestCode == REQUEST_EXPORT_PDF
            ) {
                ExportUtils.writeProductsPdf(
                        getContentResolver(),
                        uri,
                        db.getAllProducts(false)
                );

                Toast.makeText(
                        this,
                        "PDF price list saved",
                        Toast.LENGTH_LONG
                ).show();
            }

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Operation failed: "
                            + exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String getInputText(
            TextInputEditText input
    ) {
        if (input == null
                || input.getText() == null) {
            return "";
        }

        return input.getText()
                .toString()
                .trim();
    }

    private int parseInt(
            TextInputEditText input
    ) {
        try {
            String value = getInputText(input)
                    .replace(",", "")
                    .replace("₹", "")
                    .trim();

            return Math.max(
                    0,
                    (int) Math.round(
                            Double.parseDouble(value)
                    )
            );

        } catch (Exception exception) {
            return 0;
        }
    }

    private double parseDouble(
            TextInputEditText input
    ) {
        try {
            String value = getInputText(input)
                    .replace(",", "")
                    .trim();

            return Math.max(
                    0d,
                    Double.parseDouble(value)
            );

        } catch (Exception exception) {
            return 0d;
        }
    }

    private String formatDouble(double value) {
        if (value == Math.rint(value)) {
            return String.format(
                    Locale.getDefault(),
                    "%.0f",
                    value
            );
        }

        return String.format(
                Locale.getDefault(),
                "%.2f",
                value
        );
    }
}