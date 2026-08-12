package com.example.productprice;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;

import com.example.productprice.security.AppLockManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class AppLockSettingsActivity extends AppCompatActivity {

    private final String[] timeoutLabels = {
            "Immediately",
            "After 30 seconds",
            "After 1 minute",
            "After 5 minutes"
    };

    private AppLockManager lockManager;
    private TextView statusText;
    private SwitchMaterial biometricSwitch;
    private SwitchMaterial secureScreenSwitch;
    private MaterialAutoCompleteTextView timeoutDropdown;

    private boolean rendering = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock_settings);

        lockManager = ((ProductPriceApplication) getApplication()).getAppLockManager();
        bindViews();
        setupTimeoutDropdown();
        setupActions();
        renderState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderState();
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_security);
        toolbar.setNavigationOnClickListener(view -> finish());

        statusText = findViewById(R.id.text_security_status);
        biometricSwitch = findViewById(R.id.switch_biometric_unlock);
        secureScreenSwitch = findViewById(R.id.switch_secure_screen);
        timeoutDropdown = findViewById(R.id.dropdown_lock_timeout);
    }

    private void setupTimeoutDropdown() {
        timeoutDropdown.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        timeoutLabels
                )
        );

        timeoutDropdown.setOnItemClickListener(
                (parent, view, position, id) -> {
                    if (rendering) {
                        return;
                    }
                    lockManager.setTimeoutMillis(timeoutForPosition(position));
                    renderState();
                }
        );
    }

    private void setupActions() {
        findViewById(R.id.button_set_change_pin).setOnClickListener(
                view -> beginPinSetupOrChange()
        );

        biometricSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    if (rendering) {
                        return;
                    }

                    if (isChecked && !canUseBiometric()) {
                        Toast.makeText(
                                this,
                                "Biometric unlock is not available on this device",
                                Toast.LENGTH_LONG
                        ).show();
                        renderState();
                        return;
                    }

                    lockManager.setBiometricEnabled(isChecked);
                    renderState();
                }
        );

        secureScreenSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    if (rendering) {
                        return;
                    }
                    lockManager.setSecureScreenEnabled(isChecked);
                    applySecureScreenImmediately();
                }
        );

        findViewById(R.id.button_lock_now).setOnClickListener(
                view -> {
                    if (!lockManager.isLockEnabled()) {
                        Toast.makeText(
                                this,
                                "Set a PIN first",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    lockManager.lockNow();
                    startActivity(new Intent(this, AppLockActivity.class));
                }
        );

        findViewById(R.id.button_disable_app_lock).setOnClickListener(
                view -> confirmDisableLock()
        );
    }

    private void beginPinSetupOrChange() {
        if (!lockManager.hasPin()) {
            showNewPinDialog();
            return;
        }

        TextInputEditText currentPin = createPinInput("Current PIN");
        TextInputLayout wrapper = wrapInput(currentPin, "Current PIN");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Verify current PIN")
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Continue",
                        (dialog, which) -> {
                            if (lockManager.verifyPin(text(currentPin))) {
                                showNewPinDialog();
                            } else {
                                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_LONG).show();
                            }
                        }
                )
                .show();
    }

    private void showNewPinDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(4), padding, 0);

        TextInputEditText newPin = createPinInput("New PIN");
        TextInputEditText confirmPin = createPinInput("Confirm PIN");

        container.addView(wrapInput(newPin, "New PIN"));

        TextInputLayout confirmWrapper = wrapInput(confirmPin, "Confirm PIN");
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        confirmParams.topMargin = dp(8);
        confirmWrapper.setLayoutParams(confirmParams);
        container.addView(confirmWrapper);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(lockManager.hasPin() ? "Change App Lock PIN" : "Create App Lock PIN")
                .setMessage("Choose a 4–8 digit PIN.")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            String first = text(newPin);
                            String second = text(confirmPin);

                            if (!first.matches("\\d{4,8}")) {
                                newPin.setError("Enter 4–8 digits");
                                return;
                            }

                            if (!first.equals(second)) {
                                confirmPin.setError("PINs do not match");
                                return;
                            }

                            try {
                                lockManager.setPin(first);
                                dialog.dismiss();
                                Toast.makeText(
                                        this,
                                        "App lock enabled",
                                        Toast.LENGTH_SHORT
                                ).show();
                                renderState();
                            } catch (Exception exception) {
                                Toast.makeText(
                                        this,
                                        "Could not save PIN",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        })
        );

        dialog.show();
    }

    private void confirmDisableLock() {
        if (!lockManager.hasPin()) {
            lockManager.disableLock();
            renderState();
            return;
        }

        TextInputEditText pinInput = createPinInput("Current PIN");
        TextInputLayout wrapper = wrapInput(pinInput, "Current PIN");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Disable App Lock?")
                .setMessage("Enter your current PIN to remove PIN and biometric protection.")
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Disable",
                        (dialog, which) -> {
                            if (!lockManager.verifyPin(text(pinInput))) {
                                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_LONG).show();
                                return;
                            }

                            lockManager.disableLock();
                            Toast.makeText(this, "App lock disabled", Toast.LENGTH_SHORT).show();
                            renderState();
                        }
                )
                .show();
    }

    private void renderState() {
        if (lockManager == null || statusText == null) {
            return;
        }

        rendering = true;

        boolean enabled = lockManager.isLockEnabled();
        statusText.setText(
                enabled
                        ? "Protected • " + lockManager.getTimeoutLabel()
                        : "App lock is off • create a PIN to protect the app"
        );

        ((com.google.android.material.button.MaterialButton)
                findViewById(R.id.button_set_change_pin)).setText(
                lockManager.hasPin()
                        ? "Change App Lock PIN"
                        : "Set PIN & Enable App Lock"
        );

        biometricSwitch.setEnabled(enabled && canUseBiometric());
        biometricSwitch.setChecked(lockManager.isBiometricEnabled());

        timeoutDropdown.setEnabled(enabled);
        timeoutDropdown.setText(lockManager.getTimeoutLabel(), false);

        secureScreenSwitch.setChecked(lockManager.isSecureScreenEnabled());
        findViewById(R.id.button_lock_now).setEnabled(enabled);
        findViewById(R.id.button_disable_app_lock).setEnabled(enabled);

        rendering = false;
        applySecureScreenImmediately();
    }

    private boolean canUseBiometric() {
        try {
            return BiometricManager.from(this).canAuthenticate()
                    == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception exception) {
            return false;
        }
    }

    private long timeoutForPosition(int position) {
        switch (position) {
            case 1:
                return AppLockManager.TIMEOUT_30_SECONDS;
            case 2:
                return AppLockManager.TIMEOUT_1_MINUTE;
            case 3:
                return AppLockManager.TIMEOUT_5_MINUTES;
            default:
                return AppLockManager.TIMEOUT_IMMEDIATE;
        }
    }

    private TextInputEditText createPinInput(String hint) {
        TextInputEditText input = new TextInputEditText(this);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setMaxLines(1);
        return input;
    }

    private TextInputLayout wrapInput(TextInputEditText input, String hint) {
        TextInputLayout layout = new TextInputLayout(
                this,
                null,
                com.google.android.material.R.attr.textInputOutlinedStyle
        );
        layout.setHint(hint);
        layout.setPadding(dp(20), dp(4), dp(20), 0);
        layout.addView(
                input,
                new TextInputLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        return layout;
    }

    private String text(TextInputEditText input) {
        return input.getText() == null
                ? ""
                : input.getText().toString().trim();
    }

    private void applySecureScreenImmediately() {
        if (lockManager.isSecureScreenEnabled()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
