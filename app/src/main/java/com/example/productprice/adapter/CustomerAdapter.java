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
import com.example.productprice.model.Customer;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerAdapter
        extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {

    public interface CustomerActionListener {

        void onEditCustomer(Customer customer);

        void onDeleteCustomer(Customer customer);

        void onCustomerClick(Customer customer);
    }

    private final List<Customer> allCustomers = new ArrayList<>();
    private final List<Customer> visibleCustomers = new ArrayList<>();

    private final CustomerActionListener actionListener;

    private String currentSearchQuery = "";

    public CustomerAdapter(
            CustomerActionListener actionListener
    ) {
        this.actionListener = actionListener;
        setHasStableIds(true);
    }

    public void submitList(List<Customer> customers) {
        allCustomers.clear();

        if (customers != null) {
            allCustomers.addAll(customers);
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
        return visibleCustomers.size();
    }

    public int getTotalCount() {
        return allCustomers.size();
    }

    @Override
    public long getItemId(int position) {
        return visibleCustomers
                .get(position)
                .getId();
    }

    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(
                        R.layout.item_customer,
                        parent,
                        false
                );

        return new CustomerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull CustomerViewHolder holder,
            int position
    ) {
        Customer customer = visibleCustomers.get(position);

        holder.bind(customer);
    }

    @Override
    public int getItemCount() {
        return visibleCustomers.size();
    }

    private void applyFilter() {
        visibleCustomers.clear();

        String normalizedQuery = currentSearchQuery
                .toLowerCase(Locale.ROOT);

        if (normalizedQuery.isEmpty()) {
            visibleCustomers.addAll(allCustomers);

        } else {
            for (Customer customer : allCustomers) {
                String name = safeText(
                        customer.getName()
                ).toLowerCase(Locale.ROOT);

                String mobile = safeText(
                        customer.getMobile()
                ).toLowerCase(Locale.ROOT);

                String address = safeText(
                        customer.getAddress()
                ).toLowerCase(Locale.ROOT);

                String notes = safeText(
                        customer.getNotes()
                ).toLowerCase(Locale.ROOT);

                if (name.contains(normalizedQuery)
                        || mobile.contains(normalizedQuery)
                        || address.contains(normalizedQuery)
                        || notes.contains(normalizedQuery)) {
                    visibleCustomers.add(customer);
                }
            }
        }

        notifyDataSetChanged();
    }

    private String safeText(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    class CustomerViewHolder
            extends RecyclerView.ViewHolder {

        private final TextView textCustomerName;
        private final TextView textCustomerMobile;
        private final TextView textCustomerAddress;
        private final TextView textCustomerStatus;

        private final MaterialButton buttonEdit;
        private final MaterialButton buttonDelete;

        CustomerViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            textCustomerName = itemView.findViewById(
                    R.id.text_customer_name
            );

            textCustomerMobile = itemView.findViewById(
                    R.id.text_customer_mobile
            );

            textCustomerAddress = itemView.findViewById(
                    R.id.text_customer_address
            );

            textCustomerStatus = itemView.findViewById(
                    R.id.text_customer_status
            );

            buttonEdit = itemView.findViewById(
                    R.id.button_edit_customer
            );

            buttonDelete = itemView.findViewById(
                    R.id.button_delete_customer
            );
        }

        void bind(Customer customer) {
            String customerName = safeText(
                    customer.getName()
            );

            String customerMobile = safeText(
                    customer.getMobile()
            );

            String customerAddress = safeText(
                    customer.getAddress()
            );

            textCustomerName.setText(
                    customerName.isEmpty()
                            ? "Unnamed Customer"
                            : customerName
            );

            if (customerMobile.isEmpty()) {
                textCustomerMobile.setText(
                        "Mobile not added"
                );

                textCustomerMobile.setAlpha(0.65f);

            } else {
                textCustomerMobile.setText(
                        customerMobile
                );

                textCustomerMobile.setAlpha(1.0f);
            }

            if (customerAddress.isEmpty()) {
                textCustomerAddress.setText(
                        "Address not added"
                );

                textCustomerAddress.setAlpha(0.65f);

            } else {
                textCustomerAddress.setText(
                        customerAddress
                );

                textCustomerAddress.setAlpha(1.0f);
            }

            updateStatusAppearance(
                    customer.isActive()
            );

            float contentAlpha = customer.isActive()
                    ? 1.0f
                    : 0.58f;

            textCustomerName.setAlpha(
                    contentAlpha
            );

            textCustomerMobile.setAlpha(
                    textCustomerMobile.getAlpha()
                            * contentAlpha
            );

            textCustomerAddress.setAlpha(
                    textCustomerAddress.getAlpha()
                            * contentAlpha
            );

            textCustomerName.setTypeface(
                    null,
                    customer.isActive()
                            ? Typeface.BOLD
                            : Typeface.NORMAL
            );

            itemView.setOnClickListener(view -> {
                if (actionListener != null) {
                    actionListener.onCustomerClick(
                            customer
                    );
                }
            });

            buttonEdit.setOnClickListener(view -> {
                if (actionListener != null) {
                    actionListener.onEditCustomer(
                            customer
                    );
                }
            });

            buttonDelete.setOnClickListener(view -> {
                if (actionListener != null) {
                    actionListener.onDeleteCustomer(
                            customer
                    );
                }
            });
        }

        private void updateStatusAppearance(
                boolean active
        ) {
            if (active) {
                textCustomerStatus.setText(
                        "Active"
                );

                textCustomerStatus.setTextColor(
                        Color.parseColor("#107C10")
                );

                textCustomerStatus.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor("#E8F3E8")
                        )
                );

            } else {
                textCustomerStatus.setText(
                        "Inactive"
                );

                textCustomerStatus.setTextColor(
                        Color.parseColor("#616161")
                );

                textCustomerStatus.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor("#F0F0F0")
                        )
                );
            }
        }
    }
}