package com.example.productprice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.adapter.AttractiveDropdownAdapter;
import com.example.productprice.adapter.CartAdapter;
import com.example.productprice.data.CustomerDbHelper;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.CartItem;
import com.example.productprice.model.Customer;
import com.example.productprice.model.Product;
import com.example.productprice.util.ExportUtils;
import com.example.productprice.util.QuotationShareHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ORDER_XLSX = 301;
    private static final int REQUEST_ORDER_PDF = 302;

    private static final String ORDER_TYPE_SELF = "Self";
    private static final String ORDER_TYPE_CUSTOMER = "Customer";

    private ProductDbHelper productDb;
    private CustomerDbHelper customerDb;

    private MaterialAutoCompleteTextView orderTypeDropdown;
    private MaterialAutoCompleteTextView customerDropdown;
    private MaterialAutoCompleteTextView categoryDropdown;
    private MaterialAutoCompleteTextView productDropdown;
    private MaterialAutoCompleteTextView discountDropdown;
    private MaterialAutoCompleteTextView quantityDropdown;

    private TextInputLayout customerDropdownLayout;
    private LinearLayout customerSelectionLayout;

    private TextView selectedVp;
    private TextView selectedFull;
    private TextView selectedPrice;
    private TextView selectedPrice15;
    private TextView selectedPrice25;
    private TextView selectedPrice35;
    private TextView selectedPrice42;
    private TextView selectedPrice50;

    private TextView statProducts;
    private TextView statCategories;
    private TextView statUpdated;

    private TextView itemCount;
    private TextView emptyOrder;
    private TextView orderCustomerText;
    private TextView totalProducts;
    private TextView totalVp;
    private TextView logisticsText;
    private TextView grandTotal;

    private RecyclerView orderRecycler;

    private final List<Product> currentProducts =
            new ArrayList<>();

    private final List<CartItem> cartItems =
            new ArrayList<>();

    private final List<Customer> activeCustomers =
            new ArrayList<>();

    private CartAdapter cartAdapter;
    private Product selectedProduct;
    private Customer selectedCustomer;

    private String selectedOrderType =
            ORDER_TYPE_SELF;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_main
        );

        productDb =
                ProductDbHelper.getInstance(this);

        productDb.initialize();

        customerDb =
                CustomerDbHelper.getInstance(this);

        bindViews();
        setupStaticDropdowns();
        setupOrderList();
        setupActions();

        updateCustomerSelectionVisibility();
        updateSelectedProductCard();
        updateOrderSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshDashboardAndProducts();
        refreshCustomers();
    }

    private void bindViews() {
        MaterialToolbar toolbar =
                findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        orderTypeDropdown =
                findViewById(
                        R.id.dropdown_order_type
                );

        customerDropdown =
                findViewById(
                        R.id.dropdown_customer
                );

        categoryDropdown =
                findViewById(
                        R.id.dropdown_category
                );

        productDropdown =
                findViewById(
                        R.id.dropdown_product
                );

        discountDropdown =
                findViewById(
                        R.id.dropdown_discount
                );

        quantityDropdown =
                findViewById(
                        R.id.dropdown_quantity
                );

        customerSelectionLayout =
                findViewById(
                        R.id.layout_customer_selection
                );

        customerDropdownLayout =
                findViewById(
                        R.id.layout_customer_dropdown
                );

        selectedVp =
                findViewById(
                        R.id.text_selected_vp
                );

        selectedFull =
                findViewById(
                        R.id.text_selected_full
                );

        selectedPrice =
                findViewById(
                        R.id.text_selected_price
                );

        selectedPrice15 =
                findViewById(
                        R.id.text_selected_price15
                );

        selectedPrice25 =
                findViewById(
                        R.id.text_selected_price25
                );

        selectedPrice35 =
                findViewById(
                        R.id.text_selected_price35
                );

        selectedPrice42 =
                findViewById(
                        R.id.text_selected_price42
                );

        selectedPrice50 =
                findViewById(
                        R.id.text_selected_price50
                );

        statProducts =
                findViewById(
                        R.id.text_stat_products
                );

        statCategories =
                findViewById(
                        R.id.text_stat_categories
                );

        statUpdated =
                findViewById(
                        R.id.text_stat_updated
                );

        itemCount =
                findViewById(
                        R.id.text_item_count
                );

        emptyOrder =
                findViewById(
                        R.id.text_empty_order
                );

        orderCustomerText =
                findViewById(
                        R.id.text_order_customer
                );

        totalProducts =
                findViewById(
                        R.id.text_total_products
                );

        totalVp =
                findViewById(
                        R.id.text_total_vp
                );

        logisticsText =
                findViewById(
                        R.id.text_logistics
                );

        grandTotal =
                findViewById(
                        R.id.text_grand_total
                );

        orderRecycler =
                findViewById(
                        R.id.recycler_order
                );
    }

    private void setupStaticDropdowns() {
        setupOrderTypeDropdown();
        setupCustomerDropdown();
        setupDiscountDropdown();
        setupQuantityDropdown();
        setupCategoryAndProductDropdowns();
    }

    private void setupOrderTypeDropdown() {
        String[] orderTypes = {
                ORDER_TYPE_SELF,
                ORDER_TYPE_CUSTOMER
        };

        orderTypeDropdown.setAdapter(
                new AttractiveDropdownAdapter(
                        this,
                        orderTypes
                )
        );

        orderTypeDropdown.setText(
                ORDER_TYPE_SELF,
                false
        );

        prepareDropdown(
                orderTypeDropdown
        );

        orderTypeDropdown.setOnItemClickListener(
                (parent, view, position, id) -> {
                    selectedOrderType =
                            String.valueOf(
                                    parent.getItemAtPosition(
                                            position
                                    )
                            );

                    if (ORDER_TYPE_SELF.equals(
                            selectedOrderType
                    )) {
                        selectedCustomer = null;

                        customerDropdown.setText(
                                "",
                                false
                        );

                        customerDropdownLayout
                                .setError(null);
                    }

                    updateCustomerSelectionVisibility();
                    updateOrderSummary();
                }
        );
    }

    private void setupCustomerDropdown() {
        prepareDropdown(
                customerDropdown
        );

        customerDropdown.setOnClickListener(
                view -> {
                    if (activeCustomers.isEmpty()) {
                        showNoCustomerDialog();

                    } else {
                        customerDropdown.showDropDown();
                    }
                }
        );

        customerDropdown.setOnFocusChangeListener(
                (view, hasFocus) -> {
                    if (hasFocus
                            && !activeCustomers.isEmpty()) {
                        customerDropdown.showDropDown();
                    }
                }
        );

        customerDropdown.setOnItemClickListener(
                (parent, view, position, id) -> {
                    if (position >= 0
                            && position
                            < activeCustomers.size()) {

                        selectedCustomer =
                                activeCustomers.get(
                                        position
                                );

                        customerDropdown.setText(
                                selectedCustomer
                                        .getDisplayName(),
                                false
                        );

                        customerDropdownLayout
                                .setError(null);

                        updateOrderSummary();
                    }
                }
        );
    }

    private void setupDiscountDropdown() {
        String[] priceTiers = {
                "Full Price",
                "Price@15",
                "Price@25",
                "Price@35",
                "Price@42",
                "Price@50"
        };

        discountDropdown.setAdapter(
                new AttractiveDropdownAdapter(
                        this,
                        priceTiers
                )
        );

        discountDropdown.setText(
                "Price@25",
                false
        );

        prepareDropdown(
                discountDropdown
        );

        discountDropdown.setOnItemClickListener(
                (parent, view, position, id) ->
                        updateSelectedProductCard()
        );
    }

    private void setupQuantityDropdown() {
        List<String> quantities =
                new ArrayList<>();

        for (
                int quantity = 1;
                quantity <= 20;
                quantity++
        ) {
            quantities.add(
                    String.valueOf(quantity)
            );
        }

        quantityDropdown.setAdapter(
                new AttractiveDropdownAdapter(
                        this,
                        quantities
                )
        );

        quantityDropdown.setText(
                "1",
                false
        );

        prepareDropdown(
                quantityDropdown
        );
    }

    private void setupCategoryAndProductDropdowns() {
        prepareDropdown(
                categoryDropdown
        );

        categoryDropdown.setOnItemClickListener(
                (parent, view, position, id) -> {
                    String categoryName =
                            String.valueOf(
                                    parent.getItemAtPosition(
                                            position
                                    )
                            );

                    loadProductsForCategory(
                            categoryName
                    );
                }
        );

        prepareDropdown(
                productDropdown
        );

        productDropdown.setOnItemClickListener(
                (parent, view, position, id) -> {
                    String productName =
                            String.valueOf(
                                    parent.getItemAtPosition(
                                            position
                                    )
                            );

                    selectedProduct =
                            findProductByName(
                                    productName
                            );

                    updateSelectedProductCard();
                }
        );
    }

    private void prepareDropdown(
            MaterialAutoCompleteTextView dropdown
    ) {
        dropdown.setThreshold(0);

        dropdown.setDropDownBackgroundResource(
                R.drawable.bg_dropdown_popup
        );

        dropdown.setDropDownVerticalOffset(
                dpToPx(4)
        );

        dropdown.setOnClickListener(
                view -> dropdown.showDropDown()
        );
    }

    private int dpToPx(int dp) {
        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }

    private void setupOrderList() {
        cartAdapter =
                new CartAdapter(
                        cartItems,
                        position -> {
                            if (position >= 0
                                    && position
                                    < cartItems.size()) {

                                cartItems.remove(
                                        position
                                );

                                cartAdapter
                                        .notifyItemRemoved(
                                                position
                                        );

                                updateOrderSummary();
                            }
                        }
                );

        orderRecycler.setLayoutManager(
                new LinearLayoutManager(this)
        );

        orderRecycler.setAdapter(
                cartAdapter
        );
    }

    private void setupActions() {
        findViewById(
                R.id.button_manage_products
        ).setOnClickListener(
                view -> startActivity(
                        new Intent(
                                this,
                                ProductManagementActivity.class
                        )
                )
        );

        findViewById(
                R.id.button_update_prices
        ).setOnClickListener(
                view -> startActivity(
                        new Intent(
                                this,
                                PriceUpdateActivity.class
                        )
                )
        );

        findViewById(
                R.id.button_manage_customers
        ).setOnClickListener(
                view -> openCustomerManager()
        );

        findViewById(
                R.id.button_manage_customers_from_order
        ).setOnClickListener(
                view -> openCustomerManager()
        );

        findViewById(
                R.id.button_add_to_order
        ).setOnClickListener(
                view -> addSelectedProduct()
        );

        findViewById(
                R.id.button_clear_order
        ).setOnClickListener(
                view -> confirmClearOrder()
        );

        findViewById(
                R.id.button_export_order_excel
        ).setOnClickListener(
                view -> createOrderExport(false)
        );

        findViewById(
                R.id.button_export_order_pdf
        ).setOnClickListener(
                view -> createOrderExport(true)
        );

        findViewById(
                R.id.button_share_quotation
        ).setOnClickListener(
                view -> createAndShareCurrentPdf()
        );
    }

    private void createAndShareCurrentPdf() {
        if (!validateOrderCustomer()) {
            return;
        }

        if (cartItems.isEmpty()) {
            Toast.makeText(
                    this,
                    "Add at least one product before sharing",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {
            QuotationShareHelper.createAndSharePdf(
                    this,
                    cartItems,
                    selectedOrderType,
                    selectedCustomer
            );

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "PDF share failed: "
                            + exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void openCustomerManager() {
        startActivity(
                new Intent(
                        this,
                        CustomerManagerActivity.class
                )
        );
    }

    private void refreshDashboardAndProducts() {
        List<Product> allProducts =
                productDb.getAllProducts(true);

        List<String> categories =
                productDb.getCategories();

        statProducts.setText(
                String.valueOf(
                        allProducts.size()
                )
        );

        statCategories.setText(
                String.valueOf(
                        categories.size()
                )
        );

        long updatedAt =
                productDb.getLastUpdated();

        if (updatedAt == 0) {
            statUpdated.setText("—");

        } else {
            statUpdated.setText(
                    new SimpleDateFormat(
                            "dd MMM",
                            Locale.getDefault()
                    ).format(
                            new Date(updatedAt)
                    )
            );
        }

        categoryDropdown.setAdapter(
                new AttractiveDropdownAdapter(
                        this,
                        categories
                )
        );

        if (!categories.isEmpty()) {
            String currentCategory =
                    categoryDropdown
                            .getText()
                            .toString()
                            .trim();

            String category =
                    containsExact(
                            categories,
                            currentCategory
                    )
                            ? currentCategory
                            : categories.get(0);

            categoryDropdown.setText(
                    category,
                    false
            );

            loadProductsForCategory(
                    category
            );

        } else {
            currentProducts.clear();

            productDropdown.setText(
                    "",
                    false
            );

            selectedProduct = null;

            updateSelectedProductCard();
        }
    }

    private void refreshCustomers() {
        long previousCustomerId =
                selectedCustomer == null
                        ? 0
                        : selectedCustomer.getId();

        activeCustomers.clear();

        activeCustomers.addAll(
                customerDb.getAllCustomers(true)
        );

        List<String> displayNames =
                new ArrayList<>();

        for (
                Customer customer :
                activeCustomers
        ) {
            displayNames.add(
                    customer.getDisplayName()
            );
        }

        customerDropdown.setAdapter(
                new AttractiveDropdownAdapter(
                        this,
                        displayNames
                )
        );

        selectedCustomer = null;

        if (previousCustomerId > 0) {
            for (
                    Customer customer :
                    activeCustomers
            ) {
                if (customer.getId()
                        == previousCustomerId) {

                    selectedCustomer =
                            customer;

                    break;
                }
            }
        }

        if (selectedCustomer != null) {
            customerDropdown.setText(
                    selectedCustomer
                            .getDisplayName(),
                    false
            );

        } else {
            customerDropdown.setText(
                    "",
                    false
            );
        }

        updateCustomerSelectionVisibility();
        updateOrderSummary();
    }

    private void updateCustomerSelectionVisibility() {
        boolean customerOrder =
                ORDER_TYPE_CUSTOMER.equals(
                        selectedOrderType
                );

        customerSelectionLayout.setVisibility(
                customerOrder
                        ? View.VISIBLE
                        : View.GONE
        );

        if (!customerOrder) {
            customerDropdownLayout
                    .setError(null);
        }
    }

    private void loadProductsForCategory(
            String category
    ) {
        currentProducts.clear();

        currentProducts.addAll(
                productDb.getProductsByCategory(
                        category,
                        true
                )
        );

        List<String> productNames =
                new ArrayList<>();

        for (
                Product product :
                currentProducts
        ) {
            productNames.add(
                    product.getName()
            );
        }

        productDropdown.setAdapter(
                new AttractiveDropdownAdapter(
                        this,
                        productNames
                )
        );

        if (!currentProducts.isEmpty()) {
            String currentName =
                    productDropdown
                            .getText()
                            .toString()
                            .trim();

            selectedProduct =
                    findProductByName(
                            currentName
                    );

            if (selectedProduct == null) {
                selectedProduct =
                        currentProducts.get(0);
            }

            productDropdown.setText(
                    selectedProduct.getName(),
                    false
            );

        } else {
            selectedProduct = null;

            productDropdown.setText(
                    "",
                    false
            );
        }

        updateSelectedProductCard();
    }

    @Nullable
    private Product findProductByName(
            String productName
    ) {
        if (productName == null) {
            return null;
        }

        for (
                Product product :
                currentProducts
        ) {
            if (product.getName() != null
                    && product.getName()
                    .equals(productName)) {

                return product;
            }
        }

        return null;
    }

    private void updateSelectedProductCard() {
        if (selectedProduct == null) {
            selectedVp.setText(
                    "VP: 0.00"
            );

            selectedFull.setText(
                    "Full: ₹0"
            );

            selectedPrice.setText(
                    "Selected: ₹0"
            );

            selectedPrice15.setText("₹0");
            selectedPrice25.setText("₹0");
            selectedPrice35.setText("₹0");
            selectedPrice42.setText("₹0");
            selectedPrice50.setText("₹0");

            return;
        }

        String selectedTier =
                discountDropdown
                        .getText()
                        .toString()
                        .trim();

        if (selectedTier.isEmpty()) {
            selectedTier =
                    "Full Price";
        }

        selectedVp.setText(
                String.format(
                        Locale.getDefault(),
                        "VP: %.2f",
                        selectedProduct.getVp()
                )
        );

        selectedFull.setText(
                "Full: ₹"
                        + selectedProduct
                        .getFullPrice()
        );

        selectedPrice.setText(
                "Selected: ₹"
                        + selectedProduct
                        .getPriceForTier(
                                selectedTier
                        )
        );

        selectedPrice15.setText(
                "₹"
                        + selectedProduct
                        .getPrice15()
        );

        selectedPrice25.setText(
                "₹"
                        + selectedProduct
                        .getPrice25()
        );

        selectedPrice35.setText(
                "₹"
                        + selectedProduct
                        .getPrice35()
        );

        selectedPrice42.setText(
                "₹"
                        + selectedProduct
                        .getPrice42()
        );

        selectedPrice50.setText(
                "₹"
                        + selectedProduct
                        .getPrice50()
        );
    }

    private void addSelectedProduct() {
        if (!validateOrderCustomer()) {
            return;
        }

        if (selectedProduct == null) {
            Toast.makeText(
                    this,
                    "Please select a product",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        int quantity =
                parseQuantity(
                        quantityDropdown
                                .getText()
                                .toString()
                );

        String selectedTier =
                discountDropdown
                        .getText()
                        .toString()
                        .trim();

        if (selectedTier.isEmpty()) {
            selectedTier =
                    "Full Price";
        }

        for (
                int index = 0;
                index < cartItems.size();
                index++
        ) {
            CartItem cartItem =
                    cartItems.get(index);

            if (cartItem
                    .getProduct()
                    .getId()
                    == selectedProduct.getId()
                    && cartItem
                    .getTier()
                    .equals(selectedTier)) {

                cartItem.setQuantity(
                        cartItem.getQuantity()
                                + quantity
                );

                cartAdapter.notifyItemChanged(
                        index
                );

                updateOrderSummary();

                Toast.makeText(
                        this,
                        "Quantity updated",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }
        }

        cartItems.add(
                new CartItem(
                        selectedProduct,
                        quantity,
                        selectedTier
                )
        );

        cartAdapter.notifyItemInserted(
                cartItems.size() - 1
        );

        updateOrderSummary();

        Toast.makeText(
                this,
                "Product added",
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean validateOrderCustomer() {
        customerDropdownLayout
                .setError(null);

        if (ORDER_TYPE_CUSTOMER.equals(
                selectedOrderType
        )) {
            if (activeCustomers.isEmpty()) {
                showNoCustomerDialog();
                return false;
            }

            if (selectedCustomer == null) {
                customerDropdownLayout.setError(
                        "Select a customer"
                );

                customerDropdown.requestFocus();
                customerDropdown.showDropDown();

                Toast.makeText(
                        this,
                        "Please select a customer",
                        Toast.LENGTH_SHORT
                ).show();

                return false;
            }
        }

        return true;
    }

    private void showNoCustomerDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        "No customer available"
                )
                .setMessage(
                        "Customer order बनाने से पहले "
                                + "कम से कम एक active "
                                + "customer जोड़ें."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Manage Customers",
                        (dialog, which) ->
                                openCustomerManager()
                )
                .show();
    }

    private int parseQuantity(
            String value
    ) {
        try {
            return Math.max(
                    1,
                    Integer.parseInt(
                            value.trim()
                    )
            );

        } catch (Exception exception) {
            return 1;
        }
    }

    private void updateOrderSummary() {
        int totalQuantity = 0;
        double totalVolumePoints = 0d;
        int subtotal = 0;

        for (
                CartItem cartItem :
                cartItems
        ) {
            totalQuantity +=
                    cartItem.getQuantity();

            totalVolumePoints +=
                    cartItem.getTotalVp();

            subtotal +=
                    cartItem.getTotalPrice();
        }

        int logistics =
                totalVolumePoints > 0
                        && totalVolumePoints < 100
                        ? 118
                        : 0;

        int finalTotal =
                subtotal + logistics;

        boolean orderEmpty =
                cartItems.isEmpty();

        emptyOrder.setVisibility(
                orderEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        orderRecycler.setVisibility(
                orderEmpty
                        ? View.GONE
                        : View.VISIBLE
        );

        itemCount.setText(
                totalQuantity
                        + (totalQuantity == 1
                        ? " item"
                        : " items")
        );

        totalProducts.setText(
                "Products: "
                        + totalQuantity
        );

        totalVp.setText(
                String.format(
                        Locale.getDefault(),
                        "Total VP: %.2f",
                        totalVolumePoints
                )
        );

        logisticsText.setText(
                "Logistics: ₹"
                        + logistics
                        + (logistics > 0
                        ? " (below 100 VP)"
                        : "")
        );

        grandTotal.setText(
                "Grand Total: ₹"
                        + finalTotal
        );

        if (ORDER_TYPE_CUSTOMER.equals(
                selectedOrderType
        )) {
            if (selectedCustomer != null) {
                orderCustomerText.setText(
                        "Order For: "
                                + selectedCustomer
                                .getName()
                );

            } else {
                orderCustomerText.setText(
                        "Order For: Customer not selected"
                );
            }

        } else {
            orderCustomerText.setText(
                    "Order For: Self"
            );
        }
    }

    private void confirmClearOrder() {
        if (cartItems.isEmpty()) {
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        "Clear current order?"
                )
                .setMessage(
                        "All added products will be "
                                + "removed from this quotation."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Clear",
                        (dialog, which) -> {
                            cartItems.clear();

                            cartAdapter
                                    .notifyDataSetChanged();

                            updateOrderSummary();
                        }
                )
                .show();
    }

    private void createOrderExport(
            boolean pdf
    ) {
        if (!validateOrderCustomer()) {
            return;
        }

        if (cartItems.isEmpty()) {
            Toast.makeText(
                    this,
                    "Add at least one product before exporting",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String timeStamp =
                new SimpleDateFormat(
                        "yyyyMMdd_HHmm",
                        Locale.getDefault()
                ).format(new Date());

        String quotationName =
                getQuotationFileName();

        Intent intent =
                new Intent(
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
                    quotationName
                            + "_"
                            + timeStamp
                            + ".pdf"
            );

            startActivityForResult(
                    intent,
                    REQUEST_ORDER_PDF
            );

        } else {
            intent.setType(
                    "application/vnd.openxmlformats-officedocument"
                            + ".spreadsheetml.sheet"
            );

            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    quotationName
                            + "_"
                            + timeStamp
                            + ".xlsx"
            );

            startActivityForResult(
                    intent,
                    REQUEST_ORDER_XLSX
            );
        }
    }

    private String getQuotationFileName() {
        String name;

        if (ORDER_TYPE_CUSTOMER.equals(
                selectedOrderType
        )
                && selectedCustomer != null) {

            name =
                    selectedCustomer.getName();

        } else {
            name = "Self";
        }

        String safeName =
                name == null
                        ? "Quotation"
                        : name.trim()
                        .replaceAll(
                                "[^a-zA-Z0-9\\-_ ]",
                                ""
                        )
                        .replaceAll(
                                "\\s+",
                                "_"
                        );

        if (safeName.isEmpty()) {
            safeName = "Quotation";
        }

        return "Quotation_"
                + safeName;
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

        Uri uri =
                data.getData();

        int totalQuantity = 0;
        double totalVolumePoints = 0d;
        int subtotal = 0;

        for (
                CartItem cartItem :
                cartItems
        ) {
            totalQuantity +=
                    cartItem.getQuantity();

            totalVolumePoints +=
                    cartItem.getTotalVp();

            subtotal +=
                    cartItem.getTotalPrice();
        }

        int logistics =
                totalVolumePoints > 0
                        && totalVolumePoints < 100
                        ? 118
                        : 0;

        int finalTotal =
                subtotal + logistics;

        Customer exportCustomer =
                ORDER_TYPE_CUSTOMER.equals(
                        selectedOrderType
                )
                        ? selectedCustomer
                        : null;

        try {
            if (requestCode
                    == REQUEST_ORDER_XLSX) {

                ExportUtils.writeQuoteXlsx(
                        getContentResolver(),
                        uri,
                        cartItems,
                        totalQuantity,
                        totalVolumePoints,
                        logistics,
                        finalTotal,
                        selectedOrderType,
                        exportCustomer
                );

                Toast.makeText(
                        this,
                        "Excel quotation saved",
                        Toast.LENGTH_LONG
                ).show();

            } else if (
                    requestCode
                            == REQUEST_ORDER_PDF
            ) {
                ExportUtils.writeQuotePdf(
                        getContentResolver(),
                        uri,
                        cartItems,
                        totalQuantity,
                        totalVolumePoints,
                        logistics,
                        finalTotal,
                        selectedOrderType,
                        exportCustomer
                );

                Toast.makeText(
                        this,
                        "PDF quotation saved",
                        Toast.LENGTH_LONG
                ).show();
            }

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Export failed: "
                            + exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private boolean containsExact(
            List<String> values,
            String target
    ) {
        if (target == null) {
            return false;
        }

        for (String value : values) {
            if (value != null
                    && value.equals(target)) {

                return true;
            }
        }

        return false;
    }
}