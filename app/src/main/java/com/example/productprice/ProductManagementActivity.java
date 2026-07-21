package com.example.productprice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
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
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProductManagementActivity extends AppCompatActivity implements ProductAdapter.ProductActionListener {
    private static final int REQUEST_IMPORT_CSV = 401;
    private static final int REQUEST_EXPORT_XLSX = 402;
    private static final int REQUEST_EXPORT_PDF = 403;

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

        ExtendedFloatingActionButton addButton = findViewById(R.id.fab_add_product);
        addButton.setOnClickListener(v -> showProductDialog(null));
        refreshProducts();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_products);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_product_management);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_import_csv) {
                openCsvPicker();
                return true;
            }
            if (id == R.id.action_export_excel) {
                createCatalogFile(false);
                return true;
            }
            if (id == R.id.action_export_pdf) {
                createCatalogFile(true);
                return true;
            }
            return false;
        });
    }

    private void setupList() {
        RecyclerView recyclerView = findViewById(R.id.recycler_products);
        countText = findViewById(R.id.text_product_count);
        emptyText = findViewById(R.id.text_empty_products);
        adapter = new ProductAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        searchInput = findViewById(R.id.input_search_product);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString();
                refreshProducts();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void refreshProducts() {
        List<Product> products = currentQuery.trim().isEmpty()
                ? db.getAllProducts(false)
                : db.searchProducts(currentQuery);
        adapter.submitList(products);
        countText.setText(products.size() + (products.size() == 1 ? " product" : " products"));
        emptyText.setVisibility(products.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onEdit(Product product) {
        showProductDialog(product);
    }

    @Override
    public void onDelete(Product product) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete product?")
                .setMessage(product.getName() + " will be permanently removed from the app database.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (db.deleteProduct(product.getId())) {
                        Toast.makeText(this, "Product deleted", Toast.LENGTH_SHORT).show();
                        refreshProducts();
                    }
                })
                .show();
    }

    private void showProductDialog(@Nullable Product existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_product, null, false);
        TextInputEditText category = view.findViewById(R.id.input_product_category);
        TextInputEditText name = view.findViewById(R.id.input_product_name);
        TextInputEditText vp = view.findViewById(R.id.input_product_vp);
        TextInputEditText full = view.findViewById(R.id.input_product_full);
        TextInputEditText p15 = view.findViewById(R.id.input_product_15);
        TextInputEditText p25 = view.findViewById(R.id.input_product_25);
        TextInputEditText p35 = view.findViewById(R.id.input_product_35);
        TextInputEditText p42 = view.findViewById(R.id.input_product_42);
        TextInputEditText p50 = view.findViewById(R.id.input_product_50);
        MaterialCheckBox active = view.findViewById(R.id.check_product_active);

        if (existing != null) {
            category.setText(existing.getCategory());
            name.setText(existing.getName());
            vp.setText(String.valueOf(existing.getVp()));
            full.setText(String.valueOf(existing.getFullPrice()));
            p15.setText(String.valueOf(existing.getPrice15()));
            p25.setText(String.valueOf(existing.getPrice25()));
            p35.setText(String.valueOf(existing.getPrice35()));
            p42.setText(String.valueOf(existing.getPrice42()));
            p50.setText(String.valueOf(existing.getPrice50()));
            active.setChecked(existing.isActive());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? "Add Product" : "Edit Product")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String categoryValue = text(category);
            String nameValue = text(name);
            if (categoryValue.isEmpty()) {
                category.setError("Category is required");
                return;
            }
            if (nameValue.isEmpty()) {
                name.setError("Product name is required");
                return;
            }

            Product product = existing == null ? new Product() : existing;
            product.setCategory(categoryValue);
            product.setName(nameValue);
            product.setVp(parseDouble(vp));
            product.setFullPrice(parseInt(full));
            product.setPrice15(parseInt(p15));
            product.setPrice25(parseInt(p25));
            product.setPrice35(parseInt(p35));
            product.setPrice42(parseInt(p42));
            product.setPrice50(parseInt(p50));
            product.setActive(active.isChecked());
            product.setUpdatedAt(System.currentTimeMillis());

            try {
                db.saveProduct(product);
                Toast.makeText(this, existing == null ? "Product added" : "Product updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                refreshProducts();
            } catch (Exception e) {
                Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private void openCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_IMPORT_CSV);
    }

    private void createCatalogFile(boolean pdf) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (pdf) {
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_TITLE, "Product_Price_List_" + stamp + ".pdf");
            startActivityForResult(intent, REQUEST_EXPORT_PDF);
        } else {
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.putExtra(Intent.EXTRA_TITLE, "Product_Price_List_" + stamp + ".xlsx");
            startActivityForResult(intent, REQUEST_EXPORT_XLSX);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_IMPORT_CSV) {
                ProductDbHelper.ImportResult result;
                try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Unable to open CSV file");
                    result = db.importCsv(input, true);
                }
                Toast.makeText(this,
                        "CSV imported: " + result.inserted + " added, " + result.updated + " updated, " + result.skipped + " skipped",
                        Toast.LENGTH_LONG).show();
                refreshProducts();
            } else if (requestCode == REQUEST_EXPORT_XLSX) {
                ExportUtils.writeProductsXlsx(getContentResolver(), uri, db.getAllProducts(false));
                Toast.makeText(this, "Excel price list saved", Toast.LENGTH_LONG).show();
            } else if (requestCode == REQUEST_EXPORT_PDF) {
                ExportUtils.writeProductsPdf(getContentResolver(), uri, db.getAllProducts(false));
                Toast.makeText(this, "PDF price list saved", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Operation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int parseInt(TextInputEditText input) {
        try { return Math.max(0, (int) Math.round(Double.parseDouble(text(input)))); }
        catch (Exception e) { return 0; }
    }

    private double parseDouble(TextInputEditText input) {
        try { return Math.max(0d, Double.parseDouble(text(input))); }
        catch (Exception e) { return 0d; }
    }
}
