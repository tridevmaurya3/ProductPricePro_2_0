package com.example.productprice.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps a lightweight audit trail for official PDF imports independently of
 * the reversible price snapshot table. The snapshot rows are intentionally
 * deleted by the existing undo flow, while these audit records remain so the
 * user can still see that an import happened and was later undone.
 */
public class PriceImportAuditStore {

    private static final String PREFS = "official_price_import_audit";
    private static final String KEY_INDEX = "operation_index";

    private static final String SUFFIX_EFFECTIVE_DATE = ".effective_date";
    private static final String SUFFIX_ACTION = ".action";
    private static final String SUFFIX_IMPORTED_AT = ".imported_at";
    private static final String SUFFIX_PRODUCT_COUNT = ".product_count";

    private final SharedPreferences preferences;

    public PriceImportAuditStore(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }

        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(
            String operationId,
            String effectiveDate,
            String action,
            long importedAt,
            int productCount
    ) {
        String id = safe(operationId);
        if (id.isEmpty()) {
            return;
        }

        Set<String> index = new HashSet<>(
                preferences.getStringSet(KEY_INDEX, Collections.emptySet())
        );
        index.add(id);

        preferences.edit()
                .putStringSet(KEY_INDEX, index)
                .putString(id + SUFFIX_EFFECTIVE_DATE, safe(effectiveDate))
                .putString(id + SUFFIX_ACTION, safe(action))
                .putLong(id + SUFFIX_IMPORTED_AT, Math.max(0L, importedAt))
                .putInt(id + SUFFIX_PRODUCT_COUNT, Math.max(0, productCount))
                .apply();
    }

    public AuditRecord get(String operationId) {
        String id = safe(operationId);
        if (id.isEmpty()) {
            return null;
        }

        Set<String> index = preferences.getStringSet(
                KEY_INDEX,
                Collections.emptySet()
        );

        if (index == null || !index.contains(id)) {
            return null;
        }

        return new AuditRecord(
                id,
                preferences.getString(id + SUFFIX_EFFECTIVE_DATE, ""),
                preferences.getString(id + SUFFIX_ACTION, ""),
                preferences.getLong(id + SUFFIX_IMPORTED_AT, 0L),
                preferences.getInt(id + SUFFIX_PRODUCT_COUNT, 0)
        );
    }

    public List<AuditRecord> getAll() {
        List<AuditRecord> result = new ArrayList<>();

        Set<String> index = preferences.getStringSet(
                KEY_INDEX,
                Collections.emptySet()
        );

        if (index == null) {
            return result;
        }

        for (String operationId : index) {
            AuditRecord record = get(operationId);
            if (record != null) {
                result.add(record);
            }
        }

        Collections.sort(
                result,
                (left, right) -> Long.compare(
                        right.getImportedAt(),
                        left.getImportedAt()
                )
        );

        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class AuditRecord {

        private final String operationId;
        private final String effectiveDate;
        private final String action;
        private final long importedAt;
        private final int productCount;

        private AuditRecord(
                String operationId,
                String effectiveDate,
                String action,
                long importedAt,
                int productCount
        ) {
            this.operationId = safe(operationId);
            this.effectiveDate = safe(effectiveDate);
            this.action = safe(action);
            this.importedAt = Math.max(0L, importedAt);
            this.productCount = Math.max(0, productCount);
        }

        public String getOperationId() {
            return operationId;
        }

        public String getEffectiveDate() {
            return effectiveDate;
        }

        public String getAction() {
            return action;
        }

        public long getImportedAt() {
            return importedAt;
        }

        public int getProductCount() {
            return productCount;
        }
    }
}
