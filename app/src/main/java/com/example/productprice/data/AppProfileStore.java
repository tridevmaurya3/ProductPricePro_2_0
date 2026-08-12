package com.example.productprice.data;

import android.content.Context;
import android.content.SharedPreferences;

public class AppProfileStore {

    private static final String PREFS = "product_price_profile";
    private static final String KEY_NAME = "name";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ORGANIZATION = "organization";
    private static final String KEY_PHOTO_URI = "photo_uri";

    private final SharedPreferences preferences;

    public AppProfileStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getName() {
        return preferences.getString(KEY_NAME, "") == null
                ? ""
                : preferences.getString(KEY_NAME, "").trim();
    }

    public String getPhone() {
        return preferences.getString(KEY_PHONE, "") == null
                ? ""
                : preferences.getString(KEY_PHONE, "").trim();
    }

    public String getEmail() {
        return preferences.getString(KEY_EMAIL, "") == null
                ? ""
                : preferences.getString(KEY_EMAIL, "").trim();
    }

    public String getOrganization() {
        return preferences.getString(KEY_ORGANIZATION, "Health Care Wellness Club") == null
                ? "Health Care Wellness Club"
                : preferences.getString(KEY_ORGANIZATION, "Health Care Wellness Club").trim();
    }

    public String getPhotoUri() {
        return preferences.getString(KEY_PHOTO_URI, "") == null
                ? ""
                : preferences.getString(KEY_PHOTO_URI, "").trim();
    }

    public void save(
            String name,
            String phone,
            String email,
            String organization
    ) {
        preferences.edit()
                .putString(KEY_NAME, clean(name))
                .putString(KEY_PHONE, clean(phone))
                .putString(KEY_EMAIL, clean(email))
                .putString(KEY_ORGANIZATION, clean(organization))
                .apply();
    }

    public void setPhotoUri(String photoUri) {
        preferences.edit()
                .putString(KEY_PHOTO_URI, clean(photoUri))
                .apply();
    }

    public String getDisplayName() {
        String name = getName();
        return name.isEmpty() ? "My Profile" : name;
    }

    public String getInitials() {
        String name = getName();
        if (name.isEmpty()) {
            return "PP";
        }

        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }

        return initials.length() == 0 ? "PP" : initials.toString();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
