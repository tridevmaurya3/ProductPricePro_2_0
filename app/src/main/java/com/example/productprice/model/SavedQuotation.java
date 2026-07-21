package com.example.productprice.model;

import java.util.Locale;
import java.util.Objects;

public class SavedQuotation {

    public static final String ORDER_TYPE_SELF = "Self";
    public static final String ORDER_TYPE_CUSTOMER = "Customer";

    public static final String STATUS_DRAFT = "Draft";
    public static final String STATUS_FINAL = "Final";
    public static final String STATUS_SENT = "Sent";

    private long id;

    private String title;
    private String orderType;

    private long customerId;
    private String customerName;
    private String customerMobile;
    private String customerAddress;

    private String status;
    private String notes;

    private int totalQuantity;
    private double totalVp;
    private int subtotal;
    private int logistics;
    private int grandTotal;

    private long createdAt;
    private long updatedAt;

    public SavedQuotation() {
        this.id = 0;
        this.title = "";
        this.orderType = ORDER_TYPE_SELF;

        this.customerId = 0;
        this.customerName = "";
        this.customerMobile = "";
        this.customerAddress = "";

        this.status = STATUS_DRAFT;
        this.notes = "";

        this.totalQuantity = 0;
        this.totalVp = 0d;
        this.subtotal = 0;
        this.logistics = 0;
        this.grandTotal = 0;

        long currentTime = System.currentTimeMillis();

        this.createdAt = currentTime;
        this.updatedAt = currentTime;
    }

    public SavedQuotation(
            String title,
            String orderType
    ) {
        this();

        setTitle(title);
        setOrderType(orderType);
    }

    public SavedQuotation(
            long id,
            String title,
            String orderType,
            long customerId,
            String customerName,
            String customerMobile,
            String customerAddress,
            String status,
            String notes,
            int totalQuantity,
            double totalVp,
            int subtotal,
            int logistics,
            int grandTotal,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;

        setTitle(title);
        setOrderType(orderType);

        this.customerId = Math.max(0, customerId);
        setCustomerName(customerName);
        setCustomerMobile(customerMobile);
        setCustomerAddress(customerAddress);

        setStatus(status);
        setNotes(notes);

        this.totalQuantity = Math.max(0, totalQuantity);
        this.totalVp = Math.max(0d, totalVp);
        this.subtotal = Math.max(0, subtotal);
        this.logistics = Math.max(0, logistics);
        this.grandTotal = Math.max(0, grandTotal);

        this.createdAt = createdAt > 0
                ? createdAt
                : System.currentTimeMillis();

        this.updatedAt = updatedAt > 0
                ? updatedAt
                : this.createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = Math.max(0, id);
    }

    public String getTitle() {
        return safeText(title);
    }

    public void setTitle(String title) {
        this.title = safeText(title);
    }

    public String getOrderType() {
        return ORDER_TYPE_CUSTOMER.equalsIgnoreCase(
                safeText(orderType)
        )
                ? ORDER_TYPE_CUSTOMER
                : ORDER_TYPE_SELF;
    }

    public void setOrderType(String orderType) {
        this.orderType = ORDER_TYPE_CUSTOMER.equalsIgnoreCase(
                safeText(orderType)
        )
                ? ORDER_TYPE_CUSTOMER
                : ORDER_TYPE_SELF;

        if (ORDER_TYPE_SELF.equals(this.orderType)) {
            clearCustomer();
        }
    }

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(long customerId) {
        this.customerId = Math.max(0, customerId);
    }

    public String getCustomerName() {
        return safeText(customerName);
    }

    public void setCustomerName(String customerName) {
        this.customerName = safeText(customerName);
    }

    public String getCustomerMobile() {
        return safeText(customerMobile);
    }

    public void setCustomerMobile(String customerMobile) {
        this.customerMobile = safeText(customerMobile);
    }

    public String getCustomerAddress() {
        return safeText(customerAddress);
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = safeText(customerAddress);
    }

