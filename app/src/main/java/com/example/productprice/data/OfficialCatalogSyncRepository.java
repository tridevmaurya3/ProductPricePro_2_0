package com.example.productprice.data;

import com.example.productprice.model.OfficialCatalogCandidate;
import com.example.productprice.model.Product;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies user-approved new official products to the existing app catalogue.
 * ProductDbHelper.saveProduct() creates a missing category automatically.
 */
public class OfficialCatalogSyncRepository {

    private final ProductDbHelper db;

    public OfficialCatalogSyncRepository(ProductDbHelper db) {
        if (db == null) {
            throw new IllegalArgumentException("Product database is required");
        }
        this.db = db;
    }

    public SyncResult addSmartGroups(List<OfficialCatalogCandidate> candidates) {
        SyncResult result = new SyncResult();
        if (candidates == null || candidates.isEmpty()) return result;

        Set<String> categoriesBefore = normalizedCategories();

        for (OfficialCatalogCandidate candidate : candidates) {
            if (candidate == null) continue;
            Product product = candidate.toSmartGroupProduct();
            long id = db.saveProduct(product);
            if (id > 0) {
                result.productsAdded++;
            } else {
                result.productsSkipped++;
            }
        }

        result.categoriesAdded = countNewCategories(categoriesBefore);
        return result;
    }

    public SyncResult addOfficialVariants(List<OfficialCatalogCandidate> candidates) {
        SyncResult result = new SyncResult();
        if (candidates == null || candidates.isEmpty()) return result;

        Set<String> categoriesBefore = normalizedCategories();

        for (OfficialCatalogCandidate candidate : candidates) {
            if (candidate == null) continue;

            List<Product> products = candidate.toOfficialVariantProducts();
            for (Product product : products) {
                long id = db.saveProduct(product);
                if (id > 0) {
                    result.productsAdded++;
                } else {
                    result.productsSkipped++;
                }
            }
        }

        result.categoriesAdded = countNewCategories(categoriesBefore);
        return result;
    }

    private Set<String> normalizedCategories() {
        Set<String> result = new HashSet<>();
        for (String category : db.getCategories()) {
            result.add(normalize(category));
        }
        return result;
    }

    private int countNewCategories(Set<String> before) {
        int count = 0;
        Set<String> after = normalizedCategories();
        for (String category : after) {
            if (!before.contains(category)) count++;
        }
        return count;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.US).replaceAll("\\s+", " ");
    }

    public static class SyncResult {
        private int productsAdded;
        private int productsSkipped;
        private int categoriesAdded;

        public int getProductsAdded() {
            return productsAdded;
        }

        public int getProductsSkipped() {
            return productsSkipped;
        }

        public int getCategoriesAdded() {
            return categoriesAdded;
        }
    }
}
