package com.example.productprice.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.productprice.R;
import com.example.productprice.model.Product;
import com.example.productprice.util.CsvUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProductDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "product_price_pro.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_PRODUCTS = "products";
    private static final String TABLE_HISTORY = "price_history";

    private static ProductDbHelper instance;
    private final Context appContext;

    public static synchronized ProductDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ProductDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private ProductDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.appContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PRODUCTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "category TEXT NOT NULL," +
                "name TEXT NOT NULL COLLATE NOCASE UNIQUE," +
                "vp REAL NOT NULL DEFAULT 0," +
                "full_price INTEGER NOT NULL DEFAULT 0," +
                "price15 INTEGER NOT NULL DEFAULT 0," +
                "price25 INTEGER NOT NULL DEFAULT 0," +
                "price35 INTEGER NOT NULL DEFAULT 0," +
                "price42 INTEGER NOT NULL DEFAULT 0," +
                "price50 INTEGER NOT NULL DEFAULT 0," +
                "active INTEGER NOT NULL DEFAULT 1," +
                "updated_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "operation_id TEXT NOT NULL," +
                "product_id INTEGER NOT NULL," +
                "full_price INTEGER NOT NULL," +
                "price15 INTEGER NOT NULL," +
                "price25 INTEGER NOT NULL," +
                "price35 INTEGER NOT NULL," +
                "price42 INTEGER NOT NULL," +
                "price50 INTEGER NOT NULL," +
                "action TEXT," +
                "changed_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations will be added here. Version 1 is the first database-backed release.
    }

    public synchronized void initialize() {
        SQLiteDatabase db = getWritableDatabase();
        if (DatabaseUtils.queryNumEntries(db, TABLE_PRODUCTS) == 0) {
            try (InputStream input = appContext.getResources().openRawResource(R.raw.p_price)) {
                importCsv(input, false);
            } catch (Exception ignored) {
                // The app remains usable; products can still be imported from Product Manager.
            }
        }
    }

    public List<Product> getAllProducts(boolean activeOnly) {
        SQLiteDatabase db = getReadableDatabase();
        String selection = activeOnly ? "active=1" : null;
        try (Cursor cursor = db.query(TABLE_PRODUCTS, null, selection, null, null, null,
                "category COLLATE NOCASE, name COLLATE NOCASE")) {
            return readProducts(cursor);
        }
    }

    public List<Product> getProductsByCategory(String category, boolean activeOnly) {
        SQLiteDatabase db = getReadableDatabase();
        String selection;
        String[] args;
        if (category == null || category.trim().isEmpty()) {
            selection = activeOnly ? "active=1" : null;
            args = null;
        } else {
            selection = activeOnly ? "category=? AND active=1" : "category=?";
            args = new String[]{category};
        }
        try (Cursor cursor = db.query(TABLE_PRODUCTS, null, selection, args, null, null,
                "name COLLATE NOCASE")) {
            return readProducts(cursor);
        }
    }

    public List<Product> searchProducts(String query) {
        String safe = query == null ? "" : query.trim();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_PRODUCTS, null,
                "name LIKE ? OR category LIKE ?",
                new String[]{"%" + safe + "%", "%" + safe + "%"},
                null, null, "active DESC, category COLLATE NOCASE, name COLLATE NOCASE")) {
            return readProducts(cursor);
        }
    }

    public List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT DISTINCT category FROM " + TABLE_PRODUCTS + " WHERE active=1 ORDER BY category COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) categories.add(cursor.getString(0));
        }
        return categories;
    }

    public Product getProduct(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_PRODUCTS, null, "id=?", new String[]{String.valueOf(id)},
                null, null, null, "1")) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public long getLastUpdated() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT MAX(updated_at) FROM " + TABLE_PRODUCTS, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    public long saveProduct(Product product) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        product.setUpdatedAt(now);

        if (product.getId() > 0) {
            Product old = getProduct(product.getId());
            if (old != null) {
                String operation = "EDIT-" + now;
                db.beginTransaction();
                try {
                    recordHistory(db, old, operation, "Manual product edit");
                    db.update(TABLE_PRODUCTS, toValues(product), "id=?", new String[]{String.valueOf(product.getId())});
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                return product.getId();
            }
        }

        long id = db.insertWithOnConflict(TABLE_PRODUCTS, null, toValues(product), SQLiteDatabase.CONFLICT_REPLACE);
        product.setId(id);
        return id;
    }

    public boolean deleteProduct(long id) {
        return getWritableDatabase().delete(TABLE_PRODUCTS, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public ImportResult importCsv(InputStream inputStream, boolean saveHistory) throws Exception {
        ImportResult result = new ImportResult();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean firstMeaningfulLine = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> values = CsvUtils.parseLine(line);
                if (values.size() < 9) {
                    result.skipped++;
                    continue;
                }
                if (firstMeaningfulLine && isHeader(values)) {
                    firstMeaningfulLine = false;
                    continue;
                }
                firstMeaningfulLine = false;

                Product product = productFromCsv(values);
                if (product.getName().isEmpty() || product.getCategory().isEmpty()) {
                    result.skipped++;
                    continue;
                }

                Product existing = findByName(db, product.getName());
                if (existing == null) {
                    long id = db.insert(TABLE_PRODUCTS, null, toValues(product));
                    if (id > 0) result.inserted++; else result.skipped++;
                } else {
                    product.setId(existing.getId());
                    if (saveHistory) recordHistory(db, existing, "IMPORT-" + System.currentTimeMillis(), "CSV import");
                    db.update(TABLE_PRODUCTS, toValues(product), "id=?", new String[]{String.valueOf(existing.getId())});
                    result.updated++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return result;
    }

    public int scalePrices(String category, double percent, boolean includeFullPrice, int roundTo) {
        SQLiteDatabase db = getWritableDatabase();
        String operation = "BULK-SCALE-" + System.currentTimeMillis();
        List<Product> products = getProductsForBulk(db, category);
        double factor = 1d + (percent / 100d);

        db.beginTransaction();
        try {
            for (Product product : products) {
                recordHistory(db, product, operation, "Bulk price revision " + percent + "%");
                if (includeFullPrice) product.setFullPrice(round(product.getFullPrice() * factor, roundTo));
                product.setPrice15(round(product.getPrice15() * factor, roundTo));
                product.setPrice25(round(product.getPrice25() * factor, roundTo));
                product.setPrice35(round(product.getPrice35() * factor, roundTo));
                product.setPrice42(round(product.getPrice42() * factor, roundTo));
                product.setPrice50(round(product.getPrice50() * factor, roundTo));
                product.setUpdatedAt(System.currentTimeMillis());
                db.update(TABLE_PRODUCTS, toValues(product), "id=?", new String[]{String.valueOf(product.getId())});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return products.size();
    }

    public int recalculateDiscounts(String category, double d15, double d25, double d35,
                                    double d42, double d50, int roundTo) {
        SQLiteDatabase db = getWritableDatabase();
        String operation = "BULK-DISCOUNT-" + System.currentTimeMillis();
        List<Product> products = getProductsForBulk(db, category);

        db.beginTransaction();
        try {
            for (Product product : products) {
                recordHistory(db, product, operation, "Discount recalculation");
                int full = product.getFullPrice();
                product.setPrice15(round(full * (1 - d15 / 100d), roundTo));
                product.setPrice25(round(full * (1 - d25 / 100d), roundTo));
                product.setPrice35(round(full * (1 - d35 / 100d), roundTo));
                product.setPrice42(round(full * (1 - d42 / 100d), roundTo));
                product.setPrice50(round(full * (1 - d50 / 100d), roundTo));
                product.setUpdatedAt(System.currentTimeMillis());
                db.update(TABLE_PRODUCTS, toValues(product), "id=?", new String[]{String.valueOf(product.getId())});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return products.size();
    }

    public int undoLastBulkOperation() {
        SQLiteDatabase db = getWritableDatabase();
        String operation = null;
        try (Cursor cursor = db.rawQuery(
                "SELECT operation_id FROM " + TABLE_HISTORY + " WHERE operation_id LIKE 'BULK-%' ORDER BY id DESC LIMIT 1", null)) {
            if (cursor.moveToFirst()) operation = cursor.getString(0);
        }
        if (operation == null) return 0;

        int restored = 0;
        db.beginTransaction();
        try (Cursor cursor = db.query(TABLE_HISTORY, null, "operation_id=?", new String[]{operation},
                null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put("full_price", cursor.getInt(cursor.getColumnIndexOrThrow("full_price")));
                values.put("price15", cursor.getInt(cursor.getColumnIndexOrThrow("price15")));
                values.put("price25", cursor.getInt(cursor.getColumnIndexOrThrow("price25")));
                values.put("price35", cursor.getInt(cursor.getColumnIndexOrThrow("price35")));
                values.put("price42", cursor.getInt(cursor.getColumnIndexOrThrow("price42")));
                values.put("price50", cursor.getInt(cursor.getColumnIndexOrThrow("price50")));
                values.put("updated_at", System.currentTimeMillis());
                long productId = cursor.getLong(cursor.getColumnIndexOrThrow("product_id"));
                restored += db.update(TABLE_PRODUCTS, values, "id=?", new String[]{String.valueOf(productId)});
            }
            db.delete(TABLE_HISTORY, "operation_id=?", new String[]{operation});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return restored;
    }

    private List<Product> getProductsForBulk(SQLiteDatabase db, String category) {
        String selection = null;
        String[] args = null;
        if (category != null && !category.trim().isEmpty() && !"All Categories".equalsIgnoreCase(category)) {
            selection = "category=?";
            args = new String[]{category};
        }
        try (Cursor cursor = db.query(TABLE_PRODUCTS, null, selection, args, null, null, "id")) {
            return readProducts(cursor);
        }
    }

    private void recordHistory(SQLiteDatabase db, Product product, String operationId, String action) {
        ContentValues values = new ContentValues();
        values.put("operation_id", operationId);
        values.put("product_id", product.getId());
        values.put("full_price", product.getFullPrice());
        values.put("price15", product.getPrice15());
        values.put("price25", product.getPrice25());
        values.put("price35", product.getPrice35());
        values.put("price42", product.getPrice42());
        values.put("price50", product.getPrice50());
        values.put("action", action);
        values.put("changed_at", System.currentTimeMillis());
        db.insert(TABLE_HISTORY, null, values);
    }

    private Product findByName(SQLiteDatabase db, String name) {
        try (Cursor cursor = db.query(TABLE_PRODUCTS, null, "name=? COLLATE NOCASE", new String[]{name},
                null, null, null, "1")) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    private boolean isHeader(List<String> values) {
        return values.get(0).toLowerCase().contains("category") || values.get(1).toLowerCase().contains("product name");
    }

    private Product productFromCsv(List<String> v) {
        Product product = new Product();
        product.setCategory(v.get(0).trim());
        product.setName(v.get(1).trim());
        product.setVp(parseDouble(v.get(2)));
        product.setFullPrice(parseInt(v.get(3)));
        product.setPrice15(parseInt(v.get(4)));
        product.setPrice25(parseInt(v.get(5)));
        product.setPrice35(parseInt(v.get(6)));
        product.setPrice42(parseInt(v.get(7)));
        product.setPrice50(parseInt(v.get(8)));
        product.setActive(true);
        product.setUpdatedAt(System.currentTimeMillis());
        return product;
    }

    private ContentValues toValues(Product product) {
        ContentValues values = new ContentValues();
        values.put("category", product.getCategory().trim());
        values.put("name", product.getName().trim());
        values.put("vp", product.getVp());
        values.put("full_price", product.getFullPrice());
        values.put("price15", product.getPrice15());
        values.put("price25", product.getPrice25());
        values.put("price35", product.getPrice35());
        values.put("price42", product.getPrice42());
        values.put("price50", product.getPrice50());
        values.put("active", product.isActive() ? 1 : 0);
        values.put("updated_at", product.getUpdatedAt() > 0 ? product.getUpdatedAt() : System.currentTimeMillis());
        return values;
    }

    private List<Product> readProducts(Cursor cursor) {
        List<Product> products = new ArrayList<>();
        while (cursor.moveToNext()) products.add(fromCursor(cursor));
        return products;
    }

    private Product fromCursor(Cursor cursor) {
        Product product = new Product();
        product.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        product.setCategory(cursor.getString(cursor.getColumnIndexOrThrow("category")));
        product.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        product.setVp(cursor.getDouble(cursor.getColumnIndexOrThrow("vp")));
        product.setFullPrice(cursor.getInt(cursor.getColumnIndexOrThrow("full_price")));
        product.setPrice15(cursor.getInt(cursor.getColumnIndexOrThrow("price15")));
        product.setPrice25(cursor.getInt(cursor.getColumnIndexOrThrow("price25")));
        product.setPrice35(cursor.getInt(cursor.getColumnIndexOrThrow("price35")));
        product.setPrice42(cursor.getInt(cursor.getColumnIndexOrThrow("price42")));
        product.setPrice50(cursor.getInt(cursor.getColumnIndexOrThrow("price50")));
        product.setActive(cursor.getInt(cursor.getColumnIndexOrThrow("active")) == 1);
        product.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")));
        return product;
    }

    private int round(double value, int roundTo) {
        int safeRound = Math.max(1, roundTo);
        return Math.max(0, (int) (Math.round(value / safeRound) * safeRound));
    }

    private int parseInt(String value) {
        try { return (int) Math.round(Double.parseDouble(value.replaceAll("[^0-9.-]", ""))); }
        catch (Exception e) { return 0; }
    }

    private double parseDouble(String value) {
        try { return Double.parseDouble(value.replaceAll("[^0-9.-]", "")); }
        catch (Exception e) { return 0d; }
    }

    public static class ImportResult {
        public int inserted;
        public int updated;
        public int skipped;

        public int totalChanged() { return inserted + updated; }
    }
}
