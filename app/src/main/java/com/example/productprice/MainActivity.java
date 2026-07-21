package com.example.productprice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.adapter.CartAdapter;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.CartItem;
import com.example.productprice.model.Product;
import com.example.productprice.util.ExportUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ORDER_XLSX = 301;
    private static final int REQUEST_ORDER_PDF = 302;

    private ProductDbHelper db;
    private AutoCompleteTextView categoryDropdown;
    private AutoCompleteTextView productDropdown;
    private AutoCompleteTextView discountDropdown;
    private AutoCompleteTextView quantityDropdown;
    private TextView selectedVp;
    private TextView selectedFull;
    private TextView selectedPrice;
    private TextView statProducts;
    private TextView statCategories;
    private TextView statUpdated;
    private TextView itemCount;
    private TextView emptyOrder;
    private TextView totalProducts;
    private TextView totalVp;
    private TextView logisticsText;
    private TextView grandTotal;
    private RecyclerView orderRecycler;

    private final List<Product> currentProducts = new ArrayList<>();
    private final List<CartItem> cartItems = new ArrayList<>();
    private CartAdapter cartAdapter;
    private Product selectedProduct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = ProductDbHelper.getInstance(this);
        db.initialize();
        bindViews();
        setupStaticDropdowns();
        setupOrderList();
        setupActions();
        updateOrderSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboardAndProducts();
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        categoryDropdown = findViewById(R.id.dropdown_category);
        productDropdown = findViewById(R.id.dropdown_product);
        discountDropdown = findViewById(R.id.dropdown_discount);
        quantityDropdown = findViewById(R.id.dropdown_quantity);
        selectedVp = findViewById(R.id.text_selected_vp);
        selectedFull = findViewById(R.id.text_selected_full);
        selectedPrice = findViewById(R.id.text_selected_price);
        statProducts = findViewById(R.id.text_stat_products);
        statCategories = findViewById(R.id.text_stat_categories);
        statUpdated = findViewById(R.id.text_stat_updated);
        itemCount = findViewById(R.id.text_item_count);
        emptyOrder = findViewById(R.id.text_empty_order);
        totalProducts = findViewById(R.id.text_total_products);
        totalVp = findViewById(R.id.text_total_vp);
        logisticsText = findViewById(R.id.text_logistics);
        grandTotal = findViewById(R.id.text_grand_total);
        orderRecycler = findViewById(R.id.recycler_order);
    }

    private void setupStaticDropdowns() {
        String[] tiers = {"Full Price", "Price@15", "Price@25", "Price@35", "Price@42", "Price@50"};
        discountDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, tiers));
        discountDropdown.setText("Price@25", false);
        discountDropdown.setOnItemClickListener((parent, view, position, id) -> updateSelectedProductCard());

        List<String> quantities = new ArrayList<>();
        for (int i = 1; i <= 20; i++) quantities.add(String.valueOf(i));
        quantityDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, quantities));
        quantityDropdown.setText("1", false);

        categoryDropdown.setOnItemClickListener((parent, view, position, id) ->
                loadProductsForCategory(String.valueOf(parent.getItemAtPosition(position))));

        productDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = String.valueOf(parent.getItemAtPosition(position));
            selectedProduct = null;
            for (Product product : currentProducts) {
                if (product.getName().equals(selectedName)) {
                    selectedProduct = product;
                    break;
                }
            }
            updateSelectedProductCard();
        });
    }

    private void setupOrderList() {
        cartAdapter = new CartAdapter(cartItems, position -> {
            if (position >= 0 && position < cartItems.size()) {
                cartItems.remove(position);
                cartAdapter.notifyItemRemoved(position);
                updateOrderSummary();
            }
        });
        orderRecycler.setLayoutManager(new LinearLayoutManager(this));
        orderRecycler.setAdapter(cartAdapter);
    }

    private void setupActions() {
        findViewById(R.id.button_manage_products).setOnClickListener(v ->
                startActivity(new Intent(this, ProductManagementActivity.class)));
        findViewById(R.id.button_update_prices).setOnClickListener(v ->
                startActivity(new Intent(this, PriceUpdateActivity.class)));
        findViewById(R.id.button_add_to_order).setOnClickListener(v -> addSelectedProduct());
        findViewById(R.id.button_clear_order).setOnClickListener(v -> confirmClearOrder());
        findViewById(R.id.button_export_order_excel).setOnClickListener(v -> createOrderExport(false));
        findViewById(R.id.button_export_order_pdf).setOnClickListener(v -> createOrderExport(true));
    }

    private void refreshDashboardAndProducts() {
        List<Product> allProducts = db.getAllProducts(true);
        List<String> categories = db.getCategories();
        statProducts.setText(String.valueOf(allProducts.size()));
        statCategories.setText(String.valueOf(categories.size()));

        long updatedAt = db.getLastUpdated();
        statUpdated.setText(updatedAt == 0 ? "—" : new SimpleDateFormat("dd MMM", Locale.getDefault()).format(new Date(updatedAt)));

        categoryDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categories));
        if (!categories.isEmpty()) {
            String currentCategory = categoryDropdown.getText().toString();
            String category = categories.contains(currentCategory) ? currentCategory : categories.get(0);
            categoryDropdown.setText(category, false);
            loadProductsForCategory(category);
        } else {
            currentProducts.clear();
            productDropdown.setText("", false);
            selectedProduct = null;
            updateSelectedProductCard();
        }
    }

    private void loadProductsForCategory(String category) {
        currentProducts.clear();
        currentProducts.addAll(db.getProductsByCategory(category, true));
        List<String> names = new ArrayList<>();
        for (Product product : currentProducts) names.add(product.getName());
        productDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names));

        if (!currentProducts.isEmpty()) {
            String currentName = productDropdown.getText().toString();
            selectedProduct = null;
            for (Product product : currentProducts) {
                if (product.getName().equals(currentName)) {
                    selectedProduct = product;
                    break;
                }
            }
            if (selectedProduct == null) selectedProduct = currentProducts.get(0);
            productDropdown.setText(selectedProduct.getName(), false);
        } else {
            selectedProduct = null;
            productDropdown.setText("", false);
        }
        updateSelectedProductCard();
    }

    private void updateSelectedProductCard() {
        if (selectedProduct == null) {
            selectedVp.setText("VP: 0.00");
            selectedFull.setText("Full: ₹0");
            selectedPrice.setText("Price: ₹0");
            return;
        }
        String tier = discountDropdown.getText().toString().trim();
        if (tier.isEmpty()) tier = "Full Price";
        selectedVp.setText(String.format(Locale.getDefault(), "VP: %.2f", selectedProduct.getVp()));
        selectedFull.setText("Full: ₹" + selectedProduct.getFullPrice());
        selectedPrice.setText("Price: ₹" + selectedProduct.getPriceForTier(tier));
    }

    private void addSelectedProduct() {
        if (selectedProduct == null) {
            Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show();
            return;
        }
        int quantity = parseQuantity(quantityDropdown.getText().toString());
        String tier = discountDropdown.getText().toString().trim();
        if (tier.isEmpty()) tier = "Full Price";

        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            if (item.getProduct().getId() == selectedProduct.getId() && item.getTier().equals(tier)) {
                item.setQuantity(item.getQuantity() + quantity);
                cartAdapter.notifyItemChanged(i);
                updateOrderSummary();
                Toast.makeText(this, "Quantity updated", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        cartItems.add(new CartItem(selectedProduct, quantity, tier));
        cartAdapter.notifyItemInserted(cartItems.size() - 1);
        updateOrderSummary();
        Toast.makeText(this, "Product added", Toast.LENGTH_SHORT).show();
    }

    private int parseQuantity(String value) {
        try { return Math.max(1, Integer.parseInt(value.trim())); }
        catch (Exception e) { return 1; }
    }

    private void updateOrderSummary() {
        int quantity = 0;
        double vp = 0d;
        int subtotal = 0;
        for (CartItem item : cartItems) {
            quantity += item.getQuantity();
            vp += item.getTotalVp();
            subtotal += item.getTotalPrice();
        }
        int logistics = vp > 0 && vp < 100 ? 118 : 0;
        int total = subtotal + logistics;

        boolean empty = cartItems.isEmpty();
        emptyOrder.setVisibility(empty ? View.VISIBLE : View.GONE);
        orderRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        itemCount.setText(quantity + (quantity == 1 ? " item" : " items"));
        totalProducts.setText("Products: " + quantity);
        totalVp.setText(String.format(Locale.getDefault(), "Total VP: %.2f", vp));
        logisticsText.setText("Logistics: ₹" + logistics + (logistics > 0 ? " (below 100 VP)" : ""));
        grandTotal.setText("Grand Total: ₹" + total);
    }

    private void confirmClearOrder() {
        if (cartItems.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear current order?")
                .setMessage("All added products will be removed from this quotation.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    cartItems.clear();
                    cartAdapter.notifyDataSetChanged();
                    updateOrderSummary();
                })
                .show();
    }

    private void createOrderExport(boolean pdf) {
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Add at least one product before exporting", Toast.LENGTH_SHORT).show();
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (pdf) {
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_TITLE, "Product_Quotation_" + stamp + ".pdf");
            startActivityForResult(intent, REQUEST_ORDER_PDF);
        } else {
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.putExtra(Intent.EXTRA_TITLE, "Product_Quotation_" + stamp + ".xlsx");
            startActivityForResult(intent, REQUEST_ORDER_XLSX);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        int totalQty = 0;
        double vp = 0d;
        int subtotal = 0;
        for (CartItem item : cartItems) {
            totalQty += item.getQuantity();
            vp += item.getTotalVp();
            subtotal += item.getTotalPrice();
        }
        int logistics = vp > 0 && vp < 100 ? 118 : 0;
        int finalTotal = subtotal + logistics;

        try {
            if (requestCode == REQUEST_ORDER_XLSX) {
                ExportUtils.writeQuoteXlsx(getContentResolver(), uri, cartItems, totalQty, vp, logistics, finalTotal);
                Toast.makeText(this, "Excel quotation saved", Toast.LENGTH_LONG).show();
            } else if (requestCode == REQUEST_ORDER_PDF) {
                ExportUtils.writeQuotePdf(getContentResolver(), uri, cartItems, totalQty, vp, logistics, finalTotal);
                Toast.makeText(this, "PDF quotation saved", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
