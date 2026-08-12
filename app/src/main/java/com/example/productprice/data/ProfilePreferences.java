package com.example.productprice.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the local owner/profile information used by Product Price Pro.
 * No profile data is sent to a server.
 */
public class ProfilePreferences {

    private static final String PREFS = "product_price_profile";
    private static final String KEY_NAME = "name";
    private static final String KEY_ORGANIZATION = "organization";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHOTO_URI = "photo_uri";

    private final SharedPreferences preferences;

    public ProfilePreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getName() {
        return getString(KEY_NAME);
    }

    public void setName(String value) {
        putString(KEY_NAME, value);
    }

    public String getOrganization() {
        String value = getString(KEY_ORGANIZATION);
        return value.isEmpty() ? "Health Care Wellness Club" : value;
    }

    public void setOrganization(String value) {
        putString(KEY_ORGANIZATION, value);
    }

    public String getPhone() {
        return getString(KEY_PHONE);
    }

    public void setPhone(String value) {
        putString(KEY_PHONE, value);
    }

    public String getEmail() {
        return getString(KEY_EMAIL);
    }

    public void setEmail(String value) {
        putString(KEY_EMAIL, value);
    }

    public String getPhotoUri() {
        return getString(KEY_PHOTO_URI);
    }

    public void setPhotoUri(String value) {
        putString(KEY_PHOTO_URI, value);
    }

    public void save(
            String name,
            String organization,
            String phone,
            String email
    ) {
        preferences.edit()
                .putString(KEY_NAME, clean(name))
                .putString(KEY_ORGANIZATION, clean(organization))
                .putString(KEY_PHONE, clean(phone))
                .putString(KEY_EMAIL, clean(email))
                .apply();
    }

    private String getString(String key) {
        String value = preferences.getString(key, "");
        return value == null ? "" : value.trim();
    }

    private void putString(String key, String value) {
        preferences.edit()
                .putString(key, clean(value))
                .apply();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
