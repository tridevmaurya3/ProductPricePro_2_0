package com.example.productprice.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.example.productprice.model.Product;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces only the active product/category catalogue. Customers, quotations,
 * profile and security settings are not touched. The replacement is atomic:
 * if any insert fails the old catalogue remains unchanged.
 */
public class OfficialCatalogReplaceRepository {

    private static final String TABLE_PRODUCTS = "products";
    private static final String TABLE_CATEGORIES = "categories";
    private static final String TABLE_HISTORY = "price_history";

    private final Context appContext;
    private final ProductDbHelper dbHelper;

    public OfficialCatalogReplaceRepository(Context context, ProductDbHelper dbHelper) {
        if (context == null || dbHelper == null) {
            throw new IllegalArgumentException("Context and database are required");
        }
        this.appContext = context.getApplicationContext();
        this.dbHelper = dbHelper;
    }

    public ReplaceResult replaceWithOfficialCatalog(
            List<Product> products,
            String effectiveDate
    ) {
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Official catalog is empty");
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ReplaceResult result = new ReplaceResult();
        result.oldProductCount = (int) DatabaseUtils.queryNumEntries(db, TABLE_PRODUCTS);
        result.oldCategoryCount = (int) DatabaseUtils.queryNumEntries(db, TABLE_CATEGORIES);

        long now = System.currentTimeMillis();
        Set<String> categories = new LinkedHashSet<>();
        for (Product product : products) {
            if (product == null) continue;
            String category = safe(product.getCategory());
            String name = safe(product.getName());
            if (category.isEmpty() || name.isEmpty()) {
                throw new IllegalArgumentException("Official product has missing name/category");
            }
            categories.add(category);
        }

        db.beginTransaction();
        try {
            // Old price snapshots point to old product IDs and must not survive
            // a full catalog replacement.
            db.delete(TABLE_HISTORY, null, null);
            db.delete(TABLE_PRODUCTS, null, null);
            db.delete(TABLE_CATEGORIES, null, null);

            for (String category : categories) {
                ContentValues categoryValues = new ContentValues();
                categoryValues.put("name", category);
                categoryValues.put("active", 1);
                categoryValues.put("created_at", now);
                categoryValues.put("updated_at", now);

                long categoryId = db.insertOrThrow(
                        TABLE_CATEGORIES,
                        null,
                        categoryValues
                );
                if (categoryId <= 0) {
                    throw new IllegalStateException("Could not create category " + category);
                }
                result.newCategoryCount++;
            }

            for (Product product : products) {
                if (product == null) continue;

                ContentValues values = new ContentValues();
                values.put("category", safe(product.getCategory()));
                values.put("name", safe(product.getName()));
                values.put("vp", Math.max(0d, product.getVp()));
                values.put("full_price", Math.max(0, product.getFullPrice()));
                values.put("price15", Math.max(0, product.getPrice15()));
                values.put("price25", Math.max(0, product.getPrice25()));
                values.put("price35", Math.max(0, product.getPrice35()));
                values.put("price42", Math.max(0, product.getPrice42()));
                values.put("price50", Math.max(0, product.getPrice50()));
                values.put("active", 1);
                values.put("updated_at", now);

                long id = db.insertOrThrow(TABLE_PRODUCTS, null, values);
                if (id <= 0) {
                    throw new IllegalStateException("Could not add " + product.getName());
                }
                result.newProductCount++;
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (result.newProductCount != products.size()) {
            throw new IllegalStateException("Catalog replacement count mismatch");
        }

        // Old manual mapping memory belongs to the deleted legacy catalogue.
        new CompanyProductMappingStore(appContext).clearAll();

        appContext.getSharedPreferences("smart_price_update", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("official_full_catalog", true)
                .putString("last_effective_date", safe(effectiveDate))
                .putLong("last_imported_at", now)
                .putInt("last_updated_count", result.newProductCount)
                .putInt("last_category_count", result.newCategoryCount)
                .apply();

        result.effectiveDate = safe(effectiveDate);
        result.replacedAt = now;
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class ReplaceResult {
        private int oldProductCount;
        private int oldCategoryCount;
        private int newProductCount;
        private int newCategoryCount;
        private String effectiveDate = "";
        private long replacedAt;

        public int getOldProductCount() {
            return oldProductCount;
        }

        public int getOldCategoryCount() {
            return oldCategoryCount;
        }

        public int getNewProductCount() {
            return newProductCount;
        }

        public int getNewCategoryCount() {
            return newCategoryCount;
        }

        public String getEffectiveDate() {
            return effectiveDate;
        }

        public long getReplacedAt() {
            return replacedAt;
        }
    }
}