    public String getStatus() {
        String cleanStatus = safeText(status);

        if (STATUS_FINAL.equalsIgnoreCase(cleanStatus)) {
            return STATUS_FINAL;
        }

        if (STATUS_SENT.equalsIgnoreCase(cleanStatus)) {
            return STATUS_SENT;
        }

        return STATUS_DRAFT;
    }

    public void setStatus(String status) {
        String cleanStatus = safeText(status);

        if (STATUS_FINAL.equalsIgnoreCase(cleanStatus)) {
            this.status = STATUS_FINAL;

        } else if (STATUS_SENT.equalsIgnoreCase(cleanStatus)) {
            this.status = STATUS_SENT;

        } else {
            this.status = STATUS_DRAFT;
        }
    }

    public String getNotes() {
        return safeText(notes);
    }

    public void setNotes(String notes) {
        this.notes = safeText(notes);
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = Math.max(0, totalQuantity);
    }

    public double getTotalVp() {
        return totalVp;
    }

    public void setTotalVp(double totalVp) {
        this.totalVp = Math.max(0d, totalVp);
    }

    public int getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(int subtotal) {
        this.subtotal = Math.max(0, subtotal);
    }

    public int getLogistics() {
        return logistics;
    }

    public void setLogistics(int logistics) {
        this.logistics = Math.max(0, logistics);
    }

    public int getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(int grandTotal) {
        this.grandTotal = Math.max(0, grandTotal);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt > 0
                ? createdAt
                : System.currentTimeMillis();
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt > 0
                ? updatedAt
                : System.currentTimeMillis();
    }

    public boolean isCustomerQuotation() {
        return ORDER_TYPE_CUSTOMER.equals(
                getOrderType()
        );
    }

    public boolean isSelfQuotation() {
        return ORDER_TYPE_SELF.equals(
                getOrderType()
        );
    }

    public boolean isDraft() {
        return STATUS_DRAFT.equals(
                getStatus()
        );
    }

    public boolean isFinal() {
        return STATUS_FINAL.equals(
                getStatus()
        );
    }

    public boolean isSent() {
        return STATUS_SENT.equals(
                getStatus()
        );
    }

    public void setCustomer(Customer customer) {
        if (customer == null) {
            setOrderType(ORDER_TYPE_SELF);
            return;
        }

        this.orderType = ORDER_TYPE_CUSTOMER;
        this.customerId = customer.getId();
        this.customerName = customer.getName();
        this.customerMobile = customer.getMobile();
        this.customerAddress = customer.getAddress();
    }

    public void clearCustomer() {
        this.customerId = 0;
        this.customerName = "";
        this.customerMobile = "";
        this.customerAddress = "";
    }

    public String getOrderForDisplay() {
        if (isCustomerQuotation()) {
            String name = getCustomerName();

            return name.isEmpty()
                    ? "Customer"
                    : name;
        }

        return "Self";
    }

    public String getDisplayTitle() {
        String savedTitle = getTitle();

        if (!savedTitle.isEmpty()) {
            return savedTitle;
        }

        if (isCustomerQuotation()) {
            return getOrderForDisplay()
                    + " Quotation";
        }

        return "Self Quotation";
    }

    public String getTotalVpDisplay() {
        return String.format(
                Locale.getDefault(),
                "%.2f VP",
                getTotalVp()
        );
    }

    public boolean isValid() {
        if (getDisplayTitle().isEmpty()) {
            return false;
        }

        if (getTotalQuantity() <= 0) {
            return false;
        }

        if (isCustomerQuotation()) {
            return !getCustomerName().isEmpty();
        }

        return true;
    }

    private String safeText(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    @Override
    public String toString() {
        return getDisplayTitle();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof SavedQuotation)) {
            return false;
        }

        SavedQuotation quotation =
                (SavedQuotation) object;

        if (id > 0 && quotation.id > 0) {
            return id == quotation.id;
        }

        return createdAt == quotation.createdAt
                && Objects.equals(
                getDisplayTitle(),
                quotation.getDisplayTitle()
        );
    }

    @Override
    public int hashCode() {
        if (id > 0) {
            return Long.valueOf(id).hashCode();
        }

        return Objects.hash(
                getDisplayTitle(),
                createdAt
        );
    }
}