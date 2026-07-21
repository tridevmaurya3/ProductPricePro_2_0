package com.example.productprice.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.productprice.model.SavedQuotation;
import com.example.productprice.model.SavedQuotationItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuotationDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME =
            "saved_quotations.db";

    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_QUOTATIONS =
            "quotations";

    private static final String TABLE_QUOTATION_ITEMS =
            "quotation_items";

    // Quotation columns

    private static final String Q_ID =
            "id";

    private static final String Q_TITLE =
            "title";

    private static final String Q_ORDER_TYPE =
            "order_type";

    private static final String Q_CUSTOMER_ID =
            "customer_id";

    private static final String Q_CUSTOMER_NAME =
            "customer_name";

    private static final String Q_CUSTOMER_MOBILE =
            "customer_mobile";

    private static final String Q_CUSTOMER_ADDRESS =
            "customer_address";

    private static final String Q_STATUS =
            "status";

    private static final String Q_NOTES =
            "notes";

    private static final String Q_TOTAL_QUANTITY =
            "total_quantity";

    private static final String Q_TOTAL_VP =
            "total_vp";

    private static final String Q_SUBTOTAL =
            "subtotal";

    private static final String Q_LOGISTICS =
            "logistics";

    private static final String Q_GRAND_TOTAL =
            "grand_total";

    private static final String Q_CREATED_AT =
            "created_at";

    private static final String Q_UPDATED_AT =
            "updated_at";

    // Quotation item columns

    private static final String I_ID =
            "id";

    private static final String I_QUOTATION_ID =
            "quotation_id";

    private static final String I_PRODUCT_ID =
            "product_id";

    private static final String I_CATEGORY =
            "category";

    private static final String I_PRODUCT_NAME =
            "product_name";

    private static final String I_VOLUME_POINT =
            "volume_point";

    private static final String I_FULL_PRICE =
            "full_price";

    private static final String I_PRICE_15 =
            "price_15";

    private static final String I_PRICE_25 =
            "price_25";

    private static final String I_PRICE_35 =
            "price_35";

    private static final String I_PRICE_42 =
            "price_42";

    private static final String I_PRICE_50 =
            "price_50";

    private static final String I_SELECTED_TIER =
            "selected_tier";

    private static final String I_UNIT_PRICE =
            "unit_price";

    private static final String I_QUANTITY =
            "quantity";

    private static final String I_SORT_ORDER =
            "sort_order";

    private static final String I_CREATED_AT =
            "created_at";

    private static volatile QuotationDbHelper instance;

    public static QuotationDbHelper getInstance(
            @NonNull Context context
    ) {
        if (instance == null) {
            synchronized (QuotationDbHelper.class) {
                if (instance == null) {
                    instance = new QuotationDbHelper(
                            context.getApplicationContext()
                    );
                }
            }
        }

        return instance;
    }

    private QuotationDbHelper(
            @NonNull Context context
    ) {
        super(
                context,
                DATABASE_NAME,
                null,
                DATABASE_VERSION
        );
    }

    @Override
    public void onConfigure(
            SQLiteDatabase database
    ) {
        super.onConfigure(database);

        database.setForeignKeyConstraintsEnabled(
                true
        );
    }

    @Override
    public void onCreate(
            SQLiteDatabase database
    ) {
        createQuotationTable(
                database
        );

        createQuotationItemsTable(
                database
        );

        createIndexes(
                database
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase database,
            int oldVersion,
            int newVersion
    ) {
        /*
         * DATABASE_VERSION अभी 1 है।
         * भविष्य में version बढ़ने पर migration
         * यहीं जोड़ा जाएगा।
         */
    }

    private void createQuotationTable(
            SQLiteDatabase database
    ) {
        String sql =
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_QUOTATIONS
                        + " ("

                        + Q_ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, "

                        + Q_TITLE
                        + " TEXT NOT NULL DEFAULT '', "

                        + Q_ORDER_TYPE
                        + " TEXT NOT NULL DEFAULT 'Self', "

                        + Q_CUSTOMER_ID
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + Q_CUSTOMER_NAME
                        + " TEXT NOT NULL DEFAULT '', "

                        + Q_CUSTOMER_MOBILE
                        + " TEXT NOT NULL DEFAULT '', "

                        + Q_CUSTOMER_ADDRESS
                        + " TEXT NOT NULL DEFAULT '', "

                        + Q_STATUS
                        + " TEXT NOT NULL DEFAULT 'Draft', "

                        + Q_NOTES
                        + " TEXT NOT NULL DEFAULT '', "

                        + Q_TOTAL_QUANTITY
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + Q_TOTAL_VP
                        + " REAL NOT NULL DEFAULT 0, "

                        + Q_SUBTOTAL
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + Q_LOGISTICS
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + Q_GRAND_TOTAL
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + Q_CREATED_AT
                        + " INTEGER NOT NULL, "

                        + Q_UPDATED_AT
                        + " INTEGER NOT NULL"

                        + ")";

        database.execSQL(sql);
    }

    private void createQuotationItemsTable(
            SQLiteDatabase database
    ) {
        String sql =
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_QUOTATION_ITEMS
                        + " ("

                        + I_ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, "

                        + I_QUOTATION_ID
                        + " INTEGER NOT NULL, "

                        + I_PRODUCT_ID
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_CATEGORY
                        + " TEXT NOT NULL DEFAULT '', "

                        + I_PRODUCT_NAME
                        + " TEXT NOT NULL DEFAULT '', "

                        + I_VOLUME_POINT
                        + " REAL NOT NULL DEFAULT 0, "

                        + I_FULL_PRICE
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_PRICE_15
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_PRICE_25
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_PRICE_35
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_PRICE_42
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_PRICE_50
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_SELECTED_TIER
                        + " TEXT NOT NULL DEFAULT 'Full Price', "

                        + I_UNIT_PRICE
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_QUANTITY
                        + " INTEGER NOT NULL DEFAULT 1, "

                        + I_SORT_ORDER
                        + " INTEGER NOT NULL DEFAULT 0, "

                        + I_CREATED_AT
                        + " INTEGER NOT NULL, "

                        + "FOREIGN KEY("
                        + I_QUOTATION_ID
                        + ") REFERENCES "
                        + TABLE_QUOTATIONS
                        + "("
                        + Q_ID
                        + ") ON DELETE CASCADE"

                        + ")";

        database.execSQL(sql);
    }

    private void createIndexes(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_quotation_updated_at "
                        + "ON "
                        + TABLE_QUOTATIONS
                        + "("
                        + Q_UPDATED_AT
                        + " DESC)"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_quotation_customer "
                        + "ON "
                        + TABLE_QUOTATIONS
                        + "("
                        + Q_CUSTOMER_ID
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_quotation_item_parent "
                        + "ON "
                        + TABLE_QUOTATION_ITEMS
                        + "("
                        + I_QUOTATION_ID
                        + ", "
                        + I_SORT_ORDER
                        + ")"
        );
    }

    /**
     * नया quotation insert करता है या पुराने quotation को update करता है।
     *
     * @return quotation ID, असफल होने पर -1
     */
    public long saveQuotation(
            @NonNull SavedQuotation quotation,
            @NonNull List<SavedQuotationItem> items
    ) {
        if (items.isEmpty()) {
            return -1;
        }

        SQLiteDatabase database =
                getWritableDatabase();

        database.beginTransaction();

        try {
            long currentTime =
                    System.currentTimeMillis();

            calculateAndApplyTotals(
                    quotation,
                    items
            );

            quotation.setUpdatedAt(
                    currentTime
            );

            long quotationId =
                    quotation.getId();

            if (quotationId > 0
                    && quotationExists(
                    database,
                    quotationId
            )) {
                ContentValues quotationValues =
                        createQuotationValues(
                                quotation,
                                false
                        );

                int updatedRows =
                        database.update(
                                TABLE_QUOTATIONS,
                                quotationValues,
                                Q_ID + " = ?",
                                new String[]{
                                        String.valueOf(
                                                quotationId
                                        )
                                }
                        );

                if (updatedRows <= 0) {
                    return -1;
                }

                database.delete(
                        TABLE_QUOTATION_ITEMS,
                        I_QUOTATION_ID + " = ?",
                        new String[]{
                                String.valueOf(
                                        quotationId
                                )
                        }
                );

            } else {
                quotation.setCreatedAt(
                        currentTime
                );

                ContentValues quotationValues =
                        createQuotationValues(
                                quotation,
                                true
                        );

                quotationId =
                        database.insertOrThrow(
                                TABLE_QUOTATIONS,
                                null,
                                quotationValues
                        );

                quotation.setId(
                        quotationId
                );
            }

            for (
                    int index = 0;
                    index < items.size();
                    index++
            ) {
                SavedQuotationItem item =
                        items.get(index);

                if (item == null
                        || !item.isValid()) {
                    continue;
                }

                item.setQuotationId(
                        quotationId
                );

                item.setSortOrder(
                        index
                );

                if (item.getCreatedAt() <= 0) {
                    item.setCreatedAt(
                            currentTime
                    );
                }

                ContentValues itemValues =
                        createQuotationItemValues(
                                item
                        );

                long itemId =
                        database.insertOrThrow(
                                TABLE_QUOTATION_ITEMS,
                                null,
                                itemValues
                        );

                item.setId(
                        itemId
                );
            }

            database.setTransactionSuccessful();

            return quotationId;

        } catch (Exception exception) {
            return -1;

        } finally {
            database.endTransaction();
        }
    }

    @NonNull
    public List<SavedQuotation> getAllQuotations() {
        SQLiteDatabase database =
                getReadableDatabase();

        List<SavedQuotation> quotations =
                new ArrayList<>();

        try (
                Cursor cursor =
                        database.query(
                                TABLE_QUOTATIONS,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Q_UPDATED_AT + " DESC"
                        )
        ) {
            while (cursor.moveToNext()) {
                quotations.add(
                        readQuotation(cursor)
                );
            }
        }

        return quotations;
    }

    @NonNull
    public List<SavedQuotation> searchQuotations(
            @Nullable String query
    ) {
        String cleanQuery =
                query == null
                        ? ""
                        : query.trim();

        if (cleanQuery.isEmpty()) {
            return getAllQuotations();
        }

        SQLiteDatabase database =
                getReadableDatabase();

        List<SavedQuotation> quotations =
                new ArrayList<>();

        String searchValue =
                "%" + cleanQuery + "%";

        String selection =
                Q_TITLE + " LIKE ? OR "
                        + Q_CUSTOMER_NAME + " LIKE ? OR "
                        + Q_CUSTOMER_MOBILE + " LIKE ? OR "
                        + Q_STATUS + " LIKE ?";

        String[] selectionArgs = {
                searchValue,
                searchValue,
                searchValue,
                searchValue
        };

        try (
                Cursor cursor =
                        database.query(
                                TABLE_QUOTATIONS,
                                null,
                                selection,
                                selectionArgs,
                                null,
                                null,
                                Q_UPDATED_AT + " DESC"
                        )
        ) {
            while (cursor.moveToNext()) {
                quotations.add(
                        readQuotation(cursor)
                );
            }
        }

        return quotations;
    }

    @Nullable
    public SavedQuotation getQuotation(
            long quotationId
    ) {
        if (quotationId <= 0) {
            return null;
        }

        SQLiteDatabase database =
                getReadableDatabase();

        try (
                Cursor cursor =
                        database.query(
                                TABLE_QUOTATIONS,
                                null,
                                Q_ID + " = ?",
                                new String[]{
                                        String.valueOf(
                                                quotationId
                                        )
                                },
                                null,
                                null,
                                null,
                                "1"
                        )
        ) {
            if (cursor.moveToFirst()) {
                return readQuotation(
                        cursor
                );
            }
        }

        return null;
    }

    @NonNull
    public List<SavedQuotationItem> getQuotationItems(
            long quotationId
    ) {
        if (quotationId <= 0) {
            return Collections.emptyList();
        }

        SQLiteDatabase database =
                getReadableDatabase();

        List<SavedQuotationItem> items =
                new ArrayList<>();

        try (
                Cursor cursor =
                        database.query(
                                TABLE_QUOTATION_ITEMS,
                                null,
                                I_QUOTATION_ID + " = ?",
                                new String[]{
                                        String.valueOf(
                                                quotationId
                                        )
                                },
                                null,
                                null,
                                I_SORT_ORDER + " ASC, "
                                        + I_ID + " ASC"
                        )
        ) {
            while (cursor.moveToNext()) {
                items.add(
                        readQuotationItem(
                                cursor
                        )
                );
            }
        }

        return items;
    }

    public boolean updateQuotationStatus(
            long quotationId,
            @Nullable String newStatus
    ) {
        if (quotationId <= 0) {
            return false;
        }

        SavedQuotation statusValidator =
                new SavedQuotation();

        statusValidator.setStatus(
                newStatus
        );

        ContentValues values =
                new ContentValues();

        values.put(
                Q_STATUS,
                statusValidator.getStatus()
        );

        values.put(
                Q_UPDATED_AT,
                System.currentTimeMillis()
        );

        int updatedRows =
                getWritableDatabase().update(
                        TABLE_QUOTATIONS,
                        values,
                        Q_ID + " = ?",
                        new String[]{
                                String.valueOf(
                                        quotationId
                                )
                        }
                );

        return updatedRows > 0;
    }

    public boolean renameQuotation(
            long quotationId,
            @Nullable String newTitle
    ) {
        if (quotationId <= 0) {
            return false;
        }

        String cleanTitle =
                newTitle == null
                        ? ""
                        : newTitle.trim();

        if (cleanTitle.isEmpty()) {
            return false;
        }

        ContentValues values =
                new ContentValues();

        values.put(
                Q_TITLE,
                cleanTitle
        );

        values.put(
                Q_UPDATED_AT,
                System.currentTimeMillis()
        );

        int updatedRows =
                getWritableDatabase().update(
                        TABLE_QUOTATIONS,
                        values,
                        Q_ID + " = ?",
                        new String[]{
                                String.valueOf(
                                        quotationId
                                )
                        }
                );

        return updatedRows > 0;
    }

    public boolean deleteQuotation(
            long quotationId
    ) {
        if (quotationId <= 0) {
            return false;
        }

        int deletedRows =
                getWritableDatabase().delete(
                        TABLE_QUOTATIONS,
                        Q_ID + " = ?",
                        new String[]{
                                String.valueOf(
                                        quotationId
                                )
                        }
                );

        return deletedRows > 0;
    }

    public int getQuotationCount() {
        SQLiteDatabase database =
                getReadableDatabase();

        try (
                Cursor cursor =
                        database.rawQuery(
                                "SELECT COUNT(*) FROM "
                                        + TABLE_QUOTATIONS,
                                null
                        )
        ) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }

        return 0;
    }

    public boolean quotationExists(
            long quotationId
    ) {
        if (quotationId <= 0) {
            return false;
        }

        return quotationExists(
                getReadableDatabase(),
                quotationId
        );
    }

    private boolean quotationExists(
            SQLiteDatabase database,
            long quotationId
    ) {
        try (
                Cursor cursor =
                        database.query(
                                TABLE_QUOTATIONS,
                                new String[]{Q_ID},
                                Q_ID + " = ?",
                                new String[]{
                                        String.valueOf(
                                                quotationId
                                        )
                                },
                                null,
                                null,
                                null,
                                "1"
                        )
        ) {
            return cursor.moveToFirst();
        }
    }

    private void calculateAndApplyTotals(
            SavedQuotation quotation,
            List<SavedQuotationItem> items
    ) {
        int totalQuantity = 0;
        double totalVp = 0d;
        int subtotal = 0;

        for (
                SavedQuotationItem item :
                items
        ) {
            if (item == null
                    || !item.isValid()) {
                continue;
            }

            totalQuantity +=
                    item.getQuantity();

            totalVp +=
                    item.getTotalVp();

            subtotal +=
                    item.getTotalPrice();
        }

        int logistics =
                totalVp > 0
                        && totalVp < 100
                        ? 118
                        : 0;

        quotation.setTotalQuantity(
                totalQuantity
        );

        quotation.setTotalVp(
                totalVp
        );

        quotation.setSubtotal(
                subtotal
        );

        quotation.setLogistics(
                logistics
        );

        quotation.setGrandTotal(
                subtotal + logistics
        );
    }

    private ContentValues createQuotationValues(
            SavedQuotation quotation,
            boolean includeCreatedAt
    ) {
        ContentValues values =
                new ContentValues();

        values.put(
                Q_TITLE,
                quotation.getDisplayTitle()
        );

        values.put(
                Q_ORDER_TYPE,
                quotation.getOrderType()
        );

        values.put(
                Q_CUSTOMER_ID,
                quotation.getCustomerId()
        );

        values.put(
                Q_CUSTOMER_NAME,
                quotation.getCustomerName()
        );

        values.put(
                Q_CUSTOMER_MOBILE,
                quotation.getCustomerMobile()
        );

        values.put(
                Q_CUSTOMER_ADDRESS,
                quotation.getCustomerAddress()
        );

        values.put(
                Q_STATUS,
                quotation.getStatus()
        );

        values.put(
                Q_NOTES,
                quotation.getNotes()
        );

        values.put(
                Q_TOTAL_QUANTITY,
                quotation.getTotalQuantity()
        );

        values.put(
                Q_TOTAL_VP,
                quotation.getTotalVp()
        );

        values.put(
                Q_SUBTOTAL,
                quotation.getSubtotal()
        );

        values.put(
                Q_LOGISTICS,
                quotation.getLogistics()
        );

        values.put(
                Q_GRAND_TOTAL,
                quotation.getGrandTotal()
        );

        if (includeCreatedAt) {
            values.put(
                    Q_CREATED_AT,
                    quotation.getCreatedAt()
            );
        }

        values.put(
                Q_UPDATED_AT,
                quotation.getUpdatedAt()
        );

        return values;
    }

    private ContentValues createQuotationItemValues(
            SavedQuotationItem item
    ) {
        ContentValues values =
                new ContentValues();

        values.put(
                I_QUOTATION_ID,
                item.getQuotationId()
        );

        values.put(
                I_PRODUCT_ID,
                item.getProductId()
        );

        values.put(
                I_CATEGORY,
                item.getCategory()
        );

        values.put(
                I_PRODUCT_NAME,
                item.getProductName()
        );

        values.put(
                I_VOLUME_POINT,
                item.getVolumePoint()
        );

        values.put(
                I_FULL_PRICE,
                item.getFullPrice()
        );

        values.put(
                I_PRICE_15,
                item.getPrice15()
        );

        values.put(
                I_PRICE_25,
                item.getPrice25()
        );

        values.put(
                I_PRICE_35,
                item.getPrice35()
        );

        values.put(
                I_PRICE_42,
                item.getPrice42()
        );

        values.put(
                I_PRICE_50,
                item.getPrice50()
        );

        values.put(
                I_SELECTED_TIER,
                item.getSelectedTier()
        );

        values.put(
                I_UNIT_PRICE,
                item.getEffectiveUnitPrice()
        );

        values.put(
                I_QUANTITY,
                item.getQuantity()
        );

        values.put(
                I_SORT_ORDER,
                item.getSortOrder()
        );

        values.put(
                I_CREATED_AT,
                item.getCreatedAt()
        );

        return values;
    }

    private SavedQuotation readQuotation(
            Cursor cursor
    ) {
        return new SavedQuotation(
                getLong(cursor, Q_ID),
                getString(cursor, Q_TITLE),
                getString(cursor, Q_ORDER_TYPE),
                getLong(cursor, Q_CUSTOMER_ID),
                getString(cursor, Q_CUSTOMER_NAME),
                getString(cursor, Q_CUSTOMER_MOBILE),
                getString(cursor, Q_CUSTOMER_ADDRESS),
                getString(cursor, Q_STATUS),
                getString(cursor, Q_NOTES),
                getInt(cursor, Q_TOTAL_QUANTITY),
                getDouble(cursor, Q_TOTAL_VP),
                getInt(cursor, Q_SUBTOTAL),
                getInt(cursor, Q_LOGISTICS),
                getInt(cursor, Q_GRAND_TOTAL),
                getLong(cursor, Q_CREATED_AT),
                getLong(cursor, Q_UPDATED_AT)
        );
    }

    private SavedQuotationItem readQuotationItem(
            Cursor cursor
    ) {
        return new SavedQuotationItem(
                getLong(cursor, I_ID),
                getLong(cursor, I_QUOTATION_ID),
                getLong(cursor, I_PRODUCT_ID),
                getString(cursor, I_CATEGORY),
                getString(cursor, I_PRODUCT_NAME),
                getDouble(cursor, I_VOLUME_POINT),
                getInt(cursor, I_FULL_PRICE),
                getInt(cursor, I_PRICE_15),
                getInt(cursor, I_PRICE_25),
                getInt(cursor, I_PRICE_35),
                getInt(cursor, I_PRICE_42),
                getInt(cursor, I_PRICE_50),
                getString(cursor, I_SELECTED_TIER),
                getInt(cursor, I_UNIT_PRICE),
                getInt(cursor, I_QUANTITY),
                getInt(cursor, I_SORT_ORDER),
                getLong(cursor, I_CREATED_AT)
        );
    }

    private int getColumnIndex(
            Cursor cursor,
            String columnName
    ) {
        return cursor.getColumnIndexOrThrow(
                columnName
        );
    }

    private String getString(
            Cursor cursor,
            String columnName
    ) {
        int index =
                getColumnIndex(
                        cursor,
                        columnName
                );

        return cursor.isNull(index)
                ? ""
                : cursor.getString(index);
    }

    private int getInt(
            Cursor cursor,
            String columnName
    ) {
        int index =
                getColumnIndex(
                        cursor,
                        columnName
                );

        return cursor.isNull(index)
                ? 0
                : cursor.getInt(index);
    }

    private long getLong(
            Cursor cursor,
            String columnName
    ) {
        int index =
                getColumnIndex(
                        cursor,
                        columnName
                );

        return cursor.isNull(index)
                ? 0L
                : cursor.getLong(index);
    }

    private double getDouble(
            Cursor cursor,
            String columnName
    ) {
        int index =
                getColumnIndex(
                        cursor,
                        columnName
                );

        return cursor.isNull(index)
                ? 0d
                : cursor.getDouble(index);
    }
}