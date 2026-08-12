package com.example.productprice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OfficialCatalogBuildResult {

    private final String effectiveDate;
    private final List<Product> products = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    private int associateRows;
    private int preferredRows;
    private int skippedIncompleteRows;

    public OfficialCatalogBuildResult(String effectiveDate) {
        this.effectiveDate = effectiveDate == null ? "" : effectiveDate.trim();
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void addProduct(Product product) {
        if (product == null) return;
        products.add(product);
        String category = product.getCategory() == null
                ? "OTHER PRODUCTS"
                : product.getCategory().trim();
        if (category.isEmpty()) category = "OTHER PRODUCTS";
        categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
    }

    public List<Product> getProducts() {
        return Collections.unmodifiableList(products);
    }

    public Map<String, Integer> getCategoryCounts() {
        return Collections.unmodifiableMap(categoryCounts);
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.trim().isEmpty()) {
            warnings.add(warning.trim());
        }
    }

    public void addError(String error) {
        if (error != null && !error.trim().isEmpty()) {
            errors.add(error.trim());
        }
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public int getProductCount() {
        return products.size();
    }

    public int getCategoryCount() {
        return categoryCounts.size();
    }

    public boolean isReadyToReplace() {
        return errors.isEmpty() && !products.isEmpty();
    }

    public int getAssociateRows() {
        return associateRows;
    }

    public void setAssociateRows(int associateRows) {
        this.associateRows = Math.max(0, associateRows);
    }

    public int getPreferredRows() {
        return preferredRows;
    }

    public void setPreferredRows(int preferredRows) {
        this.preferredRows = Math.max(0, preferredRows);
    }

    public int getSkippedIncompleteRows() {
        return skippedIncompleteRows;
    }

    public void incrementSkippedIncompleteRows() {
        skippedIncompleteRows++;
    }
}
