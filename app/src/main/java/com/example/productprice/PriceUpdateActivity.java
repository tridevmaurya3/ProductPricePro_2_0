package com.example.productprice;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.CompanyPriceRow;
import com.example.productprice.util.CompanyPricePdfParser;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private MaterialButton selectCompanyPdfsButton;
    private MaterialButton analyzeCompanyPdfsButton;

    private TextView pdfSelectionSummaryText;
    private TextView associatePdfStatusText;
    private TextView preferredPdfStatusText;
    private TextView pdfEffectiveDateText;
    private TextView pdfCrosscheckText;
    private TextView pdfWarningsText;

    private MaterialCardView pdfAnalysisCard;
    private LinearProgressIndicator pdfAnalysisProgress;

    private final List<Uri> selectedCompanyPdfUris =
            new ArrayList<>();

    private final List<String> selectedCompanyPdfNames =
            new ArrayList<>();

    private final ExecutorService pdfExecutor =
            Executors.newSingleThreadExecutor();

    private CompanyPriceDocument associateDocument;
    private CompanyPriceDocument preferredCustomerDocument;

    private final ActivityResultLauncher<String[]> pdfPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenMultipleDocuments(),
                    this::handleSelectedPdfUris
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_price_update
        );

        db = ProductDbHelper.getInstance(this);
        db.initialize();

        bindViews();
        setupDropdowns();
        setupActions();
        resetPdfImportState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pdfExecutor.shutdownNow();
    }

    private void bindViews() {
        MaterialToolbar toolbar =
                findViewById(
                        R.id.toolbar_price_update
                );

        toolbar.setNavigationOnClickListener(
                view -> finish()
        );

        categoryDropdown =
                findViewById(
                        R.id.dropdown_update_category
                );

        roundingDropdown =
                findViewById(
                        R.id.dropdown_rounding
                );

        changePercent =
                findViewById(
                        R.id.input_price_change_percent
                );

        discount15 =
                findViewById(
                        R.id.input_discount_15
                );

        discount25 =
                findViewById(
                        R.id.input_discount_25
                );

        discount35 =
                findViewById(
                        R.id.input_discount_35
                );

        discount42 =
                findViewById(
                        R.id.input_discount_42
                );

        discount50 =
                findViewById(
                        R.id.input_discount_50
                );

        includeFullPrice =
                findViewById(
                        R.id.check_update_full_price
                );

        selectCompanyPdfsButton =
                findViewById(
                        R.id.button_select_company_pdfs
                );

        analyzeCompanyPdfsButton =
                findViewById(
                        R.id.button_analyze_company_pdfs
                );

        pdfSelectionSummaryText =
                findViewById(
                        R.id.text_pdf_selection_summary
                );

        associatePdfStatusText =
                findViewById(
                        R.id.text_associate_pdf_status
                );

        preferredPdfStatusText =
                findViewById(
                        R.id.text_preferred_pdf_status
                );

        pdfEffectiveDateText =
                findViewById(
                        R.id.text_pdf_effective_date
                );

        pdfCrosscheckText =
                findViewById(
                        R.id.text_pdf_crosscheck
                );

        pdfWarningsText =
                findViewById(
                        R.id.text_pdf_analysis_warnings
                );

        pdfAnalysisCard =
                findViewById(
                        R.id.card_pdf_analysis
                );

        pdfAnalysisProgress =
                findViewById(
                        R.id.progress_pdf_analysis
                );
    }

    private void setupDropdowns() {
        List<String> categories =
                new ArrayList<>();

        categories.add(
                getString(
                        R.string.all_categories
                )
        );

        categories.addAll(
                db.getCategories()
        );

        categoryDropdown.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        categories
                )
        );

        categoryDropdown.setText(
                categories.get(0),
                false
        );

        String[] rounding = {
                "Nearest ₹1",
                "Nearest ₹5",
                "Nearest ₹10"
        };

        roundingDropdown.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        rounding
                )
        );

        roundingDropdown.setText(
                rounding[0],
                false
        );
    }

    private void setupActions() {
        selectCompanyPdfsButton.setOnClickListener(
                view -> selectOfficialCompanyPdfs()
        );

        analyzeCompanyPdfsButton.setOnClickListener(
                view -> analyzeSelectedCompanyPdfs()
        );

        findViewById(
                R.id.button_apply_price_change
        ).setOnClickListener(
                view -> confirmScalePrices()
        );

        findViewById(
                R.id.button_recalculate_discounts
        ).setOnClickListener(
                view -> confirmRecalculate()
        );

        findViewById(
                R.id.button_undo_bulk
        ).setOnClickListener(
                view -> confirmUndo()
        );
    }

    private void selectOfficialCompanyPdfs() {
        pdfPickerLauncher.launch(
                new String[]{
                        "application/pdf"
                }
        );
    }

    private void handleSelectedPdfUris(
            List<Uri> uris
    ) {
        selectedCompanyPdfUris.clear();
        selectedCompanyPdfNames.clear();

        associateDocument = null;
        preferredCustomerDocument = null;

        pdfAnalysisCard.setVisibility(
                View.GONE
        );

        if (uris == null
                || uris.isEmpty()) {
            resetPdfImportState();
            return;
        }

        if (uris.size() != 2) {
            Toast.makeText(
                    this,
                    "Please select exactly 2 official PDF price lists",
                    Toast.LENGTH_LONG
            ).show();

            resetPdfImportState();
            return;
        }

        for (Uri uri : uris) {
            if (uri == null) {
                continue;
            }

            selectedCompanyPdfUris.add(
                    uri
            );

            selectedCompanyPdfNames.add(
                    getDisplayName(uri)
            );

            try {
                getContentResolver()
                        .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );

            } catch (Exception ignored) {
                // Temporary read permission from the picker is enough for this session.
            }
        }

        if (selectedCompanyPdfUris.size() != 2) {
            resetPdfImportState();
            return;
        }

        pdfSelectionSummaryText.setText(
                "2 PDFs selected • ready to analyze"
        );

        associatePdfStatusText.setText(
                "Detecting automatically"
        );

        preferredPdfStatusText.setText(
                "Detecting automatically"
        );

        analyzeCompanyPdfsButton.setEnabled(
                true
        );
    }

    private void resetPdfImportState() {
        associateDocument = null;
        preferredCustomerDocument = null;

        selectedCompanyPdfUris.clear();
        selectedCompanyPdfNames.clear();

        pdfSelectionSummaryText.setText(
                "No PDFs selected"
        );

        associatePdfStatusText.setText(
                "Waiting for PDF"
        );

        preferredPdfStatusText.setText(
                "Waiting for PDF"
        );

        analyzeCompanyPdfsButton.setEnabled(
                false
        );

        pdfAnalysisProgress.setVisibility(
                View.GONE
        );

        pdfAnalysisCard.setVisibility(
                View.GONE
        );
    }

    private void analyzeSelectedCompanyPdfs() {
        if (selectedCompanyPdfUris.size() != 2) {
            Toast.makeText(
                    this,
                    "Select both official PDFs first",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        setPdfAnalysisBusy(
                true
        );

        Uri firstUri =
                selectedCompanyPdfUris.get(0);

        Uri secondUri =
                selectedCompanyPdfUris.get(1);

        String firstName =
                selectedCompanyPdfNames.size() > 0
                        ? selectedCompanyPdfNames.get(0)
                        : "PDF 1";

        String secondName =
                selectedCompanyPdfNames.size() > 1
                        ? selectedCompanyPdfNames.get(1)
                        : "PDF 2";

        pdfExecutor.execute(
                () -> {
                    try {
                        CompanyPriceDocument firstDocument =
                                CompanyPricePdfParser.parse(
                                        this,
                                        firstUri,
                                        firstName
                                );

                        CompanyPriceDocument secondDocument =
                                CompanyPricePdfParser.parse(
                                        this,
                                        secondUri,
                                        secondName
                                );

                        PdfValidationResult validationResult =
                                validatePdfPair(
                                        firstDocument,
                                        secondDocument
                                );

                        runOnUiThread(
                                () -> showPdfValidationResult(
                                        validationResult
                                )
                        );

                    } catch (Exception exception) {
                        runOnUiThread(
                                () -> showPdfAnalysisFailure(
                                        exception
                                )
                        );
                    }
                }
        );
    }

    private PdfValidationResult validatePdfPair(
            CompanyPriceDocument first,
            CompanyPriceDocument second
    ) {
        PdfValidationResult result =
                new PdfValidationResult();

        result.firstDocument = first;
        result.secondDocument = second;

        CompanyPriceDocument associate = null;
        CompanyPriceDocument preferred = null;

        for (CompanyPriceDocument document :
                new CompanyPriceDocument[]{
                        first,
                        second
                }) {
            if (document == null) {
                continue;
            }

            if (document.getDocumentType()
                    == CompanyPriceDocument.DocumentType.ASSOCIATE) {
                if (associate != null) {
                    result.errors.add(
                            "Both selected files appear to be Associate price lists."
                    );
                }

                associate = document;

            } else if (document.getDocumentType()
                    == CompanyPriceDocument.DocumentType.PREFERRED_CUSTOMER) {
                if (preferred != null) {
                    result.errors.add(
                            "Both selected files appear to be Preferred Customer price lists."
                    );
                }

                preferred = document;

            } else {
                result.errors.add(
                        displayName(document)
                                + " is not recognized as an official price list."
                );
            }
        }

        result.associateDocument = associate;
        result.preferredDocument = preferred;

        if (associate == null) {
            result.errors.add(
                    "Associate price list is missing."
            );
        }

        if (preferred == null) {
            result.errors.add(
                    "Preferred Customer price list is missing."
            );
        }

        if (associate == null
                || preferred == null) {
            return result;
        }

        String associateDate =
                cleanText(
                        associate.getEffectiveDate()
                );

        String preferredDate =
                cleanText(
                        preferred.getEffectiveDate()
                );

        result.effectiveDate =
                !associateDate.isEmpty()
                        ? associateDate
                        : preferredDate;

        if (associateDate.isEmpty()
                || preferredDate.isEmpty()) {
            result.errors.add(
                    "Effective Date could not be confirmed in both PDFs."
            );

        } else if (!normalizeDateKey(
                associateDate
        ).equals(
                normalizeDateKey(
                        preferredDate
                )
        )) {
            result.errors.add(
                    "The two PDFs have different Effective Dates."
            );
        }

        if (associate.getRows().isEmpty()) {
            result.errors.add(
                    "No Associate product rows were detected."
            );
        }

        if (preferred.getRows().isEmpty()) {
            result.errors.add(
                    "No Preferred Customer product rows were detected."
            );
        }

        CrossCheckResult crossCheckResult =
                crossCheckSharedPrices(
                        associate,
                        preferred
                );

        result.commonStockRows =
                crossCheckResult.commonStockRows;

        result.sharedPriceChecks =
                crossCheckResult.sharedPriceChecks;

        result.sharedPriceConflicts =
                crossCheckResult.sharedPriceConflicts;

        if (crossCheckResult.commonStockRows == 0) {
            result.warnings.add(
                    "No common Stock No. was available for cross-checking."
            );
        }

        if (crossCheckResult.sharedPriceConflicts > 0) {
            result.errors.add(
                    crossCheckResult.sharedPriceConflicts
                            + " shared 25%/35% price conflict(s) detected."
            );
        }

        result.warnings.addAll(
                associate.getWarnings()
        );

        result.warnings.addAll(
                preferred.getWarnings()
        );

        result.valid =
                result.errors.isEmpty()
                        && associate.isRecognizedOfficialPriceList()
                        && preferred.isRecognizedOfficialPriceList();

        return result;
    }

    private CrossCheckResult crossCheckSharedPrices(
            CompanyPriceDocument associate,
            CompanyPriceDocument preferred
    ) {
        CrossCheckResult result =
                new CrossCheckResult();

        Map<String, CompanyPriceRow> associateByStock =
                new HashMap<>();

        for (CompanyPriceRow row :
                associate.getRows()) {
            String stockNo =
                    normalizeStockNo(
                            row.getStockNo()
                    );

            if (!stockNo.isEmpty()) {
                associateByStock.put(
                        stockNo,
                        row
                );
            }
        }

        for (CompanyPriceRow preferredRow :
                preferred.getRows()) {
            String stockNo =
                    normalizeStockNo(
                            preferredRow.getStockNo()
                    );

            if (stockNo.isEmpty()) {
                continue;
            }

            CompanyPriceRow associateRow =
                    associateByStock.get(
                            stockNo
                    );

            if (associateRow == null) {
                continue;
            }

            result.commonStockRows++;

            if (associateRow.getPrice25() != null
                    && preferredRow.getPrice25() != null) {
                result.sharedPriceChecks++;

                if (!associateRow.getPrice25().equals(
                        preferredRow.getPrice25()
                )) {
                    result.sharedPriceConflicts++;
                }
            }

            if (associateRow.getPrice35() != null
                    && preferredRow.getPrice35() != null) {
                result.sharedPriceChecks++;

                if (!associateRow.getPrice35().equals(
                        preferredRow.getPrice35()
                )) {
                    result.sharedPriceConflicts++;
                }
            }
        }

        return result;
    }

    private void showPdfValidationResult(
            PdfValidationResult result
    ) {
        setPdfAnalysisBusy(
                false
        );

        associateDocument =
                result.associateDocument;

        preferredCustomerDocument =
                result.preferredDocument;

        pdfAnalysisCard.setVisibility(
                View.VISIBLE
        );

        if (associateDocument != null) {
            associatePdfStatusText.setText(
                    displayName(associateDocument)
                            + "\n"
                            + associateDocument.getRows().size()
                            + " rows"
            );

        } else {
            associatePdfStatusText.setText(
                    "Not detected"
            );
        }

        if (preferredCustomerDocument != null) {
            preferredPdfStatusText.setText(
                    displayName(preferredCustomerDocument)
                            + "\n"
                            + preferredCustomerDocument.getRows().size()
                            + " rows"
            );

        } else {
            preferredPdfStatusText.setText(
                    "Not detected"
            );
        }

        pdfEffectiveDateText.setText(
                "Effective Date: "
                        + (result.effectiveDate.isEmpty()
                        ? "Not confirmed"
                        : result.effectiveDate)
        );

        if (result.valid) {
            pdfCrosscheckText.setText(
                    "Validated • Associate "
                            + associateDocument.getRows().size()
                            + " rows • Preferred "
                            + preferredCustomerDocument.getRows().size()
                            + " rows • "
                            + result.sharedPriceChecks
                            + " shared price checks • 0 conflicts"
            );

            pdfSelectionSummaryText.setText(
                    "Official PDF pair validated successfully"
            );

            Toast.makeText(
                    this,
                    "Both official PDFs validated",
                    Toast.LENGTH_LONG
            ).show();

        } else {
            pdfCrosscheckText.setText(
                    "Validation stopped • "
                            + result.errors.size()
                            + " issue(s) found"
            );

            pdfSelectionSummaryText.setText(
                    "PDF validation needs attention"
            );
        }

        List<String> messages =
                new ArrayList<>();

        messages.addAll(
                result.errors
        );

        messages.addAll(
                result.warnings
        );

        if (messages.isEmpty()) {
            pdfWarningsText.setText("");
            pdfWarningsText.setVisibility(
                    View.GONE
            );

        } else {
            pdfWarningsText.setText(
                    buildBulletText(messages)
            );

            pdfWarningsText.setVisibility(
                    View.VISIBLE
            );
        }
    }

    private void showPdfAnalysisFailure(
            Exception exception
    ) {
        setPdfAnalysisBusy(
                false
        );

        associateDocument = null;
        preferredCustomerDocument = null;

        pdfAnalysisCard.setVisibility(
                View.VISIBLE
        );

        associatePdfStatusText.setText(
                "Could not validate"
        );

        preferredPdfStatusText.setText(
                "Could not validate"
        );

        pdfEffectiveDateText.setText(
                "Effective Date: Not confirmed"
        );

        pdfCrosscheckText.setText(
                "PDF analysis failed"
        );

        String message =
                exception == null
                        || exception.getMessage() == null
                        || exception.getMessage().trim().isEmpty()
                        ? "The selected PDFs could not be read."
                        : exception.getMessage().trim();

        pdfWarningsText.setText(
                message
        );

        pdfWarningsText.setVisibility(
                View.VISIBLE
        );

        Toast.makeText(
                this,
                "PDF analysis failed",
                Toast.LENGTH_LONG
        ).show();
    }

    private void setPdfAnalysisBusy(
            boolean busy
    ) {
        selectCompanyPdfsButton.setEnabled(
                !busy
        );

        analyzeCompanyPdfsButton.setEnabled(
                !busy
                        && selectedCompanyPdfUris.size() == 2
        );

        pdfAnalysisProgress.setVisibility(
                busy
                        ? View.VISIBLE
                        : View.GONE
        );

        if (busy) {
            pdfSelectionSummaryText.setText(
                    "Reading and validating official PDFs…"
            );

            associatePdfStatusText.setText(
                    "Analyzing…"
            );

            preferredPdfStatusText.setText(
                    "Analyzing…"
            );
        }
    }

    private String getDisplayName(
            Uri uri
    ) {
        if (uri == null) {
            return "Selected PDF";
        }

        try (
                Cursor cursor =
                        getContentResolver().query(
                                uri,
                                new String[]{
                                        OpenableColumns.DISPLAY_NAME
                                },
                                null,
                                null,
                                null
                        )
        ) {
            if (cursor != null
                    && cursor.moveToFirst()) {
                int columnIndex =
                        cursor.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                        );

                if (columnIndex >= 0) {
                    String value =
                            cursor.getString(
                                    columnIndex
                            );

                    if (value != null
                            && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }

        } catch (Exception ignored) {
            // Fall through to URI based name.
        }

        String lastSegment =
                uri.getLastPathSegment();

        return lastSegment == null
                || lastSegment.trim().isEmpty()
                ? "Selected PDF"
                : lastSegment.trim();
    }

    private String displayName(
            @Nullable CompanyPriceDocument document
    ) {
        if (document == null) {
            return "PDF";
        }

        String sourceName =
                cleanText(
                        document.getSourceName()
                );

        return sourceName.isEmpty()
                ? "PDF"
                : sourceName;
    }

    private String normalizeDateKey(
            String value
    ) {
        return cleanText(value)
                .toLowerCase(Locale.US)
                .replaceAll(
                        "(?<=\\d)(st|nd|rd|th)\\b",
                        ""
                )
                .replaceAll(
                        "[^a-z0-9]+",
                        ""
                );
    }

    private String normalizeStockNo(
            String value
    ) {
        return cleanText(value)
                .toUpperCase(Locale.US)
                .replaceAll(
                        "[^A-Z0-9]",
                        ""
                );
    }

    private String cleanText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private String buildBulletText(
            List<String> messages
    ) {
        StringBuilder builder =
                new StringBuilder();

        for (String message : messages) {
            String cleanMessage =
                    cleanText(message);

            if (cleanMessage.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append('\n');
            }

            builder.append("• ")
                    .append(cleanMessage);
        }

        return builder.toString();
    }

    private void confirmScalePrices() {
        double percent =
                parse(
                        changePercent,
                        0d
                );

        if (percent == 0d) {
            Toast.makeText(
                    this,
                    "Enter a non-zero percentage",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String category =
                selectedCategory();

        String direction =
                percent > 0
                        ? "increase"
                        : "decrease";

        String message =
                "This will "
                        + direction
                        + " saved prices by "
                        + Math.abs(percent)
                        + "% for "
                        + category
                        + ".";

        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        "Apply company price revision?"
                )
                .setMessage(
                        message
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Apply",
                        (dialog, which) -> {
                            int updated =
                                    db.scalePrices(
                                            category,
                                            percent,
                                            includeFullPrice.isChecked(),
                                            selectedRounding()
                                    );

                            Toast.makeText(
                                    this,
                                    updated
                                            + " products updated",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                )
                .show();
    }

    private void confirmRecalculate() {
        double d15 =
                parse(
                        discount15,
                        15d
                );

        double d25 =
                parse(
                        discount25,
                        25d
                );

        double d35 =
                parse(
                        discount35,
                        35d
                );

        double d42 =
                parse(
                        discount42,
                        42d
                );

        double d50 =
                parse(
                        discount50,
                        50d
                );

        if (!validDiscount(d15)
                || !validDiscount(d25)
                || !validDiscount(d35)
                || !validDiscount(d42)
                || !validDiscount(d50)) {
            Toast.makeText(
                    this,
                    "Discount values must be between 0 and 100",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        "Recalculate discount prices?"
                )
                .setMessage(
                        "All selected discount prices will be calculated from Full Price. "
                                + "Official PDF values should be preferred when available. "
                                + "You can undo this bulk update."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Recalculate",
                        (dialog, which) -> {
                            int updated =
                                    db.recalculateDiscounts(
                                            selectedCategory(),
                                            d15,
                                            d25,
                                            d35,
                                            d42,
                                            d50,
                                            selectedRounding()
                                    );

                            Toast.makeText(
                                    this,
                                    updated
                                            + " products recalculated",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                )
                .show();
    }

    private void confirmUndo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(
                        "Undo latest bulk update?"
                )
                .setMessage(
                        "Prices will return to the snapshot saved immediately before the most recent bulk revision."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Undo",
                        (dialog, which) -> {
                            int restored =
                                    db.undoLastBulkOperation();

                            Toast.makeText(
                                    this,
                                    restored > 0
                                            ? restored
                                            + " products restored"
                                            : "No bulk update available to undo",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                )
                .show();
    }

    private String selectedCategory() {
        String value =
                categoryDropdown
                        .getText()
                        .toString()
                        .trim();

        return value.isEmpty()
                ? getString(
                R.string.all_categories
        )
                : value;
    }

    private int selectedRounding() {
        String value =
                roundingDropdown
                        .getText()
                        .toString();

        if (value.contains("10")) {
            return 10;
        }

        if (value.contains("5")) {
            return 5;
        }

        return 1;
    }

    private boolean validDiscount(
            double value
    ) {
        return value >= 0d
                && value <= 100d;
    }

    private double parse(
            TextInputEditText input,
            double fallback
    ) {
        try {
            if (input.getText() == null
                    || input.getText()
                    .toString()
                    .trim()
                    .isEmpty()) {
                return fallback;
            }

            return Double.parseDouble(
                    input.getText()
                            .toString()
                            .trim()
            );

        } catch (Exception exception) {
            return fallback;
        }
    }

    private static class CrossCheckResult {
        private int commonStockRows;
        private int sharedPriceChecks;
        private int sharedPriceConflicts;
    }

    private static class PdfValidationResult {
        private CompanyPriceDocument firstDocument;
        private CompanyPriceDocument secondDocument;
        private CompanyPriceDocument associateDocument;
        private CompanyPriceDocument preferredDocument;

        private String effectiveDate = "";

        private int commonStockRows;
        private int sharedPriceChecks;
        private int sharedPriceConflicts;

        private boolean valid;

        private final List<String> errors =
                new ArrayList<>();

        private final List<String> warnings =
                new ArrayList<>();
    }
}
