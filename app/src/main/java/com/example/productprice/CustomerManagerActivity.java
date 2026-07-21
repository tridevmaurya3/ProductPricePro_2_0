package com.example.productprice;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.adapter.CustomerAdapter;
import com.example.productprice.data.CustomerDbHelper;
import com.example.productprice.model.Customer;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public class CustomerManagerActivity extends AppCompatActivity
        implements CustomerAdapter.CustomerActionListener {

    private MaterialToolbar toolbar;

    private RecyclerView recyclerCustomers;

    private View emptyState;

    private TextView textCustomerCount;

    private TextInputEditText editSearchCustomer;

    private MaterialButton buttonAddCustomerTop;
    private MaterialButton buttonAddFirstCustomer;

    private ExtendedFloatingActionButton fabAddCustomer;

    private CustomerDbHelper customerDbHelper;
    private CustomerAdapter customerAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_manager);

        initializeViews();
        setupToolbar();
        setupDatabase();
        setupRecyclerView();
        setupSearch();
        setupButtons();

        loadCustomers();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (customerDbHelper != null
                && customerAdapter != null) {
            loadCustomers();
        }
    }

    private void initializeViews() {
        toolbar = findViewById(
                R.id.customer_manager_toolbar
        );

        recyclerCustomers = findViewById(
                R.id.recycler_customers
        );

        emptyState = findViewById(
                R.id.customer_empty_state
        );

        textCustomerCount = findViewById(
                R.id.text_customer_count
        );

        editSearchCustomer = findViewById(
                R.id.edit_search_customer
        );

        buttonAddCustomerTop = findViewById(
                R.id.button_add_customer_top
        );

        buttonAddFirstCustomer = findViewById(
                R.id.button_add_first_customer
        );

        fabAddCustomer = findViewById(
                R.id.fab_add_customer
        );
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(
                view -> finish()
        );

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(
                    "Customer Manager"
            );
        }
    }

    private void setupDatabase() {
        customerDbHelper = CustomerDbHelper.getInstance(
                this
        );
    }

    private void setupRecyclerView() {
        customerAdapter = new CustomerAdapter(this);

        recyclerCustomers.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recyclerCustomers.setHasFixedSize(false);
        recyclerCustomers.setAdapter(customerAdapter);
    }

    private void setupSearch() {
        editSearchCustomer.addTextChangedListener(
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
                        customerAdapter.filter(
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
        View.OnClickListener addCustomerListener =
                view -> showCustomerDialog(null);

        buttonAddCustomerTop.setOnClickListener(
                addCustomerListener
        );

        buttonAddFirstCustomer.setOnClickListener(
                addCustomerListener
        );

        fabAddCustomer.setOnClickListener(
                addCustomerListener
        );
    }

    private void loadCustomers() {
        List<Customer> customers =
                customerDbHelper.getAllCustomers(false);

        customerAdapter.submitList(customers);

        updateScreenState();
    }

    private void updateScreenState() {
        int totalCount = customerAdapter.getTotalCount();
        int visibleCount = customerAdapter.getVisibleCount();

        String searchText =
                editSearchCustomer.getText() == null
                        ? ""
                        : editSearchCustomer
                        .getText()
                        .toString()
                        .trim();

        if (searchText.isEmpty()) {
            if (totalCount == 1) {
                textCustomerCount.setText(
                        "1 customer"
                );
            } else {
                textCustomerCount.setText(
                        totalCount + " customers"
                );
            }

        } else {
            textCustomerCount.setText(
                    visibleCount
                            + " of "
                            + totalCount
                            + " customers"
            );
        }

        boolean listEmpty = visibleCount == 0;

        recyclerCustomers.setVisibility(
                listEmpty
                        ? View.GONE
                        : View.VISIBLE
        );

        emptyState.setVisibility(
                listEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        buttonAddFirstCustomer.setVisibility(
                totalCount == 0
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void showCustomerDialog(
            @Nullable Customer customerToEdit
    ) {
        boolean editing = customerToEdit != null;

        View dialogView = LayoutInflater
                .from(this)
                .inflate(
                        R.layout.dialog_customer,
                        null,
                        false
                );

        TextView textDialogTitle =
                dialogView.findViewById(
                        R.id.text_customer_dialog_title
                );

        TextView textDialogSubtitle =
                dialogView.findViewById(
                        R.id.text_customer_dialog_subtitle
                );

        TextInputLayout layoutCustomerName =
                dialogView.findViewById(
                        R.id.layout_customer_name
                );

        TextInputLayout layoutCustomerMobile =
                dialogView.findViewById(
                        R.id.layout_customer_mobile
                );

        TextInputEditText inputCustomerName =
                dialogView.findViewById(
                        R.id.input_customer_name
                );

        TextInputEditText inputCustomerMobile =
                dialogView.findViewById(
                        R.id.input_customer_mobile
                );

        TextInputEditText inputCustomerAddress =
                dialogView.findViewById(
                        R.id.input_customer_address
                );

        TextInputEditText inputCustomerNotes =
                dialogView.findViewById(
                        R.id.input_customer_notes
                );

        SwitchMaterial switchCustomerActive =
                dialogView.findViewById(
                        R.id.switch_customer_active
                );

        MaterialButton buttonCancel =
                dialogView.findViewById(
                        R.id.button_cancel_customer
                );

        MaterialButton buttonSave =
                dialogView.findViewById(
                        R.id.button_save_customer
                );

        if (editing) {
            textDialogTitle.setText(
                    "Edit Customer"
            );

            textDialogSubtitle.setText(
                    "Update customer details for orders and quotations."
            );

            inputCustomerName.setText(
                    customerToEdit.getName()
            );

            inputCustomerMobile.setText(
                    customerToEdit.getMobile()
            );

            inputCustomerAddress.setText(
                    customerToEdit.getAddress()
            );

            inputCustomerNotes.setText(
                    customerToEdit.getNotes()
            );

            switchCustomerActive.setChecked(
                    customerToEdit.isActive()
            );

            buttonSave.setText(
                    "Update Customer"
            );

        } else {
            textDialogTitle.setText(
                    "Add Customer"
            );

            textDialogSubtitle.setText(
                    "Save customer details for quotations and orders."
            );

            switchCustomerActive.setChecked(true);

            buttonSave.setText(
                    "Save Customer"
            );
        }

        updateSwitchText(switchCustomerActive);

        switchCustomerActive.setOnCheckedChangeListener(
                (buttonView, isChecked) ->
                        updateSwitchText(
                                switchCustomerActive
                        )
        );

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setView(dialogView)
                        .create();

        dialog.setOnShowListener(unused -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams
                                .SOFT_INPUT_ADJUST_RESIZE
                );
            }

            inputCustomerName.requestFocus();

            inputCustomerName.postDelayed(
                    () -> showKeyboard(
                            inputCustomerName
                    ),
                    250
            );
        });

        buttonCancel.setOnClickListener(
                view -> dialog.dismiss()
        );

        buttonSave.setOnClickListener(view -> {
            layoutCustomerName.setError(null);
            layoutCustomerMobile.setError(null);

            String customerName =
                    getInputText(inputCustomerName)
                            .replaceAll("\\s+", " ");

            String customerMobile =
                    getInputText(inputCustomerMobile)
                            .replaceAll("[^0-9+]", "");

            String customerAddress =
                    getInputText(inputCustomerAddress);

            String customerNotes =
                    getInputText(inputCustomerNotes);

            if (customerName.isEmpty()) {
                layoutCustomerName.setError(
                        "Customer name required"
                );

                inputCustomerName.requestFocus();
                return;
            }

            if (customerName.length() < 2) {
                layoutCustomerName.setError(
                        "Enter at least 2 characters"
                );

                inputCustomerName.requestFocus();
                return;
            }

            if (!customerMobile.isEmpty()
                    && customerMobile.replace("+", "")
                    .length() < 10) {
                layoutCustomerMobile.setError(
                        "Enter a valid mobile number"
                );

                inputCustomerMobile.requestFocus();
                return;
            }

            Customer customer;

            if (editing) {
                customer = new Customer(
                        customerToEdit.getId(),
                        customerName,
                        customerMobile,
                        customerAddress,
                        customerNotes,
                        switchCustomerActive.isChecked(),
                        customerToEdit.getCreatedAt(),
                        System.currentTimeMillis()
                );

            } else {
                customer = new Customer(
                        customerName,
                        customerMobile
                );

                customer.setAddress(
                        customerAddress
                );

                customer.setNotes(
                        customerNotes
                );

                customer.setActive(
                        switchCustomerActive.isChecked()
                );
            }

            long result =
                    customerDbHelper.saveCustomer(
                            customer
                    );

            if (result == -2) {
                layoutCustomerName.setError(
                        "Customer name or mobile already exists"
                );

                Toast.makeText(
                        this,
                        "Duplicate customer found",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (result <= 0) {
                Toast.makeText(
                        this,
                        "Customer save नहीं हो सका",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            dialog.dismiss();

            Toast.makeText(
                    this,
                    editing
                            ? "Customer updated"
                            : "Customer added",
                    Toast.LENGTH_SHORT
            ).show();

            loadCustomers();
        });

        dialog.show();
    }

    private void updateSwitchText(
            SwitchMaterial switchMaterial
    ) {
        if (switchMaterial.isChecked()) {
            switchMaterial.setText("Active");

            switchMaterial.setTextColor(
                    Color.parseColor("#107C10")
            );

        } else {
            switchMaterial.setText("Inactive");

            switchMaterial.setTextColor(
                    Color.parseColor("#616161")
            );
        }
    }

    private void showDeleteConfirmation(
            Customer customer
    ) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Customer")
                .setMessage(
                        "Delete \""
                                + customer.getName()
                                + "\"?\n\n"
                                + "The customer will be removed from "
                                + "future order selections."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setNeutralButton(
                        customer.isActive()
                                ? "Mark Inactive"
                                : "Mark Active",
                        (dialog, which) -> {
                            boolean updated =
                                    customerDbHelper
                                            .setCustomerActive(
                                                    customer.getId(),
                                                    !customer.isActive()
                                            );

                            if (updated) {
                                Toast.makeText(
                                        this,
                                        customer.isActive()
                                                ? "Customer marked inactive"
                                                : "Customer marked active",
                                        Toast.LENGTH_SHORT
                                ).show();

                                loadCustomers();

                            } else {
                                Toast.makeText(
                                        this,
                                        "Customer update failed",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> {
                            boolean deleted =
                                    customerDbHelper
                                            .deleteCustomer(
                                                    customer.getId()
                                            );

                            if (deleted) {
                                Toast.makeText(
                                        this,
                                        "Customer deleted",
                                        Toast.LENGTH_SHORT
                                ).show();

                                loadCustomers();

                            } else {
                                Toast.makeText(
                                        this,
                                        "Customer delete failed",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        }
                )
                .show();
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
    public void onEditCustomer(Customer customer) {
        showCustomerDialog(customer);
    }

    @Override
    public void onDeleteCustomer(Customer customer) {
        showDeleteConfirmation(customer);
    }

    @Override
    public void onCustomerClick(Customer customer) {
        showCustomerDialog(customer);
    }
}