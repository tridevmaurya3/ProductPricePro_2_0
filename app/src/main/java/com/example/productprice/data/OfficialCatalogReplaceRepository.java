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
 * Replaces only the active product/category catalogue. Customers, quotations, profile and security
 * settings are not touched. The replacement is atomic: if any insert fails the old catalogue
 * remains unchanged.
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

  public ReplaceResult replaceWithOfficialCatalog(List<Product> products, String effectiveDate) {
    if (products == null || products.isEmpty())
      throw new IllegalArgumentException("Catalog is empty");
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    ReplaceResult result = new ReplaceResult();
    result.oldProductCount = (int) DatabaseUtils.queryNumEntries(db, TABLE_PRODUCTS);
    result.oldCategoryCount = (int) DatabaseUtils.queryNumEntries(db, TABLE_CATEGORIES);
    Set<String> stocks = new LinkedHashSet<>(), categories = new LinkedHashSet<>();
    for (Product p : products) {
      if (p == null
          || p.getStockNo().isEmpty()
          || p.getName().isEmpty()
          || p.getCategory().isEmpty()
          || !stocks.add(p.getStockNo()))
        throw new IllegalArgumentException("Invalid or duplicate product identity");
      categories.add(p.getCategory());
    }
    long now = System.currentTimeMillis();
    db.beginTransaction();
    try {
      // Complete catalog + history snapshot. IDs and custom products survive sync/restore.
      for (String table : new String[] {TABLE_PRODUCTS, TABLE_CATEGORIES, TABLE_HISTORY}) {
        db.execSQL("DROP TABLE IF EXISTS catalog_backup_" + table);
        db.execSQL("CREATE TABLE catalog_backup_" + table + " AS SELECT * FROM " + table);
      }
      db.execSQL(
          "CREATE TABLE IF NOT EXISTS catalog_backup_meta (effective_date TEXT, operation_id"
              + " TEXT)");
      db.delete("catalog_backup_meta", null, null);
      ContentValues meta = new ContentValues();
      meta.put("operation_id", "CATALOG-" + now);
      meta.put(
          "effective_date",
          appContext
              .getSharedPreferences("smart_price_update", 0)
              .getString("last_effective_date", ""));
      db.insertOrThrow("catalog_backup_meta", null, meta);
      // Previously official items absent from the new list remain archived, not deleted.
      db.execSQL("UPDATE products SET active=0 WHERE stock_no<>''");
      for (String category : categories) {
        ContentValues cv = new ContentValues();
        cv.put("name", category);
        cv.put("active", 1);
        cv.put("created_at", now);
        cv.put("updated_at", now);
        db.insertWithOnConflict(TABLE_CATEGORIES, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        db.execSQL(
            "UPDATE categories SET active=1 WHERE name=? COLLATE NOCASE", new Object[] {category});
      }
      for (Product p : products) {
        Product old = dbHelper.getProductByStock(p.getStockNo());
        // Older imports had no Stock No.; adopt only an exact display-name match.
        if (old == null) {
          try (android.database.Cursor c =
              db.query(
                  TABLE_PRODUCTS,
                  new String[] {"id", "stock_no"},
                  "name=? COLLATE NOCASE",
                  new String[] {p.getName()},
                  null,
                  null,
                  null)) {
            if (c.moveToFirst() && c.getString(1).isEmpty())
              old = dbHelper.getProduct(c.getLong(0));
          }
        }
        ContentValues v = new ContentValues();
        String name = p.getName();
        try (android.database.Cursor c =
            db.query(
                TABLE_PRODUCTS,
                new String[] {"id"},
                "name=? COLLATE NOCASE",
                new String[] {name},
                null,
                null,
                null)) {
          if (c.moveToFirst() && (old == null || c.getLong(0) != old.getId()))
            name += " [" + p.getStockNo() + "]";
        }
        v.put("name", name);
        v.put("stock_no", p.getStockNo());
        v.put("effective_date", effectiveDate);
        v.put("category", p.getCategory());
        v.put("vp", p.getVp());
        v.put("full_price", p.getFullPrice());
        v.put("price15", p.getPrice15());
        v.put("price25", p.getPrice25());
        v.put("price35", p.getPrice35());
        v.put("price42", p.getPrice42());
        v.put("price50", p.getPrice50());
        v.put("active", 1);
        v.put("updated_at", now);
        if (old == null) {
          db.insertOrThrow(TABLE_PRODUCTS, null, v);
          result.addedCount++;
        } else {
          if (db.update(TABLE_PRODUCTS, v, "id=?", new String[] {String.valueOf(old.getId())}) != 1)
            throw new IllegalStateException("Product vanished");
          result.updatedCount++;
        }
        result.newProductCount++;
      }
      // Retire only the app's known legacy bundled group rows, not unrelated custom products.
      try (java.io.BufferedReader reader =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(
                  appContext
                      .getResources()
                      .openRawResource(com.example.productprice.R.raw.legacy_seed_names),
                  java.nio.charset.StandardCharsets.UTF_8))) {
        String legacyName;
        while ((legacyName = reader.readLine()) != null) {
          db.execSQL(
              "UPDATE products SET active=0 WHERE stock_no='' AND name=? COLLATE NOCASE",
              new Object[] {legacyName});
        }
      } catch (java.io.IOException e) {
        throw new IllegalStateException("Could not reconcile legacy catalog", e);
      }
      db.execSQL(
          "CREATE TABLE IF NOT EXISTS catalog_import_log (operation_id TEXT PRIMARY KEY,"
              + " effective_date TEXT, imported_at INTEGER, product_count INTEGER, restored INTEGER"
              + " DEFAULT 0)");
      db.execSQL(
          "INSERT INTO catalog_import_log VALUES(?,?,?,?,0)",
          new Object[] {"CATALOG-" + now, effectiveDate, now, products.size()});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    appContext
        .getSharedPreferences("smart_price_update", 0)
        .edit()
        .putBoolean("official_full_catalog", true)
        .putString("last_effective_date", effectiveDate)
        .putLong("last_imported_at", now)
        .putInt("last_updated_count", result.newProductCount)
        .apply();
    result.newCategoryCount = categories.size();
    result.effectiveDate = effectiveDate;
    result.replacedAt = now;
    return result;
  }

  public boolean hasBackup() {
    try (android.database.Cursor c =
        dbHelper
            .getReadableDatabase()
            .rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND"
                    + " name='catalog_backup_products'",
                null)) {
      return c.moveToFirst();
    }
  }

  public boolean restoreLastCatalog() {
    if (!hasBackup()) return false;
    SQLiteDatabase db = dbHelper.getWritableDatabase();
    String date = "";
    db.beginTransaction();
    try {
      try (android.database.Cursor c =
          db.rawQuery("SELECT effective_date FROM catalog_backup_meta", null)) {
        if (c.moveToFirst()) date = c.getString(0);
      }
      for (String table : new String[] {TABLE_PRODUCTS, TABLE_CATEGORIES, TABLE_HISTORY}) {
        db.delete(table, null, null);
        db.execSQL("INSERT INTO " + table + " SELECT * FROM catalog_backup_" + table);
        db.execSQL("DROP TABLE catalog_backup_" + table);
      }
      db.execSQL(
          "UPDATE catalog_import_log SET restored=1 WHERE operation_id=(SELECT operation_id FROM"
              + " catalog_backup_meta LIMIT 1)");
      db.execSQL("DROP TABLE catalog_backup_meta");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    appContext
        .getSharedPreferences("smart_price_update", 0)
        .edit()
        .putString("last_effective_date", date)
        .putBoolean("official_full_catalog", !date.isEmpty())
        .remove("last_imported_at")
        .remove("last_updated_count")
        .apply();
    return true;
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  public static class ReplaceResult {
    private int addedCount, updatedCount;

    public int getAddedCount() {
      return addedCount;
    }

    public int getUpdatedCount() {
      return updatedCount;
    }

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
