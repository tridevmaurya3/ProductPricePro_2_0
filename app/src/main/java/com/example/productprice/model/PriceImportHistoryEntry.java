package com.example.productprice.model;

public class PriceImportHistoryEntry {

    private final String operationId;
    private final String effectiveDate;
    private final String action;
    private final long importedAt;
    private final int productCount;
    private final boolean undoAvailable;
    private final boolean undone;

    public PriceImportHistoryEntry(
            String operationId,
            String effectiveDate,
            String action,
            long importedAt,
            int productCount,
            boolean undoAvailable,
            boolean undone
    ) {
        this.operationId = safe(operationId);
        this.effectiveDate = safe(effectiveDate);
        this.action = safe(action);
        this.importedAt = Math.max(0L, importedAt);
        this.productCount = Math.max(0, productCount);
        this.undoAvailable = undoAvailable;
        this.undone = undone;
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

    public boolean isUndoAvailable() {
        return undoAvailable;
    }

    public boolean isUndone() {
        return undone;
    }

    public String getEffectiveDateLabel() {
        return effectiveDate.isEmpty()
                ? "Effective date not recorded"
                : "Effective " + effectiveDate;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
