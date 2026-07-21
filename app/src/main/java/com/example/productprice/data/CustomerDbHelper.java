package com.example.productprice.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.productprice.model.Customer;

import java.util.ArrayList;
import java.util.List;

public class CustomerDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME =
            "product_price_customers.db";

    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CUSTOMERS =
            "customers";

    private static CustomerDbHelper instance;

    public static synchronized CustomerDbHelper getInstance(
            Context context
    ) {
        if (instance == null) {
            instance = new CustomerDbHelper(
                    context.getApplicationContext()
            );
        }

        return instance;
    }

    private CustomerDbHelper(Context context) {
        super(
                context,
                DATABASE_NAME,
                null,
                DATABASE_VERSION
        );
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_CUSTOMERS
                        + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "name TEXT NOT NULL COLLATE NOCASE,"
                        + "mobile TEXT NOT NULL DEFAULT '',"
                        + "address TEXT NOT NULL DEFAULT '',"
                        + "notes TEXT NOT NULL DEFAULT '',"
                        + "active INTEGER NOT NULL DEFAULT 1,"
                        + "created_at INTEGER NOT NULL,"
                        + "updated_at INTEGER NOT NULL"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_customers_name "
                        + "ON "
                        + TABLE_CUSTOMERS
                        + "(name)"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_customers_mobile "
                        + "ON "
                        + TABLE_CUSTOMERS
                        + "(mobile)"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_customers_active "
                        + "ON "
                        + TABLE_CUSTOMERS
                        + "(active)"
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase database,
            int oldVersion,
            int newVersion
    ) {
        /*
         * Future customer database migrations
         * यहाँ जोड़ी जाएँगी।
         */
    }

    public long saveCustomer(Customer customer) {
        if (customer == null) {
            return -1;
        }

        String customerName = cleanName(
                customer.getName()
        );

        String mobile = cleanMobile(
                customer.getMobile()
        );

        if (customerName.isEmpty()) {
            return -1;
        }

        Customer duplicate = findDuplicateCustomer(
                customerName,
                mobile,
                customer.getId()
        );

        if (duplicate != null) {
            return -2;
        }

        SQLiteDatabase database =
                getWritableDatabase();

        long currentTime =
                System.currentTimeMillis();

        customer.setName(customerName);
        customer.setMobile(mobile);
        customer.setAddress(
                cleanText(customer.getAddress())
        );
        customer.setNotes(
                cleanText(customer.getNotes())
        );
        customer.setUpdatedAt(currentTime);

        if (customer.getCreatedAt() <= 0) {
            customer.setCreatedAt(currentTime);
        }

        ContentValues values =
                customerToValues(customer);

        if (customer.getId() > 0) {
            int updated = database.update(
                    TABLE_CUSTOMERS,
                    values,
                    "id=?",
                    new String[]{
                            String.valueOf(customer.getId())
                    }
            );

            return updated > 0
                    ? customer.getId()
                    : -1;
        }

        long customerId = database.insert(
                TABLE_CUSTOMERS,
                null,
                values
        );

        if (customerId > 0) {
            customer.setId(customerId);
        }

        return customerId;
    }

    public Customer getCustomer(long customerId) {
        SQLiteDatabase database =
                getReadableDatabase();

        try (
                Cursor cursor = database.query(
                        TABLE_CUSTOMERS,
                        null,
                        "id=?",
                        new String[]{
                                String.valueOf(customerId)
                        },
                        null,
                        null,
                        null,
                        "1"
                )
        ) {
            if (cursor.moveToFirst()) {
                return customerFromCursor(cursor);
            }
        }

        return null;
    }

    public List<Customer> getAllCustomers(
            boolean activeOnly
    ) {
        SQLiteDatabase database =
                getReadableDatabase();

        String selection = activeOnly
                ? "active=1"
                : null;

        try (
                Cursor cursor = database.query(
                        TABLE_CUSTOMERS,
                        null,
                        selection,
                        null,
                        null,
                        null,
                        "name COLLATE NOCASE ASC"
                )
        ) {
            return readCustomers(cursor);
        }
    }

    public List<Customer> searchCustomers(
            String searchQuery,
            boolean activeOnly
    ) {
        String query = searchQuery == null
                ? ""
                : searchQuery.trim();

        SQLiteDatabase database =
                getReadableDatabase();

        String selection;

        if (activeOnly) {
            selection =
                    "(name LIKE ? OR mobile LIKE ? "
                            + "OR address LIKE ?) "
                            + "AND active=1";
        } else {
            selection =
                    "name LIKE ? OR mobile LIKE ? "
                            + "OR address LIKE ?";
        }

        String searchPattern =
                "%" + query + "%";

        String[] selectionArguments = {
                searchPattern,
                searchPattern,
                searchPattern
        };

        try (
                Cursor cursor = database.query(
                        TABLE_CUSTOMERS,
                        null,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        "active DESC, "
                                + "name COLLATE NOCASE ASC"
                )
        ) {
            return readCustomers(cursor);
        }
    }

    public boolean setCustomerActive(
            long customerId,
            boolean active
    ) {
        SQLiteDatabase database =
                getWritableDatabase();

        ContentValues values =
                new ContentValues();

        values.put(
                "active",
                active ? 1 : 0
        );

        values.put(
                "updated_at",
                System.currentTimeMillis()
        );

        return database.update(
                TABLE_CUSTOMERS,
                values,
                "id=?",
                new String[]{
                        String.valueOf(customerId)
                }
        ) > 0;
    }

    public boolean deleteCustomer(long customerId) {
        SQLiteDatabase database =
                getWritableDatabase();

        return database.delete(
                TABLE_CUSTOMERS,
                "id=?",
                new String[]{
                        String.valueOf(customerId)
                }
        ) > 0;
    }

    public int getCustomerCount(
            boolean activeOnly
    ) {
        SQLiteDatabase database =
                getReadableDatabase();

        if (activeOnly) {
            return (int) DatabaseUtils.queryNumEntries(
                    database,
                    TABLE_CUSTOMERS,
                    "active=1"
            );
        }

        return (int) DatabaseUtils.queryNumEntries(
                database,
                TABLE_CUSTOMERS
        );
    }

    public Customer findByMobile(String mobile) {
        String cleanMobile =
                cleanMobile(mobile);

        if (cleanMobile.isEmpty()) {
            return null;
        }

        SQLiteDatabase database =
                getReadableDatabase();

        try (
                Cursor cursor = database.query(
                        TABLE_CUSTOMERS,
                        null,
                        "mobile=?",
                        new String[]{cleanMobile},
                        null,
                        null,
                        null,
                        "1"
                )
        ) {
            if (cursor.moveToFirst()) {
                return customerFromCursor(cursor);
            }
        }

        return null;
    }

    private Customer findDuplicateCustomer(
            String name,
            String mobile,
            long excludedCustomerId
    ) {
        SQLiteDatabase database =
                getReadableDatabase();

        String selection;
        String[] arguments;

        if (!mobile.isEmpty()) {
            selection =
                    "(name=? COLLATE NOCASE "
                            + "OR mobile=?) AND id<>?";

            arguments = new String[]{
                    name,
                    mobile,
                    String.valueOf(excludedCustomerId)
            };

        } else {
            selection =
                    "name=? COLLATE NOCASE AND id<>?";

            arguments = new String[]{
                    name,
                    String.valueOf(excludedCustomerId)
            };
        }

        try (
                Cursor cursor = database.query(
                        TABLE_CUSTOMERS,
                        null,
                        selection,
                        arguments,
                        null,
                        null,
                        null,
                        "1"
                )
        ) {
            if (cursor.moveToFirst()) {
                return customerFromCursor(cursor);
            }
        }

        return null;
    }

    private ContentValues customerToValues(
            Customer customer
    ) {
        ContentValues values =
                new ContentValues();

        values.put(
                "name",
                cleanName(customer.getName())
        );

        values.put(
                "mobile",
                cleanMobile(customer.getMobile())
        );

        values.put(
                "address",
                cleanText(customer.getAddress())
        );

        values.put(
                "notes",
                cleanText(customer.getNotes())
        );

        values.put(
                "active",
                customer.isActive() ? 1 : 0
        );

        values.put(
                "created_at",
                customer.getCreatedAt() > 0
                        ? customer.getCreatedAt()
                        : System.currentTimeMillis()
        );

        values.put(
                "updated_at",
                customer.getUpdatedAt() > 0
                        ? customer.getUpdatedAt()
                        : System.currentTimeMillis()
        );

        return values;
    }

    private List<Customer> readCustomers(
            Cursor cursor
    ) {
        List<Customer> customers =
                new ArrayList<>();

        while (cursor.moveToNext()) {
            customers.add(
                    customerFromCursor(cursor)
            );
        }

        return customers;
    }

    private Customer customerFromCursor(
            Cursor cursor
    ) {
        Customer customer = new Customer();

        customer.setId(
                cursor.getLong(
                        cursor.getColumnIndexOrThrow("id")
                )
        );

        customer.setName(
                cursor.getString(
                        cursor.getColumnIndexOrThrow("name")
                )
        );

        customer.setMobile(
                cursor.getString(
                        cursor.getColumnIndexOrThrow("mobile")
                )
        );

        customer.setAddress(
                cursor.getString(
                        cursor.getColumnIndexOrThrow("address")
                )
        );

        customer.setNotes(
                cursor.getString(
                        cursor.getColumnIndexOrThrow("notes")
                )
        );

        customer.setActive(
                cursor.getInt(
                        cursor.getColumnIndexOrThrow("active")
                ) == 1
        );

        customer.setCreatedAt(
                cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                                "created_at"
                        )
                )
        );

        customer.setUpdatedAt(
                cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                                "updated_at"
                        )
                )
        );

        return customer;
    }

    private String cleanName(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private String cleanMobile(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("[^0-9+]", "");
    }
}