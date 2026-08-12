package com.example.productprice.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.productprice.ProductPriceApplication;
import com.example.productprice.model.PriceImportHistoryEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PriceImportHistoryRepository {

    private static final String TABLE_HISTORY = "price_history";

    private final ProductDbHelper productDbHelper;
    private final PriceImportAuditStore auditStore;

    public PriceImportHistoryRepository(ProductDbHelper productDbHelper) {
        if (productDbHelper == null) {
            throw new IllegalArgumentException("Product database is required");
        }

        this.productDbHelper = productDbHelper;

        Context context = ProductPriceApplication.getAppContext();
        auditStore = context == null
                ? null
                : new PriceImportAuditStore(context);
    }

    public List<PriceImportHistoryEntry> getOfficialPdfHistory() {
        SQLiteDatabase db = productDbHelper.getReadableDatabase();
        String latestBulkOperationId = getLatestBulkOperationId(db);
        Map<String, RetainedSnapshot> retained = getRetainedSnapshots(db);

        seedAuditFromExistingSnapshots(retained);

        if (auditStore == null) {
            return buildFromRetainedOnly(
                    retained,
                    latestBulkOperationId
            );
        }

        List<PriceImportHistoryEntry> result = new ArrayList<>();

        for (PriceImportAuditStore.AuditRecord audit : auditStore.getAll()) {
            RetainedSnapshot snapshot = retained.get(audit.getOperationId());
            boolean undone = snapshot == null;

            int productCount = audit.getProductCount() > 0
                    ? audit.getProductCount()
                    : snapshot == null ? 0 : snapshot.productCount;

            long importedAt = audit.getImportedAt() > 0L
                    ? audit.getImportedAt()
                    : snapshot == null ? 0L : snapshot.importedAt;

            String action = !safe(audit.getAction()).isEmpty()
                    ? audit.getAction()
                    : snapshot == null ? "" : snapshot.action;

            String effectiveDate = !safe(audit.getEffectiveDate()).isEmpty()
                    ? audit.getEffectiveDate()
                    : extractEffectiveDate(action);

            result.add(
                    new PriceImportHistoryEntry(
                            audit.getOperationId(),
                            effectiveDate,
                            action,
                            importedAt,
                            productCount,
                            !undone
                                    && audit.getOperationId()
                                    .equals(latestBulkOperationId),
                            undone
                    )
            );
        }

        return result;
    }

    public PriceImportHistoryEntry getLatestOfficialPdfUpdate() {
        List<PriceImportHistoryEntry> history = getOfficialPdfHistory();
        return history.isEmpty() ? null : history.get(0);
    }

    public int getOfficialImportCount() {
        return getOfficialPdfHistory().size();
    }

    private Map<String, RetainedSnapshot> getRetainedSnapshots(SQLiteDatabase db) {
        Map<String, RetainedSnapshot> result = new LinkedHashMap<>();

        String sql = "SELECT operation_id, MAX(changed_at) AS imported_at, "
                + "COUNT(*) AS product_count, MAX(action) AS action "
                + "FROM " + TABLE_HISTORY + " "
                + "WHERE operation_id LIKE 'BULK-PDF-%' "
                + "GROUP BY operation_id "
                + "ORDER BY MAX(changed_at) DESC";

        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                String operationId = safe(
                        cursor.getString(
                                cursor.getColumnIndexOrThrow("operation_id")
                        )
                );

                if (operationId.isEmpty()) {
                    continue;
                }

                result.put(
                        operationId,
                        new RetainedSnapshot(
                                operationId,
                                cursor.getLong(
                                        cursor.getColumnIndexOrThrow("imported_at")
                                ),
                                cursor.getInt(
                                        cursor.getColumnIndexOrThrow("product_count")
                                ),
                                cursor.getString(
                                        cursor.getColumnIndexOrThrow("action")
                                )
                        )
                );
            }
        }

        return result;
    }

    private void seedAuditFromExistingSnapshots(
            Map<String, RetainedSnapshot> retained
    ) {
        if (auditStore == null || retained == null || retained.isEmpty()) {
            return;
        }

        for (RetainedSnapshot snapshot : retained.values()) {
            if (auditStore.get(snapshot.operationId) != null) {
                continue;
            }

            auditStore.save(
                    snapshot.operationId,
                    extractEffectiveDate(snapshot.action),
                    snapshot.action,
                    snapshot.importedAt,
                    snapshot.productCount
            );
        }
    }

    private List<PriceImportHistoryEntry> buildFromRetainedOnly(
            Map<String, RetainedSnapshot> retained,
            String latestBulkOperationId
    ) {
        List<PriceImportHistoryEntry> result = new ArrayList<>();

        for (RetainedSnapshot snapshot : retained.values()) {
            result.add(
                    new PriceImportHistoryEntry(
                            snapshot.operationId,
                            extractEffectiveDate(snapshot.action),
                            snapshot.action,
                            snapshot.importedAt,
                            snapshot.productCount,
                            snapshot.operationId.equals(latestBulkOperationId),
                            false
                    )
            );
        }

        return result;
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

    private static class RetainedSnapshot {
        private final String operationId;
        private final long importedAt;
        private final int productCount;
        private final String action;

        private RetainedSnapshot(
                String operationId,
                long importedAt,
                int productCount,
                String action
        ) {
            this.operationId = operationId == null ? "" : operationId.trim();
            this.importedAt = Math.max(0L, importedAt);
            this.productCount = Math.max(0, productCount);
            this.action = action == null ? "" : action.trim();
        }
    }
}
