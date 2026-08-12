package com.example.productprice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class OfficialCatalogSyncPlan {

    private final String effectiveDate;
    private final List<OfficialCatalogCandidate> missingProducts;
    private final Set<String> newCategories;
    private final List<String> warnings;

    public OfficialCatalogSyncPlan(String effectiveDate) {
        this.effectiveDate = effectiveDate == null ? "" : effectiveDate.trim();
        missingProducts = new ArrayList<>();
        newCategories = new LinkedHashSet<>();
        warnings = new ArrayList<>();
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void addMissingProduct(OfficialCatalogCandidate candidate) {
        if (candidate != null) {
            missingProducts.add(candidate);
        }
    }

    public void addNewCategory(String category) {
        if (category != null && !category.trim().isEmpty()) {
            newCategories.add(category.trim());
        }
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.trim().isEmpty()) {
            warnings.add(warning.trim());
        }
    }

    public List<OfficialCatalogCandidate> getMissingProducts() {
        return Collections.unmodifiableList(missingProducts);
    }

    public Set<String> getNewCategories() {
        return Collections.unmodifiableSet(newCategories);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public int getMissingGroupCount() {
        return missingProducts.size();
    }

    public int getOfficialVariantCount() {
        int count = 0;
        for (OfficialCatalogCandidate candidate : missingProducts) {
            if (candidate == null) {
                continue;
            }
            count += Math.max(1, candidate.getVariantCount());
        }
        return count;
    }

    public boolean hasChanges() {
        return !missingProducts.isEmpty();
    }
}
