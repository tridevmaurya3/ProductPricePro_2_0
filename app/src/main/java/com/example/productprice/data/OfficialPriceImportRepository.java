package com.example.productprice.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.productprice.ProductPriceApplication;
import com.example.productprice.model.OfficialPriceUpdate;

import java.util.List;

public class OfficialPriceImportRepository {

    private static final String TABLE_PRODUCTS = "products";
    private static final String TABLE_HISTORY = "price_history";

    private final ProductDbHelper productDbHelper;

    public OfficialPriceImportRepository(ProductDbHelper productDbHelper) {
        if (productDbHelper == null) {
            throw new IllegalArgumentException("Product database is required");
        }
        this.productDbHelper = productDbHelper;
    }

    public int applyOfficialPriceUpdates(
            List<OfficialPriceUpdate> updates,
            String effectiveDate
    ) {
        if (updates == null || updates.isEmpty()) {
            return 0;
        }

        SQLiteDatabase db = productDbHelper.getWritableDatabase();
        String operationId = "BULK-PDF-" + System.currentTimeMillis();
        String action = "Official PDF price update";

        if (effectiveDate != null && !effectiveDate.trim().isEmpty()) {
            action += " • Effective " + effectiveDate.trim();
        }

        int updatedCount = 0;
        long now = System.currentTimeMillis();

        db.beginTransaction();

        try {
            for (OfficialPriceUpdate update : updates) {
                if (update == null || update.getProductId() <= 0 || !update.isChanged()) {
                    continue;
                }

                CurrentPrices current = readCurrentPrices(
                        db,
                        update.getProductId()
                );

                if (current == null) {
                    continue;
                }

                recordHistory(
                        db,
                        operationId,
                        update.getProductId(),
                        current,
                        action
                );

                ContentValues values = new ContentValues();
                values.put("full_price", update.getNewFullPrice());
                values.put("price15", update.getNewPrice15());
                values.put("price25", update.getNewPrice25());
                values.put("price35", update.getNewPrice35());
                values.put("price42", update.getNewPrice42());
                values.put("price50", update.getNewPrice50());
                values.put("updated_at", now);

                updatedCount += db.update(
                        TABLE_PRODUCTS,
                        values,
                        "id=?",
                        new String[]{String.valueOf(update.getProductId())}
                );
            }

            db.setTransactionSuccessful();

        } finally {
            db.endTransaction();
        }

        if (updatedCount > 0) {
            saveAuditRecord(
                    operationId,
                    effectiveDate,
                    action,
                    now,
                    updatedCount
            );
        }

        return updatedCount;
    }

    private void saveAuditRecord(
            String operationId,
            String effectiveDate,
            String action,
            long importedAt,
            int productCount
    ) {
        try {
            Context context = ProductPriceApplication.getAppContext();
            if (context == null) {
                return;
            }

            new PriceImportAuditStore(context).save(
                    operationId,
                    effectiveDate,
                    action,
                    importedAt,
                    productCount
            );
        } catch (Exception ignored) {
            // The database update is authoritative. Audit logging must never
            // make a successful official price import fail.
        }
    }

    private CurrentPrices readCurrentPrices(
            SQLiteDatabase db,
            long productId
    ) {
        try (
                Cursor cursor = db.query(
                        TABLE_PRODUCTS,
                        new String[]{
                                "full_price",
                                "price15",
                                "price25",
                                "price35",
                                "price42",
                                "price50"
                        },
                        "id=?",
                        new String[]{String.valueOf(productId)},
                        null,
                        null,
                        null,
                        "1"
                )
        ) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            return new CurrentPrices(
                    cursor.getInt(cursor.getColumnIndexOrThrow("full_price")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("price15")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("price25")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("price35")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("price42")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("price50"))
            );
        }
    }

    private void recordHistory(
            SQLiteDatabase db,
            String operationId,
            long productId,
            CurrentPrices prices,
            String action
    ) {
        ContentValues values = new ContentValues();
        values.put("operation_id", operationId);
        values.put("product_id", productId);
        values.put("full_price", prices.fullPrice);
        values.put("price15", prices.price15);
        values.put("price25", prices.price25);
        values.put("price35", prices.price35);
        values.put("price42", prices.price42);
        values.put("price50", prices.price50);
        values.put("action", action);
        values.put("changed_at", System.currentTimeMillis());

        db.insert(
                TABLE_HISTORY,
                null,
                values
        );
    }

    private static class CurrentPrices {
        private final int fullPrice;
        private final int price15;
        private final int price25;
        private final int price35;
        private final int price42;
        private final int price50;

        private CurrentPrices(
                int fullPrice,
                int price15,
                int price25,
                int price35,
                int price42,
                int price50
        ) {
            this.fullPrice = fullPrice;
            this.price15 = price15;
            this.price25 = price25;
            this.price35 = price35;
            this.price42 = price42;
            this.price50 = price50;
        }
    }
}
