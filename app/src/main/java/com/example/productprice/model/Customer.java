package com.example.productprice.model;

import java.util.Objects;

public class Customer {

    private long id;
    private String name;
    private String mobile;
    private String address;
    private String notes;
    private boolean active;
    private long createdAt;
    private long updatedAt;

    public Customer() {
        this.id = 0;
        this.name = "";
        this.mobile = "";
        this.address = "";
        this.notes = "";
        this.active = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Customer(String name, String mobile) {
        this();

        this.name = cleanText(name);
        this.mobile = cleanText(mobile);
    }

    public Customer(
            long id,
            String name,
            String mobile,
            String address,
            String notes,
            boolean active,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.name = cleanText(name);
        this.mobile = cleanText(mobile);
        this.address = cleanText(address);
        this.notes = cleanText(notes);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = cleanText(name);
        touchUpdatedAt();
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = cleanText(mobile);
        touchUpdatedAt();
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = cleanText(address);
        touchUpdatedAt();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = cleanText(notes);
        touchUpdatedAt();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        touchUpdatedAt();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isValid() {
        return name != null
                && !name.trim().isEmpty();
    }

    public String getDisplayName() {
        if (mobile == null || mobile.trim().isEmpty()) {
            return name == null ? "" : name;
        }

        return name + " • " + mobile;
    }

    private void touchUpdatedAt() {
        this.updatedAt = System.currentTimeMillis();
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\s+", " ");
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof Customer)) {
            return false;
        }

        Customer customer = (Customer) object;

        return id == customer.id
                && active == customer.active
                && Objects.equals(name, customer.name)
                && Objects.equals(mobile, customer.mobile)
                && Objects.equals(address, customer.address)
                && Objects.equals(notes, customer.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                name,
                mobile,
                address,
                notes,
                active
        );
    }
}