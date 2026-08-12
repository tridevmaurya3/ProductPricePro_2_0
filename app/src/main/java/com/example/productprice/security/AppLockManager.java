package com.example.productprice.security;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

public class AppLockManager {

    public static final long TIMEOUT_IMMEDIATE = 0L;
    public static final long TIMEOUT_30_SECONDS = 30_000L;
    public static final long TIMEOUT_1_MINUTE = 60_000L;
    public static final long TIMEOUT_5_MINUTES = 300_000L;

    private static final String PREFS = "product_price_security";
    private static final String KEY_LOCK_ENABLED = "lock_enabled";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_BIOMETRIC = "biometric_enabled";
    private static final String KEY_TIMEOUT = "lock_timeout";
    private static final String KEY_SECURE_SCREEN = "secure_screen";
    private static final String KEY_BACKGROUND_AT = "background_at";

    private final SharedPreferences preferences;

    private boolean unlockedForCurrentForeground;

    public AppLockManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        unlockedForCurrentForeground = false;
    }

    public boolean isLockEnabled() {
        return preferences.getBoolean(KEY_LOCK_ENABLED, false)
                && hasPin();
    }

    public boolean hasPin() {
        return !getString(KEY_PIN_HASH).isEmpty()
                && !getString(KEY_PIN_SALT).isEmpty();
    }

    public void setPin(String pin) {
        String cleanPin = cleanPin(pin);
        if (cleanPin.length() < 4 || cleanPin.length() > 8) {
            throw new IllegalArgumentException("PIN must contain 4 to 8 digits");
        }

        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = toHex(saltBytes);
        String hash = hashPin(cleanPin, salt);

        preferences.edit()
                .putString(KEY_PIN_SALT, salt)
                .putString(KEY_PIN_HASH, hash)
                .putBoolean(KEY_LOCK_ENABLED, true)
                .apply();

        unlockedForCurrentForeground = true;
    }

    public boolean verifyPin(String pin) {
        if (!hasPin()) {
            return false;
        }

        String expected = getString(KEY_PIN_HASH);
        String salt = getString(KEY_PIN_SALT);
        String actual = hashPin(cleanPin(pin), salt);
        return constantTimeEquals(expected, actual);
    }

    public void disableLock() {
        preferences.edit()
                .putBoolean(KEY_LOCK_ENABLED, false)
                .putBoolean(KEY_BIOMETRIC, false)
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .remove(KEY_BACKGROUND_AT)
                .apply();

        unlockedForCurrentForeground = true;
    }

    public void setBiometricEnabled(boolean enabled) {
        preferences.edit()
                .putBoolean(KEY_BIOMETRIC, enabled && hasPin())
                .apply();
    }

    public boolean isBiometricEnabled() {
        return isLockEnabled()
                && preferences.getBoolean(KEY_BIOMETRIC, false);
    }

    public void setTimeoutMillis(long timeoutMillis) {
        long safeTimeout;
        if (timeoutMillis == TIMEOUT_30_SECONDS
                || timeoutMillis == TIMEOUT_1_MINUTE
                || timeoutMillis == TIMEOUT_5_MINUTES) {
            safeTimeout = timeoutMillis;
        } else {
            safeTimeout = TIMEOUT_IMMEDIATE;
        }

        preferences.edit()
                .putLong(KEY_TIMEOUT, safeTimeout)
                .apply();
    }

    public long getTimeoutMillis() {
        return preferences.getLong(KEY_TIMEOUT, TIMEOUT_IMMEDIATE);
    }

    public String getTimeoutLabel() {
        long value = getTimeoutMillis();
        if (value == TIMEOUT_30_SECONDS) {
            return "After 30 seconds";
        }
        if (value == TIMEOUT_1_MINUTE) {
            return "After 1 minute";
        }
        if (value == TIMEOUT_5_MINUTES) {
            return "After 5 minutes";
        }
        return "Immediately";
    }

    public void setSecureScreenEnabled(boolean enabled) {
        preferences.edit()
                .putBoolean(KEY_SECURE_SCREEN, enabled)
                .apply();
    }

    public boolean isSecureScreenEnabled() {
        return preferences.getBoolean(KEY_SECURE_SCREEN, false);
    }

    public void markUnlocked() {
        unlockedForCurrentForeground = true;
        preferences.edit()
                .remove(KEY_BACKGROUND_AT)
                .apply();
    }

    public void lockNow() {
        unlockedForCurrentForeground = false;
        preferences.edit()
                .putLong(KEY_BACKGROUND_AT, 0L)
                .apply();
    }

    public void markAppBackgrounded() {
        if (!isLockEnabled()) {
            return;
        }

        preferences.edit()
                .putLong(KEY_BACKGROUND_AT, System.currentTimeMillis())
                .apply();

        if (getTimeoutMillis() == TIMEOUT_IMMEDIATE) {
            unlockedForCurrentForeground = false;
        }
    }

    public boolean shouldShowLockScreen() {
        if (!isLockEnabled()) {
            return false;
        }

        if (!unlockedForCurrentForeground) {
            return true;
        }

        long backgroundAt = preferences.getLong(KEY_BACKGROUND_AT, -1L);
        if (backgroundAt < 0L) {
            return false;
        }

        long timeout = getTimeoutMillis();
        if (timeout == TIMEOUT_IMMEDIATE) {
            unlockedForCurrentForeground = false;
            return true;
        }

        if (System.currentTimeMillis() - backgroundAt >= timeout) {
            unlockedForCurrentForeground = false;
            return true;
        }

        preferences.edit()
                .remove(KEY_BACKGROUND_AT)
                .apply();
        return false;
    }

    private String getString(String key) {
        String value = preferences.getString(key, "");
        return value == null ? "" : value;
    }

    private String cleanPin(String pin) {
        return pin == null
                ? ""
                : pin.replaceAll("[^0-9]", "").trim();
    }

    private String hashPin(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = salt + ":" + pin;
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("PIN security could not be initialized", exception);
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null || first.length() != second.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < first.length(); i++) {
            result |= first.charAt(i) ^ second.charAt(i);
        }
        return result == 0;
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }
}
