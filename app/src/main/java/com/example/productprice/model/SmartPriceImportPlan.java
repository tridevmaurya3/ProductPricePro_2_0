package com.example.productprice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmartPriceImportPlan {

    private final String effectiveDate;
    private final List<OfficialPriceUpdate> matchedUpdates;
    private final List<String> unmatchedProducts;
    private final List<String> conflicts;

    public SmartPriceImportPlan(String effectiveDate) {
        this.effectiveDate = effectiveDate == null ? "" : effectiveDate.trim();
        matchedUpdates = new ArrayList<>();
        unmatchedProducts = new ArrayList<>();
        conflicts = new ArrayList<>();
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void addMatchedUpdate(OfficialPriceUpdate update) {
        if (update != null) {
            matchedUpdates.add(update);
        }
    }

    public void addUnmatchedProduct(String productName) {
        if (productName != null && !productName.trim().isEmpty()) {
            unmatchedProducts.add(productName.trim());
        }
    }

    public void addConflict(String conflict) {
        if (conflict != null && !conflict.trim().isEmpty()) {
            conflicts.add(conflict.trim());
        }
    }

    public List<OfficialPriceUpdate> getMatchedUpdates() {
        return Collections.unmodifiableList(matchedUpdates);
    }

    public List<String> getUnmatchedProducts() {
        return Collections.unmodifiableList(unmatchedProducts);
    }

    public List<String> getConflicts() {
        return Collections.unmodifiableList(conflicts);
    }

    public int getMatchedCount() {
        return matchedUpdates.size();
    }

    public int getChangedCount() {
        int count = 0;
        for (OfficialPriceUpdate update : matchedUpdates) {
            if (update != null && update.isChanged()) {
                count++;
            }
        }
        return count;
    }

    public int getUnchangedCount() {
        return Math.max(0, getMatchedCount() - getChangedCount());
    }

    public boolean isSafeToApply() {
        return conflicts.isEmpty() && !matchedUpdates.isEmpty();
    }
}
