package com.example.productprice;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.productprice.data.ProductDbHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class PriceUpdateActivity extends AppCompatActivity {
    private ProductDbHelper db;
    private AutoCompleteTextView categoryDropdown;
    private AutoCompleteTextView roundingDropdown;
    private TextInputEditText changePercent;
    private TextInputEditText discount15;
    private TextInputEditText discount25;
    private TextInputEditText discount35;
    private TextInputEditText discount42;
    private TextInputEditText discount50;
    private MaterialCheckBox includeFullPrice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_update);

        db = ProductDbHelper.getInstance(this);
        db.initialize();
        bindViews();
        setupDropdowns();
        setupActions();
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_price_update);
        toolbar.setNavigationOnClickListener(v -> finish());
        categoryDropdown = findViewById(R.id.dropdown_update_category);
        roundingDropdown = findViewById(R.id.dropdown_rounding);
        changePercent = findViewById(R.id.input_price_change_percent);
        discount15 = findViewById(R.id.input_discount_15);
        discount25 = findViewById(R.id.input_discount_25);
        discount35 = findViewById(R.id.input_discount_35);
        discount42 = findViewById(R.id.input_discount_42);
        discount50 = findViewById(R.id.input_discount_50);
        includeFullPrice = findViewById(R.id.check_update_full_price);
    }

    private void setupDropdowns() {
        List<String> categories = new ArrayList<>();
        categories.add(getString(R.string.all_categories));
        categories.addAll(db.getCategories());
        categoryDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categories));
        categoryDropdown.setText(categories.get(0), false);

        String[] rounding = {"Nearest ₹1", "Nearest ₹5", "Nearest ₹10"};
        roundingDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, rounding));
        roundingDropdown.setText(rounding[0], false);
    }

    private void setupActions() {
        findViewById(R.id.button_apply_price_change).setOnClickListener(v -> confirmScalePrices());
        findViewById(R.id.button_recalculate_discounts).setOnClickListener(v -> confirmRecalculate());
        findViewById(R.id.button_undo_bulk).setOnClickListener(v -> confirmUndo());
    }

    private void confirmScalePrices() {
        double percent = parse(changePercent, 0d);
        if (percent == 0d) {
            Toast.makeText(this, "Enter a non-zero percentage", Toast.LENGTH_SHORT).show();
            return;
        }
        String category = selectedCategory();
        String direction = percent > 0 ? "increase" : "decrease";
        String message = "This will " + direction + " saved prices by " + Math.abs(percent) + "% for " + category + ".";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Apply company price revision?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    int updated = db.scalePrices(category, percent, includeFullPrice.isChecked(), selectedRounding());
                    Toast.makeText(this, updated + " products updated", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void confirmRecalculate() {
        double d15 = parse(discount15, 15d);
        double d25 = parse(discount25, 25d);
        double d35 = parse(discount35, 35d);
        double d42 = parse(discount42, 42d);
        double d50 = parse(discount50, 50d);
        if (!validDiscount(d15) || !validDiscount(d25) || !validDiscount(d35) || !validDiscount(d42) || !validDiscount(d50)) {
            Toast.makeText(this, "Discount values must be between 0 and 100", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Recalculate discount prices?")
                .setMessage("All selected discount prices will be calculated from Full Price. Existing manual company prices will be replaced, but you can undo this bulk update.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Recalculate", (dialog, which) -> {
                    int updated = db.recalculateDiscounts(selectedCategory(), d15, d25, d35, d42, d50, selectedRounding());
                    Toast.makeText(this, updated + " products recalculated", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void confirmUndo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Undo latest bulk update?")
                .setMessage("Prices will return to the snapshot saved immediately before the most recent bulk revision.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Undo", (dialog, which) -> {
                    int restored = db.undoLastBulkOperation();
                    Toast.makeText(this,
                            restored > 0 ? restored + " products restored" : "No bulk update available to undo",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private String selectedCategory() {
        String value = categoryDropdown.getText().toString().trim();
        return value.isEmpty() ? getString(R.string.all_categories) : value;
    }

    private int selectedRounding() {
        String value = roundingDropdown.getText().toString();
        if (value.contains("10")) return 10;
        if (value.contains("5")) return 5;
        return 1;
    }

    private boolean validDiscount(double value) {
        return value >= 0d && value <= 100d;
    }

    private double parse(TextInputEditText input, double fallback) {
        try {
            if (input.getText() == null || input.getText().toString().trim().isEmpty()) return fallback;
            return Double.parseDouble(input.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
