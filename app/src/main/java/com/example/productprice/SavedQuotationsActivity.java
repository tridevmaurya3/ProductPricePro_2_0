package com.example.productprice;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.adapter.SavedQuotationAdapter;
import com.example.productprice.data.QuotationDbHelper;
import com.example.productprice.model.CartItem;
import com.example.productprice.model.Customer;
import com.example.productprice.model.Product;
import com.example.productprice.model.SavedQuotation;
import com.example.productprice.model.SavedQuotationItem;
import com.example.productprice.util.QuotationShareHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class SavedQuotationsActivity extends AppCompatActivity
        implements SavedQuotationAdapter.OnQuotationActionListener {

    public static final String EXTRA_QUOTATION_ID =
            "extra_quotation_id";

    private static final String FILTER_ALL =
            "All";

    private static final String FILTER_DRAFT =
            SavedQuotation.STATUS_DRAFT;

    private static final String FILTER_FINAL =
            SavedQuotation.STATUS_FINAL;

    private static final String FILTER_SENT =
            SavedQuotation.STATUS_SENT;

    private QuotationDbHelper quotationDb;

    private TextInputEditText searchInput;

    private TextView quotationCountText;
    private TextView listTitleText;
    private TextView listStatusText;
    private TextView emptyTitleText;
    private TextView emptyMessageText;

    private RecyclerView quotationRecycler;
    private LinearLayout emptyLayout;

    private MaterialButton createQuotationButton;

    private Chip filterAllChip;
    private Chip filterDraftChip;
    private Chip filterFinalChip;
    private Chip filterSentChip;

    private final List<SavedQuotation> displayedQuotations =
            new ArrayList<>();

    private SavedQuotationAdapter quotationAdapter;

    private String currentSearchQuery = "";

    private String selectedStatusFilter =
            FILTER_ALL;

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_saved_quotations
        );

        quotationDb =
                QuotationDbHelper.getInstance(this);

        bindViews();
        setupToolbar();
        setupRecyclerView();
        setupSearch();
        setupStatusFilters();
        setupActions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadSavedQuotations();
    }

    private void bindViews() {
        searchInput =
                findViewById(
                        R.id.input_search_quotations
                );

        quotationCountText =
                findViewById(
                        R.id.text_saved_quotation_count
                );

        listTitleText =
                findViewById(
                        R.id.text_saved_list_title
                );

        listStatusText =
                findViewById(
                        R.id.text_saved_list_status
                );

        quotationRecycler =
                findViewById(
                        R.id.recycler_saved_quotations
                );

        emptyLayout =
                findViewById(
                        R.id.layout_saved_quotations_empty
                );

        emptyTitleText =
                findViewById(
                        R.id.text_saved_empty_title
                );

        emptyMessageText =
                findViewById(
                        R.id.text_saved_empty_message
                );

        createQuotationButton =
                findViewById(
                        R.id.button_empty_create_quotation
                );

        filterAllChip =
                findViewById(
                        R.id.chip_filter_all
                );

        filterDraftChip =
                findViewById(
                        R.id.chip_filter_draft
                );

        filterFinalChip =
                findViewById(
                        R.id.chip_filter_final
                );

        filterSentChip =
                findViewById(
                        R.id.chip_filter_sent
                );
    }

    private void setupToolbar() {
        MaterialToolbar toolbar =
                findViewById(
                        R.id.toolbar_saved_quotations
                );

        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(
                view -> finish()
        );
    }

    private void setupRecyclerView() {
        quotationAdapter =
                new SavedQuotationAdapter(
                        displayedQuotations,
                        this
                );

        quotationRecycler.setLayoutManager(
                new LinearLayoutManager(this)
        );

        quotationRecycler.setAdapter(
                quotationAdapter
        );

        quotationRecycler.setHasFixedSize(
                false
        );
    }

    private void setupSearch() {
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
                        currentSearchQuery =
                                text == null
                                        ? ""
                                        : text.toString().trim();

                        loadSavedQuotations();
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

    private void setupStatusFilters() {
        filterAllChip.setOnClickListener(
                view -> selectStatusFilter(
                        FILTER_ALL
                )
        );

        filterDraftChip.setOnClickListener(
                view -> selectStatusFilter(
                        FILTER_DRAFT
                )
        );

        filterFinalChip.setOnClickListener(
                view -> selectStatusFilter(
                        FILTER_FINAL
                )
        );

        filterSentChip.setOnClickListener(
                view -> selectStatusFilter(
                        FILTER_SENT
                )
        );
    }

    private void selectStatusFilter(
            String statusFilter
    ) {
        selectedStatusFilter =
                statusFilter == null
                        ? FILTER_ALL
                        : statusFilter;

        loadSavedQuotations();
    }

    private void setupActions() {
        createQuotationButton.setOnClickListener(
                view -> finish()
        );
    }

    private void loadSavedQuotations() {
        List<SavedQuotation> sourceQuotations;

        try {
            if (currentSearchQuery.isEmpty()) {
                sourceQuotations =
                        quotationDb.getAllQuotations();

            } else {
                sourceQuotations =
                        quotationDb.searchQuotations(
                                currentSearchQuery
                        );
            }

        } catch (Exception exception) {
            sourceQuotations =
                    new ArrayList<>();

            Toast.makeText(
                    this,
                    "Saved quotations could not be loaded",
                    Toast.LENGTH_SHORT
            ).show();
        }

        List<SavedQuotation> filteredQuotations =
                filterQuotationsByStatus(
                        sourceQuotations
                );

        quotationAdapter.replaceItems(
                filteredQuotations
        );

        updateScreenState(
                filteredQuotations.size()
        );
    }

    private List<SavedQuotation> filterQuotationsByStatus(
            List<SavedQuotation> quotations
    ) {
        List<SavedQuotation> filteredList =
                new ArrayList<>();

        if (quotations == null
                || quotations.isEmpty()) {

            return filteredList;
        }

        if (FILTER_ALL.equals(
                selectedStatusFilter
        )) {
            filteredList.addAll(
                    quotations
            );

            return filteredList;
        }

        for (
                SavedQuotation quotation :
                quotations
        ) {
            if (quotation == null) {
                continue;
            }

            String quotationStatus =
                    normalizeQuotationStatus(
                            quotation.getStatus()
                    );

            if (selectedStatusFilter.equals(
                    quotationStatus
            )) {
                filteredList.add(
                        quotation
                );
            }
        }

        return filteredList;
    }

    private String normalizeQuotationStatus(
            String status
    ) {
        if (status != null
                && status.trim().equalsIgnoreCase(
                SavedQuotation.STATUS_FINAL
        )) {
            return SavedQuotation.STATUS_FINAL;
        }

        if (status != null
                && status.trim().equalsIgnoreCase(
                SavedQuotation.STATUS_SENT
        )) {
            return SavedQuotation.STATUS_SENT;
        }

        return SavedQuotation.STATUS_DRAFT;
    }

    private void updateScreenState(
            int quotationCount
    ) {
        boolean empty =
                quotationCount == 0;

        quotationRecycler.setVisibility(
                empty
                        ? View.GONE
                        : View.VISIBLE
        );

        emptyLayout.setVisibility(
                empty
                        ? View.VISIBLE
                        : View.GONE
        );

        quotationCountText.setText(
                String.valueOf(
                        quotationCount
                )
        );

        updateListTitle();
        updateListStatus(
                quotationCount
        );

        if (empty) {
            updateEmptyState();
        }
    }

    private void updateListTitle() {
        if (FILTER_DRAFT.equals(
                selectedStatusFilter
        )) {
            listTitleText.setText(
                    "Draft Quotations"
            );

        } else if (FILTER_FINAL.equals(
                selectedStatusFilter
        )) {
            listTitleText.setText(
                    "Final Quotations"
            );

        } else if (FILTER_SENT.equals(
                selectedStatusFilter
        )) {
            listTitleText.setText(
                    "Sent Quotations"
            );

        } else {
            listTitleText.setText(
                    "All Quotations"
            );
        }
    }

    private void updateListStatus(
            int quotationCount
    ) {
        boolean searching =
                !currentSearchQuery.isEmpty();

        boolean filtering =
                !FILTER_ALL.equals(
                        selectedStatusFilter
                );

        if (searching || filtering) {
            listStatusText.setText(
                    quotationCount == 1
                            ? "1 result"
                            : quotationCount + " results"
            );

        } else {
            listStatusText.setText(
                    "Newest first"
            );
        }
    }

    private void updateEmptyState() {
        boolean searching =
                !currentSearchQuery.isEmpty();

        if (searching) {
            emptyTitleText.setText(
                    "No Matching Quotations"
            );

            emptyMessageText.setText(
                    "No quotation matches your search and selected status filter."
            );

            createQuotationButton.setVisibility(
                    View.GONE
            );

            return;
        }

        createQuotationButton.setVisibility(
                View.VISIBLE
        );

        if (FILTER_DRAFT.equals(
                selectedStatusFilter
        )) {
            emptyTitleText.setText(
                    "No Draft Quotations"
            );

            emptyMessageText.setText(
                    "Quotations saved for later will appear here."
            );

        } else if (FILTER_FINAL.equals(
                selectedStatusFilter
        )) {
            emptyTitleText.setText(
                    "No Final Quotations"
            );

            emptyMessageText.setText(
                    "Mark a draft as Final when it is completed and ready to share."
            );

        } else if (FILTER_SENT.equals(
                selectedStatusFilter
        )) {
            emptyTitleText.setText(
                    "No Sent Quotations"
            );

            emptyMessageText.setText(
                    "Quotations marked as Sent will appear here."
            );

        } else {
            emptyTitleText.setText(
                    "No Saved Quotations"
            );

            emptyMessageText.setText(
                    "Create an order and tap Save for Later. Your quotation will appear here."
            );
        }
    }

    @Override
    public void onOpenQuotation(
            SavedQuotation quotation
    ) {
        if (quotation == null
                || quotation.getId() <= 0) {

            Toast.makeText(
                    this,
                    "Quotation could not be opened",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent resultIntent =
                new Intent();

        resultIntent.putExtra(
                EXTRA_QUOTATION_ID,
                quotation.getId()
        );

        setResult(
                RESULT_OK,
                resultIntent
        );

        finish();
    }

    @Override
    public void onShareQuotation(
            SavedQuotation quotation
    ) {
        if (quotation == null
                || quotation.getId() <= 0) {

            Toast.makeText(
                    this,
                    "Quotation could not be shared",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        shareSavedQuotation(
                quotation
        );
    }

    @Override
    public void onChangeQuotationStatus(
            SavedQuotation quotation,
            String newStatus
    ) {
        if (quotation == null
                || quotation.getId() <= 0) {

            Toast.makeText(
                    this,
                    "Quotation status could not be changed",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String validNewStatus =
                normalizeNewStatus(
                        newStatus
                );

        String quotationTitle =
                safeText(
                        quotation.getTitle()
                );

        if (quotationTitle.isEmpty()) {
            quotationTitle =
                    "Saved Quotation";
        }

        String dialogTitle;
        String dialogMessage;
        String positiveButton;

        if (SavedQuotation.STATUS_SENT.equals(
                validNewStatus
        )) {
            dialogTitle =
                    "Mark quotation as Sent?";

            dialogMessage =
                    "\""
                            + quotationTitle
                            + "\" will be marked as sent to the customer.";

            positiveButton =
                    "Mark Sent";

        } else {
            dialogTitle =
                    "Mark quotation as Final?";

            dialogMessage =
                    "\""
                            + quotationTitle
                            + "\" will be marked as completed and ready to share.";

            positiveButton =
                    "Mark Final";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        dialogTitle
                )
                .setMessage(
                        dialogMessage
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        positiveButton,
                        (dialog, which) ->
                                updateQuotationStatus(
                                        quotation.getId(),
                                        validNewStatus
                                )
                )
                .show();
    }

    private String normalizeNewStatus(
            String status
    ) {
        if (status != null
                && status.trim().equalsIgnoreCase(
                SavedQuotation.STATUS_SENT
        )) {
            return SavedQuotation.STATUS_SENT;
        }

        return SavedQuotation.STATUS_FINAL;
    }

    private void updateQuotationStatus(
            long quotationId,
            String newStatus
    ) {
        try {
            boolean updated =
                    quotationDb.updateQuotationStatus(
                            quotationId,
                            newStatus
                    );

            if (!updated) {
                Toast.makeText(
                        this,
                        "Quotation status could not be updated",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            String message =
                    SavedQuotation.STATUS_SENT.equals(
                            newStatus
                    )
                            ? "Quotation marked as Sent"
                            : "Quotation marked as Final";

            Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_SHORT
            ).show();

            loadSavedQuotations();

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Quotation status could not be updated",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void shareSavedQuotation(
            SavedQuotation quotation
    ) {
        try {
            List<SavedQuotationItem> savedItems =
                    quotationDb.getQuotationItems(
                            quotation.getId()
                    );

            if (savedItems == null
                    || savedItems.isEmpty()) {

                Toast.makeText(
                        this,
                        "This quotation has no saved products",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            List<CartItem> shareItems =
                    new ArrayList<>();

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

                shareItems.add(
                        cartItem
                );
            }

            if (shareItems.isEmpty()) {
                Toast.makeText(
                        this,
                        "No valid products found in this quotation",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            Customer shareCustomer =
                    createCustomerForShare(
                            quotation
                    );

            QuotationShareHelper.createAndSharePdf(
                    this,
                    shareItems,
                    quotation.getOrderType(),
                    shareCustomer
            );

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "PDF share failed: "
                            + getErrorMessage(
                            exception
                    ),
                    Toast.LENGTH_LONG
            ).show();
        }
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

    @Nullable
    private Customer createCustomerForShare(
            SavedQuotation quotation
    ) {
        String orderType =
                quotation.getOrderType();

        boolean customerOrder =
                orderType != null
                        && orderType.equalsIgnoreCase(
                        SavedQuotation.ORDER_TYPE_CUSTOMER
                );

        if (!customerOrder) {
            return null;
        }

        Customer customer =
                new Customer();

        customer.setId(
                quotation.getCustomerId()
        );

        String customerName =
                safeText(
                        quotation.getCustomerName()
                );

        if (customerName.isEmpty()) {
            customerName =
                    "Customer";
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

        customer.setActive(
                true
        );

        return customer;
    }

    @Override
    public void onDeleteQuotation(
            SavedQuotation quotation
    ) {
        if (quotation == null
                || quotation.getId() <= 0) {

            return;
        }

        String quotationTitle =
                safeText(
                        quotation.getTitle()
                );

        if (quotationTitle.isEmpty()) {
            quotationTitle =
                    "this quotation";
        }

        String finalQuotationTitle =
                quotationTitle;

        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        "Delete saved quotation?"
                )
                .setMessage(
                        "\""
                                + finalQuotationTitle
                                + "\" will be permanently removed."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteQuotation(
                                        quotation.getId()
                                )
                )
                .show();
    }

    private void deleteQuotation(
            long quotationId
    ) {
        try {
            boolean deleted =
                    quotationDb.deleteQuotation(
                            quotationId
                    );

            if (!deleted) {
                Toast.makeText(
                        this,
                        "Quotation could not be deleted",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            Toast.makeText(
                    this,
                    "Saved quotation deleted",
                    Toast.LENGTH_SHORT
            ).show();

            loadSavedQuotations();

        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Quotation could not be deleted",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private String getErrorMessage(
            Exception exception
    ) {
        if (exception == null
                || exception.getMessage() == null
                || exception.getMessage()
                .trim()
                .isEmpty()) {

            return "Unknown error";
        }

        return exception.getMessage()
                .trim();
    }
}