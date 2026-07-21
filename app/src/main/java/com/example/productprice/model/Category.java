package com.example.productprice.model;

import java.util.Objects;

public class Category {

    private long id;
    private String name;
    private boolean active;
    private long createdAt;
    private long updatedAt;

    public Category() {
        this.id = 0;
        this.name = "";
        this.active = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Category(String name) {
        this.id = 0;
        this.name = name == null ? "" : name.trim();
        this.active = true;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public Category(
            long id,
            String name,
            boolean active,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.name = name == null ? "" : name.trim();
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
        this.name = name == null ? "" : name.trim();
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = System.currentTimeMillis();
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
        return name != null && !name.trim().isEmpty();
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof Category)) {
            return false;
        }

        Category category = (Category) object;

        return id == category.id
                && active == category.active
                && Objects.equals(name, category.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, active);
    }
}