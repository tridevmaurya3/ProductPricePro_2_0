package com.example.productprice.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.productprice.model.PriceImportHistoryEntry;

import java.util.ArrayList;
import java.util.List;

public class PriceImportHistoryRepository {

    private static final String TABLE_HISTORY = "price_history";

    private final ProductDbHelper productDbHelper;

    public PriceImportHistoryRepository(ProductDbHelper productDbHelper) {
        if (productDbHelper == null) {
            throw new IllegalArgumentException("Product database is required");
        }
        this.productDbHelper = productDbHelper;
    }

    public List<PriceImportHistoryEntry> getOfficialPdfHistory() {
        List<PriceImportHistoryEntry> result = new ArrayList<>();
        SQLiteDatabase db = productDbHelper.getReadableDatabase();
        String latestBulkOperationId = getLatestBulkOperationId(db);

        String sql = "SELECT operation_id, MAX(changed_at) AS imported_at, "
                + "COUNT(*) AS product_count, MAX(action) AS action "
                + "FROM " + TABLE_HISTORY + " "
                + "WHERE operation_id LIKE 'BULK-PDF-%' "
                + "GROUP BY operation_id "
                + "ORDER BY MAX(changed_at) DESC";

        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                String operationId = cursor.getString(
                        cursor.getColumnIndexOrThrow("operation_id")
                );
                long importedAt = cursor.getLong(
                        cursor.getColumnIndexOrThrow("imported_at")
                );
                int productCount = cursor.getInt(
                        cursor.getColumnIndexOrThrow("product_count")
                );
                String action = cursor.getString(
                        cursor.getColumnIndexOrThrow("action")
                );

                result.add(
                        new PriceImportHistoryEntry(
                                operationId,
                                extractEffectiveDate(action),
                                action,
                                importedAt,
                                productCount,
                                operationId != null
                                        && operationId.equals(latestBulkOperationId)
                        )
                );
            }
        }

        return result;
    }

    public PriceImportHistoryEntry getLatestOfficialPdfUpdate() {
        List<PriceImportHistoryEntry> history = getOfficialPdfHistory();
        return history.isEmpty() ? null : history.get(0);
    }

    public int getOfficialImportCount() {
        SQLiteDatabase db = productDbHelper.getReadableDatabase();

        try (
                Cursor cursor = db.rawQuery(
                        "SELECT COUNT(DISTINCT operation_id) FROM "
                                + TABLE_HISTORY
                                + " WHERE operation_id LIKE 'BULK-PDF-%'",
                        null
                )
        ) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private String getLatestBulkOperationId(SQLiteDatabase db) {
        try (
                Cursor cursor = db.rawQuery(
                        "SELECT operation_id FROM "
                                + TABLE_HISTORY
                                + " WHERE operation_id LIKE 'BULK-%' "
                                + "ORDER BY id DESC LIMIT 1",
                        null
                )
        ) {
            return cursor.moveToFirst() ? safe(cursor.getString(0)) : "";
        }
    }

    private String extractEffectiveDate(String action) {
        String value = safe(action);
        if (value.isEmpty()) {
            return "";
        }

        String marker = "Effective ";
        int index = value.indexOf(marker);
        if (index < 0) {
            return "";
        }

        return value.substring(index + marker.length()).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
