package com.example.productprice;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.productprice.security.AppLockManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.Executor;

public class AppLockActivity extends AppCompatActivity {

    private AppLockManager lockManager;

    private TextInputLayout pinLayout;
    private TextInputEditText pinInput;
    private MaterialButton pinUnlockButton;
    private MaterialButton biometricButton;
    private TextView messageText;

    private int failedAttempts = 0;
    private boolean temporarilyBlocked = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock);

        lockManager = ((ProductPriceApplication) getApplication()).getAppLockManager();

        if (!lockManager.isLockEnabled()) {
            lockManager.markUnlocked();
            finish();
            return;
        }

        bindViews();
        setupActions();
        configureBiometric();
    }

    private void bindViews() {
        pinLayout = findViewById(R.id.layout_unlock_pin);
        pinInput = findViewById(R.id.input_unlock_pin);
        pinUnlockButton = findViewById(R.id.button_unlock_pin);
        biometricButton = findViewById(R.id.button_unlock_biometric);
        messageText = findViewById(R.id.text_unlock_message);
    }

    private void setupActions() {
        pinUnlockButton.setOnClickListener(view -> tryPinUnlock());

        pinInput.setOnEditorActionListener(
                (view, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        tryPinUnlock();
                        return true;
                    }
                    return false;
                }
        );

        biometricButton.setOnClickListener(view -> showBiometricPrompt());
    }

    private void tryPinUnlock() {
        if (temporarilyBlocked) {
            messageText.setText("Too many failed attempts. Try again shortly.");
            return;
        }

        String pin = pinInput.getText() == null
                ? ""
                : pinInput.getText().toString().trim();

        if (!pin.matches("\\d{4,8}")) {
            pinLayout.setError("Enter your 4–8 digit PIN");
            return;
        }

        pinLayout.setError(null);

        if (lockManager.verifyPin(pin)) {
            unlockAndClose();
            return;
        }

        failedAttempts++;
        pinInput.setText("");
        pinLayout.setError("Incorrect PIN");

        if (failedAttempts >= 5) {
            startTemporaryBlock();
        }
    }

    private void startTemporaryBlock() {
        temporarilyBlocked = true;
        pinUnlockButton.setEnabled(false);
        pinInput.setEnabled(false);
        messageText.setText("Too many attempts. PIN unlock paused for 30 seconds.");

        handler.postDelayed(
                () -> {
                    temporarilyBlocked = false;
                    failedAttempts = 0;
                    pinUnlockButton.setEnabled(true);
                    pinInput.setEnabled(true);
                    pinLayout.setError(null);
                    messageText.setText("");
                },
                30_000L
        );
    }

    private void configureBiometric() {
        boolean available = canAuthenticateBiometric();
        boolean enabled = lockManager.isBiometricEnabled() && available;

        biometricButton.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);

        if (enabled) {
            biometricButton.postDelayed(this::showBiometricPrompt, 250L);
        }
    }

    private boolean canAuthenticateBiometric() {
        try {
            return BiometricManager.from(this).canAuthenticate()
                    == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception exception) {
            return false;
        }
    }

    private void showBiometricPrompt() {
        if (!lockManager.isBiometricEnabled() || !canAuthenticateBiometric()) {
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                this,
                executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result
                    ) {
                        super.onAuthenticationSucceeded(result);
                        unlockAndClose();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        messageText.setText("Biometric not recognized. Try again or use PIN.");
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            messageText.setText(
                                    errString == null ? "Biometric unlock unavailable" : errString
                            );
                        }
                    }
                }
        );

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Product Price Pro")
                .setSubtitle("Use your device biometric")
                .setNegativeButtonText("Use PIN")
                .build();

        try {
            biometricPrompt.authenticate(promptInfo);
        } catch (Exception exception) {
            Toast.makeText(this, "Biometric unlock could not start", Toast.LENGTH_SHORT).show();
        }
    }

    private void unlockAndClose() {
        lockManager.markUnlocked();
        setResult(RESULT_OK);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
