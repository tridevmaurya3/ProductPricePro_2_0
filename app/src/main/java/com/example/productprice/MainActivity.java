package com.example.productprice;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.adapter.AttractiveDropdownAdapter;
import com.example.productprice.adapter.CartAdapter;
import com.example.productprice.data.CustomerDbHelper;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.data.QuotationDbHelper;
import com.example.productprice.model.CartItem;
import com.example.productprice.model.Customer;
import com.example.productprice.model.Product;
import com.example.productprice.model.SavedQuotation;
import com.example.productprice.model.SavedQuotationItem;
import com.example.productprice.util.ExportUtils;
import com.example.productprice.util.QuotationShareHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ORDER_XLSX = 301;
    private static final int REQUEST_ORDER_PDF = 302;
    private static final int REQUEST_SAVED_QUOTATION = 303;

    private static final String ORDER_TYPE_SELF = "Self";
    private static final String ORDER_TYPE_CUSTOMER = "Customer";

    private ProductDbHelper productDb;
    private CustomerDbHelper customerDb;
    private QuotationDbHelper quotationDb;

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

    private long currentSavedQuotationId = 0L;
    private String currentSavedQuotationTitle = "";
    private String currentSavedQuotationNotes = "";

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

        quotationDb =
                QuotationDbHelper.getInstance(this);

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
                findViewById(
                        R.id.toolbar
                );

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

                        customerDropdownLayout.setError(
                                null
                        );
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

                        customerDropdownLayout.setError(
                                null
                        );

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

    private int dpToPx(
            int dp
    ) {
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

                                cartAdapter.notifyItemRemoved(
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
                R.id.button_save_for_later
        ).setOnClickListener(
                view -> showSaveQuotationDialog()
        );

        findViewById(
                R.id.button_saved_quotations
        ).setOnClickListener(
                view -> openSavedQuotations()
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

    private void openSavedQuotations() {
        Intent intent =
                new Intent(
                        this,
                        SavedQuotationsActivity.class
                );

        startActivityForResult(
                intent,
                REQUEST_SAVED_QUOTATION
        );
    }

    private void loadSavedQuotationDraft(
            long quotationId
    ) {
        if (quotationId <= 0) {
            Toast.makeText(
                    this,
                    "Invalid saved quotation",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {
            SavedQuotation quotation =
                    quotationDb.getQuotation(
                            quotationId
                    );

            if (quotation == null) {
                Toast.makeText(
                        this,
                        "Saved quotation was not found",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            List<SavedQuotationItem> savedItems =
                    quotationDb.getQuotationItems(
                            quotationId
                    );

            if (savedItems == null
                    || savedItems.isEmpty()) {

                Toast.makeText(
                        this,
                        "This saved quotation has no products",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            currentSavedQuotationId =
                    quotation.getId();

            currentSavedQuotationTitle =
                    quotation.getTitle() == null
                            ? ""
                            : quotation.getTitle();

            currentSavedQuotationNotes =
                    quotation.getNotes() == null
                            ? ""
                            : quotation.getNotes();

            restoreSavedOrderTypeAndCustomer(
                    quotation
            );

            cartItems.clear();

            for (
                    SavedQuotationItem savedItem :
                    savedItems
            ) {
                if (savedItem == null
                        || !savedItem.isValid()) {

                    continue;
                }

                Product product =
                        createProductFromSavedItem(
                                savedItem
                        );

                CartItem cartItem =
                        new CartItem(
                                product,
                                savedItem.getQuantity(),
                                savedItem.getSelectedTier(),
                                savedItem.getEffectiveUnitPrice()
                        );

                cartItems.add(
                        cartItem
                );
            }

            cartAdapter.notifyDataSetChanged();

            if (!cartItems.isEmpty()) {
                CartItem firstItem =
                        cartItems.get(0);

                discountDropdown.setText(
                        firstItem.getTier(),
                        false
                );

                quantityDropdown.setText(
                        String.valueOf(
                                firstItem.getQuantity()
                        ),
                        false
                );
            }

            updateCustomerSelectionVisibility();
            updateOrderSummary();

            String title =
                    currentSavedQuotationTitle.trim();

            if (title.isEmpty()) {
                title = "Saved quotation";
            }

            Toast.makeText(
                    this,
                    title + " loaded",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Quotation could not be loaded: "
                            + exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void restoreSavedOrderTypeAndCustomer(
            SavedQuotation quotation
    ) {
        String savedOrderType =
                quotation.getOrderType();

        boolean customerOrder =
                savedOrderType != null
                        && savedOrderType.equalsIgnoreCase(
                        SavedQuotation.ORDER_TYPE_CUSTOMER
                );

        selectedOrderType =
                customerOrder
                        ? ORDER_TYPE_CUSTOMER
                        : ORDER_TYPE_SELF;

        orderTypeDropdown.setText(
                selectedOrderType,
                false
        );

        if (!customerOrder) {
            selectedCustomer = null;

            customerDropdown.setText(
                    "",
                    false
            );

            return;
        }

        selectedCustomer =
                findMatchingActiveCustomer(
                        quotation
                );

        if (selectedCustomer == null) {
            selectedCustomer =
                    createCustomerFromSavedQuotation(
                            quotation
                    );
        }

        customerDropdown.setText(
                selectedCustomer.getDisplayName(),
                false
        );

        customerDropdownLayout.setError(
                null
        );
    }

    @Nullable
    private Customer findMatchingActiveCustomer(
            SavedQuotation quotation
    ) {
        long savedCustomerId =
                quotation.getCustomerId();

        String savedMobile =
                cleanText(
                        quotation.getCustomerMobile()
                );

        String savedName =
                cleanText(
                        quotation.getCustomerName()
                );

        if (savedCustomerId > 0) {
            for (
                    Customer customer :
                    activeCustomers
            ) {
                if (customer.getId()
                        == savedCustomerId) {

                    return customer;
                }
            }
        }

        if (!savedMobile.isEmpty()) {
            for (
                    Customer customer :
                    activeCustomers
            ) {
                if (savedMobile.equalsIgnoreCase(
                        cleanText(
                                customer.getMobile()
                        )
                )) {
                    return customer;
                }
            }
        }

        if (!savedName.isEmpty()) {
            for (
                    Customer customer :
                    activeCustomers
            ) {
                if (savedName.equalsIgnoreCase(
                        cleanText(
                                customer.getName()
                        )
                )) {
                    return customer;
                }
            }
        }

        return null;
    }

    private Customer createCustomerFromSavedQuotation(
            SavedQuotation quotation
    ) {
        Customer customer =
                new Customer();

        customer.setId(
                quotation.getCustomerId()
        );

        String customerName =
                cleanText(
                        quotation.getCustomerName()
                );

        if (customerName.isEmpty()) {
            customerName =
                    "Saved Customer";
        }

        customer.setName(
                customerName
        );

        customer.setMobile(
                quotation.getCustomerMobile()
        );

        customer.setAddress(
                quotation.getCustomerAddress()
        );

        customer.setNotes(
                "Loaded from saved quotation"
        );

        customer.setActive(
                true
        );

        return customer;
    }

    private Product createProductFromSavedItem(
            SavedQuotationItem savedItem
    ) {
        Product product =
                new Product();

        product.setId(
                savedItem.getProductId()
        );

        product.setCategory(
                savedItem.getCategory()
        );

        product.setName(
                savedItem.getProductName()
        );

        product.setVp(
                savedItem.getVolumePoint()
        );

        product.setFullPrice(
                savedItem.getFullPrice()
        );

        product.setPrice15(
                savedItem.getPrice15()
        );

        product.setPrice25(
                savedItem.getPrice25()
        );

        product.setPrice35(
                savedItem.getPrice35()
        );

        product.setPrice42(
                savedItem.getPrice42()
        );

        product.setPrice50(
                savedItem.getPrice50()
        );

        product.setActive(
                true
        );

        product.setUpdatedAt(
                savedItem.getCreatedAt()
        );

        return product;
    }

    private void showSaveQuotationDialog() {
        if (!validateOrderCustomer()) {
            return;
        }

        if (cartItems.isEmpty()) {
            Toast.makeText(
                    this,
                    "Add at least one product before saving",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        View dialogView =
                LayoutInflater.from(this)
                        .inflate(
                                R.layout.dialog_save_quotation,
                                null,
                                false
                        );

        TextView orderForText =
                dialogView.findViewById(
                        R.id.text_save_order_for
                );

        TextView itemSummaryText =
                dialogView.findViewById(
                        R.id.text_save_item_summary
                );

        TextInputLayout titleLayout =
                dialogView.findViewById(
                        R.id.layout_quotation_title
                );

        TextInputEditText titleInput =
                dialogView.findViewById(
                        R.id.input_quotation_title
                );

        TextInputEditText notesInput =
                dialogView.findViewById(
                        R.id.input_quotation_notes
                );

        MaterialButton cancelButton =
                dialogView.findViewById(
                        R.id.button_cancel_save_quotation
                );

        MaterialButton saveButton =
                dialogView.findViewById(
                        R.id.button_confirm_save_quotation
                );

        OrderTotals totals =
                calculateOrderTotals();

        String orderForName =
                getCurrentOrderForName();

        orderForText.setText(
                "Order For: "
                        + orderForName
        );

        String productWord =
                totals.totalQuantity == 1
                        ? "product"
                        : "products";

        itemSummaryText.setText(
                totals.totalQuantity
                        + " "
                        + productWord
                        + " • "
                        + String.format(
                        Locale.getDefault(),
                        "%.2f VP",
                        totals.totalVp
                )
                        + " • "
                        + formatRupees(
                        totals.grandTotal
                )
        );

        String defaultTitle;

        if (currentSavedQuotationId > 0
                && !currentSavedQuotationTitle
                .trim()
                .isEmpty()) {

            defaultTitle =
                    currentSavedQuotationTitle;

        } else {
            defaultTitle =
                    buildDefaultQuotationTitle();
        }

        titleInput.setText(
                defaultTitle
        );

        titleInput.setSelection(
                defaultTitle.length()
        );

        notesInput.setText(
                currentSavedQuotationNotes
        );

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setView(dialogView)
                        .create();

        cancelButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        saveButton.setOnClickListener(
                view -> {
                    titleLayout.setError(null);

                    String quotationTitle =
                            getInputText(
                                    titleInput
                            );

                    String quotationNotes =
                            getInputText(
                                    notesInput
                            );

                    if (quotationTitle.isEmpty()) {
                        titleLayout.setError(
                                "Enter quotation name"
                        );

                        titleInput.requestFocus();
                        return;
                    }

                    boolean updatingExisting =
                            currentSavedQuotationId > 0;

                    long quotationId =
                            saveCurrentQuotation(
                                    quotationTitle,
                                    quotationNotes
                            );

                    if (quotationId > 0) {
                        dialog.dismiss();

                        Toast.makeText(
                                this,
                                updatingExisting
                                        ? "Quotation draft updated"
                                        : "Quotation draft created",
                                Toast.LENGTH_LONG
                        ).show();

                    } else {
                        Toast.makeText(
                                this,
                                "Quotation could not be saved",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        dialog.show();
    }

    private long saveCurrentQuotation(
            String quotationTitle,
            String quotationNotes
    ) {
        SavedQuotation quotation =
                new SavedQuotation(
                        quotationTitle,
                        selectedOrderType
                );

        if (currentSavedQuotationId > 0) {
            quotation.setId(
                    currentSavedQuotationId
            );
        }

        if (ORDER_TYPE_CUSTOMER.equals(
                selectedOrderType
        )) {
            quotation.setCustomer(
                    selectedCustomer
            );

        } else {
            quotation.setOrderType(
                    SavedQuotation.ORDER_TYPE_SELF
            );
        }

        quotation.setStatus(
                SavedQuotation.STATUS_DRAFT
        );

        quotation.setNotes(
                quotationNotes
        );

        List<SavedQuotationItem> savedItems =
                new ArrayList<>();

        for (
                int index = 0;
                index < cartItems.size();
                index++
        ) {
            CartItem cartItem =
                    cartItems.get(index);

            SavedQuotationItem savedItem =
                    SavedQuotationItem.fromCartItem(
                            currentSavedQuotationId,
                            cartItem,
                            index
                    );

            savedItems.add(
                    savedItem
            );
        }

        long savedId =
                quotationDb.saveQuotation(
                        quotation,
                        savedItems
                );

        if (savedId > 0) {
            currentSavedQuotationId =
                    savedId;

            currentSavedQuotationTitle =
                    quotationTitle;

            currentSavedQuotationNotes =
                    quotationNotes;
        }

        return savedId;
    }

    private String buildDefaultQuotationTitle() {
        String date =
                new SimpleDateFormat(
                        "dd MMM yyyy",
                        Locale.getDefault()
                ).format(
                        new Date()
                );

        return getCurrentOrderForName()
                + " Quotation - "
                + date;
    }

    private String getCurrentOrderForName() {
        if (ORDER_TYPE_CUSTOMER.equals(
                selectedOrderType
        )
                && selectedCustomer != null) {

            String customerName =
                    selectedCustomer.getName();

            if (customerName != null
                    && !customerName
                    .trim()
                    .isEmpty()) {

                return customerName.trim();
            }

            return "Customer";
        }

        return "Self";
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

    private String cleanText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
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
        Customer previousSelectedCustomer =
                selectedCustomer;

        long previousCustomerId =
                previousSelectedCustomer == null
                        ? 0
                        : previousSelectedCustomer.getId();

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

        if (selectedCustomer == null
                && previousSelectedCustomer != null
                && currentSavedQuotationId > 0
                && ORDER_TYPE_CUSTOMER.equals(
                selectedOrderType
        )) {
            selectedCustomer =
                    previousSelectedCustomer;
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
            customerDropdownLayout.setError(
                    null
            );
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
                    "0.00 VP"
            );

            selectedFull.setText(
                    formatRupees(0)
            );

            selectedPrice.setText(
                    formatRupees(0)
            );

            selectedPrice15.setText(
                    formatRupees(0)
            );

            selectedPrice25.setText(
                    formatRupees(0)
            );

            selectedPrice35.setText(
                    formatRupees(0)
            );

            selectedPrice42.setText(
                    formatRupees(0)
            );

            selectedPrice50.setText(
                    formatRupees(0)
            );

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
                        "%.2f VP",
                        selectedProduct.getVp()
                )
        );

        selectedFull.setText(
                formatRupees(
                        selectedProduct
                                .getFullPrice()
                )
        );

        selectedPrice.setText(
                formatRupees(
                        selectedProduct
                                .getPriceForTier(
                                        selectedTier
                                )
                )
        );

        selectedPrice15.setText(
                formatRupees(
                        selectedProduct
                                .getPrice15()
                )
        );

        selectedPrice25.setText(
                formatRupees(
                        selectedProduct
                                .getPrice25()
                )
        );

        selectedPrice35.setText(
                formatRupees(
                        selectedProduct
                                .getPrice35()
                )
        );

        selectedPrice42.setText(
                formatRupees(
                        selectedProduct
                                .getPrice42()
                )
        );

        selectedPrice50.setText(
                formatRupees(
                        selectedProduct
                                .getPrice50()
                )
        );
    }

    private String formatRupees(
            int amount
    ) {
        NumberFormat numberFormat =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(0);

        return "₹"
                + numberFormat.format(
                amount
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
        customerDropdownLayout.setError(
                null
        );

        if (ORDER_TYPE_CUSTOMER.equals(
                selectedOrderType
        )) {
            if (selectedCustomer != null) {
                return true;
            }

            if (activeCustomers.isEmpty()) {
                showNoCustomerDialog();
                return false;
            }

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

    private OrderTotals calculateOrderTotals() {
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

        return new OrderTotals(
                totalQuantity,
                totalVolumePoints,
                subtotal,
                logistics,
                finalTotal
        );
    }

    private void updateOrderSummary() {
        OrderTotals totals =
                calculateOrderTotals();

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
                totals.totalQuantity
                        + (totals.totalQuantity == 1
                        ? " item"
                        : " items")
        );

        totalProducts.setText(
                "Products: "
                        + totals.totalQuantity
        );

        totalVp.setText(
                String.format(
                        Locale.getDefault(),
                        "Total VP: %.2f",
                        totals.totalVp
                )
        );

        logisticsText.setText(
                "Logistics: "
                        + formatRupees(
                        totals.logistics
                )
                        + (totals.logistics > 0
                        ? " (below 100 VP)"
                        : "")
        );

        grandTotal.setText(
                "Grand Total: "
                        + formatRupees(
                        totals.grandTotal
                )
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

                            cartAdapter.notifyDataSetChanged();

                            resetCurrentSavedDraft();
                            updateOrderSummary();
                        }
                )
                .show();
    }

    private void resetCurrentSavedDraft() {
        currentSavedQuotationId = 0L;
        currentSavedQuotationTitle = "";
        currentSavedQuotationNotes = "";
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
                ).format(
                        new Date()
                );

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

        if (requestCode
                == REQUEST_SAVED_QUOTATION) {

            if (resultCode != RESULT_OK
                    || data == null) {

                return;
            }

            long quotationId =
                    data.getLongExtra(
                            SavedQuotationsActivity
                                    .EXTRA_QUOTATION_ID,
                            0L
                    );

            if (quotationId <= 0) {
                Toast.makeText(
                        this,
                        "Saved quotation could not be selected",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            loadSavedQuotationDraft(
                    quotationId
            );

            return;
        }

        if (resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {

            return;
        }

        Uri uri =
                data.getData();

        OrderTotals totals =
                calculateOrderTotals();

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
                        totals.totalQuantity,
                        totals.totalVp,
                        totals.logistics,
                        totals.grandTotal,
                        selectedOrderType,
                        exportCustomer
                );

                Toast.makeText(
                        this,
                        "Excel quotation saved",
                        Toast.LENGTH_LONG
                ).show();

            } else if (requestCode
                    == REQUEST_ORDER_PDF) {

                ExportUtils.writeQuotePdf(
                        getContentResolver(),
                        uri,
                        cartItems,
                        totals.totalQuantity,
                        totals.totalVp,
                        totals.logistics,
                        totals.grandTotal,
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

        for (
                String value :
                values
        ) {
            if (value != null
                    && value.equals(target)) {

                return true;
            }
        }

        return false;
    }

    private static class OrderTotals {

        private final int totalQuantity;
        private final double totalVp;
        private final int subtotal;
        private final int logistics;
        private final int grandTotal;

        private OrderTotals(
                int totalQuantity,
                double totalVp,
                int subtotal,
                int logistics,
                int grandTotal
        ) {
            this.totalQuantity =
                    totalQuantity;

            this.totalVp =
                    totalVp;

            this.subtotal =
                    subtotal;

            this.logistics =
                    logistics;

            this.grandTotal =
                    grandTotal;
        }
    }
}