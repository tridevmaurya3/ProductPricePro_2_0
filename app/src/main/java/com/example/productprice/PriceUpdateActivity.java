package com.example.productprice;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.productprice.data.CompanyProductMappingStore;
import com.example.productprice.data.OfficialCatalogSyncRepository;
import com.example.productprice.data.OfficialPriceImportRepository;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.CompanyPriceRow;
import com.example.productprice.model.CompanyProductMapping;
import com.example.productprice.model.OfficialCatalogCandidate;
import com.example.productprice.model.OfficialCatalogSyncPlan;
import com.example.productprice.model.OfficialPriceUpdate;
import com.example.productprice.model.Product;
import com.example.productprice.model.SmartPriceImportPlan;
import com.example.productprice.util.CompanyPricePdfParser;
import com.example.productprice.util.OfficialCatalogDetector;
import com.example.productprice.util.PriceChangeIntelligence;
import com.example.productprice.util.SmartCompanyPriceMatcher;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PriceUpdateActivity extends AppCompatActivity {

    private ProductDbHelper db;
    private OfficialPriceImportRepository officialPriceImportRepository;
    private OfficialCatalogSyncRepository catalogSyncRepository;
    private CompanyProductMappingStore mappingStore;

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
    private MaterialButton mapUnmatchedButton;
    private MaterialButton syncNewCatalogButton;

    private TextView pdfSelectionSummaryText;
    private TextView associatePdfStatusText;
    private TextView preferredPdfStatusText;
    private TextView pdfEffectiveDateText;
    private TextView pdfCrosscheckText;
    private TextView pdfWarningsText;

    private MaterialCardView pdfAnalysisCard;
    private LinearProgressIndicator pdfAnalysisProgress;

    private final List<Uri> selectedCompanyPdfUris = new ArrayList<>();
    private final List<String> selectedCompanyPdfNames = new ArrayList<>();
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();

    private CompanyPriceDocument associateDocument;
    private CompanyPriceDocument preferredCustomerDocument;
    private SmartPriceImportPlan latestImportPlan;
    private OfficialCatalogSyncPlan latestCatalogPlan;
    private PdfValidationResult latestValidationResult;

    private final ActivityResultLauncher<String[]> pdfPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenMultipleDocuments(),
                    this::handleSelectedPdfUris
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_update);

        db = ProductDbHelper.getInstance(this);
        db.initialize();
        officialPriceImportRepository = new OfficialPriceImportRepository(db);
        catalogSyncRepository = new OfficialCatalogSyncRepository(db);
        mappingStore = new CompanyProductMappingStore(this);

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
        MaterialToolbar toolbar = findViewById(R.id.toolbar_price_update);
        toolbar.setNavigationOnClickListener(view -> finish());

        categoryDropdown = findViewById(R.id.dropdown_update_category);
        roundingDropdown = findViewById(R.id.dropdown_rounding);
        changePercent = findViewById(R.id.input_price_change_percent);
        discount15 = findViewById(R.id.input_discount_15);
        discount25 = findViewById(R.id.input_discount_25);
        discount35 = findViewById(R.id.input_discount_35);
        discount42 = findViewById(R.id.input_discount_42);
        discount50 = findViewById(R.id.input_discount_50);
        includeFullPrice = findViewById(R.id.check_update_full_price);

        selectCompanyPdfsButton = findViewById(R.id.button_select_company_pdfs);
        analyzeCompanyPdfsButton = findViewById(R.id.button_analyze_company_pdfs);
        pdfSelectionSummaryText = findViewById(R.id.text_pdf_selection_summary);
        associatePdfStatusText = findViewById(R.id.text_associate_pdf_status);
        preferredPdfStatusText = findViewById(R.id.text_preferred_pdf_status);
        pdfEffectiveDateText = findViewById(R.id.text_pdf_effective_date);
        pdfCrosscheckText = findViewById(R.id.text_pdf_crosscheck);
        pdfWarningsText = findViewById(R.id.text_pdf_analysis_warnings);
        pdfAnalysisCard = findViewById(R.id.card_pdf_analysis);
        pdfAnalysisProgress = findViewById(R.id.progress_pdf_analysis);

        createSmartActionButtons();
    }

    private void createSmartActionButtons() {
        ViewGroup parent = pdfWarningsText.getParent() instanceof ViewGroup
                ? (ViewGroup) pdfWarningsText.getParent()
                : null;

        if (parent == null) return;

        syncNewCatalogButton = new MaterialButton(this);
        syncNewCatalogButton.setText("Review New Official Products");
        syncNewCatalogButton.setAllCaps(false);
        syncNewCatalogButton.setVisibility(View.GONE);
        syncNewCatalogButton.setContentDescription(
                "Review products or categories found in the official PDFs but missing from the app"
        );
        LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        syncParams.topMargin = dp(8);
        syncNewCatalogButton.setLayoutParams(syncParams);
        parent.addView(syncNewCatalogButton);

        mapUnmatchedButton = new MaterialButton(this);
        mapUnmatchedButton.setText("Link Unmatched Products");
        mapUnmatchedButton.setAllCaps(false);
        mapUnmatchedButton.setVisibility(View.GONE);
        mapUnmatchedButton.setContentDescription(
                "Link unmatched app products to official company products"
        );
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        mapParams.topMargin = dp(8);
        mapUnmatchedButton.setLayoutParams(mapParams);
        parent.addView(mapUnmatchedButton);
    }

    private void setupDropdowns() {
        refreshCategoryDropdown();

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
        roundingDropdown.setText(rounding[0], false);
    }

    private void refreshCategoryDropdown() {
        List<String> categories = new ArrayList<>();
        categories.add(getString(R.string.all_categories));
        categories.addAll(db.getCategories());

        categoryDropdown.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        categories
                )
        );

        String current = categoryDropdown.getText() == null
                ? ""
                : categoryDropdown.getText().toString().trim();

        if (current.isEmpty() || !containsIgnoreCase(categories, current)) {
            categoryDropdown.setText(categories.get(0), false);
        }
    }

    private void setupActions() {
        selectCompanyPdfsButton.setOnClickListener(
                view -> selectOfficialCompanyPdfs()
        );

        analyzeCompanyPdfsButton.setOnClickListener(
                view -> {
                    if (latestImportPlan != null
                            && latestImportPlan.isSafeToApply()
                            && latestImportPlan.getChangedCount() > 0) {
                        showImportPreviewDialog();
                    } else {
                        analyzeSelectedCompanyPdfs();
                    }
                }
        );

        if (syncNewCatalogButton != null) {
            syncNewCatalogButton.setOnClickListener(
                    view -> showCatalogSyncDialog()
            );
        }

        if (mapUnmatchedButton != null) {
            mapUnmatchedButton.setOnClickListener(
                    view -> showUnmatchedProductDialog()
            );
        }

        findViewById(R.id.button_apply_price_change).setOnClickListener(
                view -> confirmScalePrices()
        );

        findViewById(R.id.button_recalculate_discounts).setOnClickListener(
                view -> confirmRecalculate()
        );

        findViewById(R.id.button_undo_bulk).setOnClickListener(
                view -> confirmUndo()
        );
    }

    private void selectOfficialCompanyPdfs() {
        pdfPickerLauncher.launch(new String[]{"application/pdf"});
    }

    private void handleSelectedPdfUris(List<Uri> uris) {
        selectedCompanyPdfUris.clear();
        selectedCompanyPdfNames.clear();
        associateDocument = null;
        preferredCustomerDocument = null;
        latestImportPlan = null;
        latestCatalogPlan = null;
        latestValidationResult = null;
        pdfAnalysisCard.setVisibility(View.GONE);
        setMappingButtonVisible(false, 0);
        setCatalogButtonVisible(false, 0, 0);

        if (uris == null || uris.isEmpty()) {
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
            if (uri == null) continue;

            selectedCompanyPdfUris.add(uri);
            selectedCompanyPdfNames.add(getDisplayName(uri));

            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
            }
        }

        if (selectedCompanyPdfUris.size() != 2) {
            resetPdfImportState();
            return;
        }

        pdfSelectionSummaryText.setText("2 PDFs selected • ready to analyze");
        associatePdfStatusText.setText("Detecting automatically");
        preferredPdfStatusText.setText("Detecting automatically");
        analyzeCompanyPdfsButton.setText("Analyze & Validate PDFs");
        analyzeCompanyPdfsButton.setEnabled(true);
    }

    private void resetPdfImportState() {
        associateDocument = null;
        preferredCustomerDocument = null;
        latestImportPlan = null;
        latestCatalogPlan = null;
        latestValidationResult = null;
        selectedCompanyPdfUris.clear();
        selectedCompanyPdfNames.clear();

        pdfSelectionSummaryText.setText("No PDFs selected");
        associatePdfStatusText.setText("Waiting for PDF");
        preferredPdfStatusText.setText("Waiting for PDF");
        analyzeCompanyPdfsButton.setText("Analyze & Validate PDFs");
        analyzeCompanyPdfsButton.setEnabled(false);
        pdfAnalysisProgress.setVisibility(View.GONE);
        pdfAnalysisCard.setVisibility(View.GONE);
        setMappingButtonVisible(false, 0);
        setCatalogButtonVisible(false, 0, 0);
    }

    private void analyzeSelectedCompanyPdfs() {
        if (selectedCompanyPdfUris.size() != 2) {
            Toast.makeText(this, "Select both official PDFs first", Toast.LENGTH_SHORT).show();
            return;
        }

        latestImportPlan = null;
        latestCatalogPlan = null;
        latestValidationResult = null;
        setMappingButtonVisible(false, 0);
        setCatalogButtonVisible(false, 0, 0);
        setPdfAnalysisBusy(true);

        Uri firstUri = selectedCompanyPdfUris.get(0);
        Uri secondUri = selectedCompanyPdfUris.get(1);
        String firstName = selectedCompanyPdfNames.size() > 0
                ? selectedCompanyPdfNames.get(0)
                : "PDF 1";
        String secondName = selectedCompanyPdfNames.size() > 1
                ? selectedCompanyPdfNames.get(1)
                : "PDF 2";

        pdfExecutor.execute(() -> {
            try {
                CompanyPriceDocument firstDocument = CompanyPricePdfParser.parse(
                        this,
                        firstUri,
                        firstName
                );
                CompanyPriceDocument secondDocument = CompanyPricePdfParser.parse(
                        this,
                        secondUri,
                        secondName
                );

                PdfValidationResult validationResult = validatePdfPair(
                        firstDocument,
                        secondDocument
                );

                if (validationResult.valid
                        && validationResult.associateDocument != null
                        && validationResult.preferredDocument != null) {
                    buildPlans(validationResult);
                }

                runOnUiThread(() -> showPdfValidationResult(validationResult));

            } catch (Exception exception) {
                runOnUiThread(() -> showPdfAnalysisFailure(exception));
            }
        });
    }

    private void buildPlans(PdfValidationResult validationResult) {
        List<Product> currentProducts = db.getAllProducts(true);

        validationResult.importPlan = SmartCompanyPriceMatcher.buildPlan(
                currentProducts,
                validationResult.associateDocument,
                validationResult.preferredDocument,
                validationResult.effectiveDate,
                mappingStore.getAllMappings()
        );

        validationResult.catalogPlan = OfficialCatalogDetector.buildPlan(
                currentProducts,
                db.getCategories(),
                validationResult.associateDocument,
                validationResult.preferredDocument,
                validationResult.effectiveDate
        );
    }

    private PdfValidationResult validatePdfPair(
            CompanyPriceDocument first,
            CompanyPriceDocument second
    ) {
        PdfValidationResult result = new PdfValidationResult();
        CompanyPriceDocument associate = null;
        CompanyPriceDocument preferred = null;

        for (CompanyPriceDocument document : new CompanyPriceDocument[]{first, second}) {
            if (document == null) continue;

            if (document.getDocumentType() == CompanyPriceDocument.DocumentType.ASSOCIATE) {
                if (associate != null) {
                    result.errors.add("Both selected files appear to be Associate price lists.");
                }
                associate = document;
            } else if (document.getDocumentType()
                    == CompanyPriceDocument.DocumentType.PREFERRED_CUSTOMER) {
                if (preferred != null) {
                    result.errors.add("Both selected files appear to be Preferred Customer price lists.");
                }
                preferred = document;
            } else {
                result.errors.add(displayName(document) + " is not recognized as an official price list.");
            }
        }

        result.associateDocument = associate;
        result.preferredDocument = preferred;

        if (associate == null) result.errors.add("Associate price list is missing.");
        if (preferred == null) result.errors.add("Preferred Customer price list is missing.");
        if (associate == null || preferred == null) return result;

        String associateDate = cleanText(associate.getEffectiveDate());
        String preferredDate = cleanText(preferred.getEffectiveDate());
        result.effectiveDate = !associateDate.isEmpty() ? associateDate : preferredDate;

        if (associateDate.isEmpty() || preferredDate.isEmpty()) {
            result.errors.add("Effective Date could not be confirmed in both PDFs.");
        } else if (!normalizeDateKey(associateDate).equals(normalizeDateKey(preferredDate))) {
            result.errors.add("The two PDFs have different Effective Dates.");
        }

        if (associate.getRows().isEmpty()) result.errors.add("No Associate product rows were detected.");
        if (preferred.getRows().isEmpty()) result.errors.add("No Preferred Customer product rows were detected.");

        CrossCheckResult crossCheckResult = crossCheckSharedPrices(associate, preferred);
        result.commonStockRows = crossCheckResult.commonStockRows;
        result.sharedPriceChecks = crossCheckResult.sharedPriceChecks;
        result.sharedPriceConflicts = crossCheckResult.sharedPriceConflicts;

        if (crossCheckResult.commonStockRows == 0) {
            result.warnings.add("No common Stock No. was available for cross-checking.");
        }
        if (crossCheckResult.sharedPriceConflicts > 0) {
            result.errors.add(
                    crossCheckResult.sharedPriceConflicts
                            + " shared 25%/35% price conflict(s) detected."
            );
        }

        result.warnings.addAll(associate.getWarnings());
        result.warnings.addAll(preferred.getWarnings());
        result.valid = result.errors.isEmpty()
                && associate.isRecognizedOfficialPriceList()
                && preferred.isRecognizedOfficialPriceList();
        return result;
    }

    private CrossCheckResult crossCheckSharedPrices(
            CompanyPriceDocument associate,
            CompanyPriceDocument preferred
    ) {
        CrossCheckResult result = new CrossCheckResult();
        Map<String, CompanyPriceRow> associateByStock = new HashMap<>();

        for (CompanyPriceRow row : associate.getRows()) {
            String stockNo = normalizeStockNo(row.getStockNo());
            if (!stockNo.isEmpty()) associateByStock.put(stockNo, row);
        }

        for (CompanyPriceRow preferredRow : preferred.getRows()) {
            String stockNo = normalizeStockNo(preferredRow.getStockNo());
            if (stockNo.isEmpty()) continue;

            CompanyPriceRow associateRow = associateByStock.get(stockNo);
            if (associateRow == null) continue;

            result.commonStockRows++;

            if (associateRow.getPrice25() != null && preferredRow.getPrice25() != null) {
                result.sharedPriceChecks++;
                if (!associateRow.getPrice25().equals(preferredRow.getPrice25())) {
                    result.sharedPriceConflicts++;
                }
            }

            if (associateRow.getPrice35() != null && preferredRow.getPrice35() != null) {
                result.sharedPriceChecks++;
                if (!associateRow.getPrice35().equals(preferredRow.getPrice35())) {
                    result.sharedPriceConflicts++;
                }
            }
        }

        return result;
    }

    private void showPdfValidationResult(PdfValidationResult result) {
        setPdfAnalysisBusy(false);

        latestValidationResult = result;
        associateDocument = result.associateDocument;
        preferredCustomerDocument = result.preferredDocument;
        latestImportPlan = result.importPlan;
        latestCatalogPlan = result.catalogPlan;
        pdfAnalysisCard.setVisibility(View.VISIBLE);

        associatePdfStatusText.setText(
                associateDocument == null
                        ? "Not detected"
                        : displayName(associateDocument) + "\n" + associateDocument.getRows().size() + " rows"
        );
        preferredPdfStatusText.setText(
                preferredCustomerDocument == null
                        ? "Not detected"
                        : displayName(preferredCustomerDocument) + "\n"
                        + preferredCustomerDocument.getRows().size() + " rows"
        );
        pdfEffectiveDateText.setText(
                "Effective Date: "
                        + (result.effectiveDate.isEmpty() ? "Not confirmed" : result.effectiveDate)
        );

        List<String> messages = new ArrayList<>();
        messages.addAll(result.errors);
        messages.addAll(result.warnings);

        if (result.valid && latestImportPlan != null) {
            int matched = latestImportPlan.getMatchedCount();
            int changed = latestImportPlan.getChangedCount();
            int unchanged = latestImportPlan.getUnchangedCount();
            int unmatched = latestImportPlan.getUnmatchedProducts().size();
            int matchingConflicts = latestImportPlan.getConflicts().size();
            int rememberedMappings = mappingStore.getCount();
            int newGroups = latestCatalogPlan == null ? 0 : latestCatalogPlan.getMissingGroupCount();
            int newVariants = latestCatalogPlan == null ? 0 : latestCatalogPlan.getOfficialVariantCount();
            int newCategories = latestCatalogPlan == null ? 0 : latestCatalogPlan.getNewCategories().size();

            PriceChangeIntelligence.Summary intelligence =
                    PriceChangeIntelligence.summarize(latestImportPlan.getMatchedUpdates());

            String rememberedText = rememberedMappings > 0
                    ? " • " + rememberedMappings + " remembered mapping(s)"
                    : "";
            String catalogText = newGroups > 0
                    ? " • " + newVariants + " official new/missing product(s) in "
                    + newGroups + " group(s)"
                    : "";

            pdfCrosscheckText.setText(
                    "Validated • " + result.sharedPriceChecks + " PDF cross-checks • "
                            + matched + " app products matched • "
                            + changed + " price changes • "
                            + unchanged + " unchanged • "
                            + unmatched + " unmatched"
                            + rememberedText
                            + catalogText
            );

            if (changed > 0) {
                messages.add(
                        "Price intelligence: "
                                + intelligence.getIncreasedProducts() + " increased • "
                                + intelligence.getDecreasedProducts() + " decreased • "
                                + intelligence.getTierOnlyChanges() + " tier-only change(s) • average Full Price change "
                                + formatPercent(intelligence.getAverageFullPricePercent())
                );
            }

            if (newGroups > 0) {
                messages.add(
                        newVariants + " official product variant(s) are not yet represented in the app. "
                                + newCategories + " new category/categories would be created automatically if needed."
                );
                addCatalogPreviewMessages(messages, latestCatalogPlan);
            }

            if (matchingConflicts > 0) {
                messages.addAll(latestImportPlan.getConflicts());
                pdfSelectionSummaryText.setText("PDFs valid • product matching needs attention");
                analyzeCompanyPdfsButton.setText("Re-analyze PDFs");
                analyzeCompanyPdfsButton.setEnabled(true);
                setMappingButtonVisible(false, 0);
            } else if (changed > 0) {
                pdfSelectionSummaryText.setText(changed + " safe price update(s) ready for review");
                analyzeCompanyPdfsButton.setText("Review & Apply " + changed + " Updates");
                analyzeCompanyPdfsButton.setEnabled(true);
                setMappingButtonVisible(unmatched > 0, unmatched);
            } else {
                pdfSelectionSummaryText.setText(
                        newGroups > 0
                                ? "Prices checked • new official products detected"
                                : unmatched > 0
                                ? "Matched prices are current • link remaining products"
                                : "Matched prices are already up to date"
                );
                analyzeCompanyPdfsButton.setText("Prices Already Up to Date");
                analyzeCompanyPdfsButton.setEnabled(false);
                setMappingButtonVisible(unmatched > 0, unmatched);
            }

            setCatalogButtonVisible(newGroups > 0, newGroups, newVariants);

            if (unmatched > 0) {
                messages.add(
                        unmatched + " app product(s) need a one-time link. Once linked, the app will remember that mapping for future yearly PDFs."
                );
            }

            Toast.makeText(this, "Official PDFs validated and catalogue checked", Toast.LENGTH_LONG).show();
        } else {
            pdfCrosscheckText.setText(
                    "Validation stopped • " + result.errors.size() + " issue(s) found"
            );
            pdfSelectionSummaryText.setText("PDF validation needs attention");
            analyzeCompanyPdfsButton.setText("Re-analyze PDFs");
            analyzeCompanyPdfsButton.setEnabled(true);
            setMappingButtonVisible(false, 0);
            setCatalogButtonVisible(false, 0, 0);
        }

        if (messages.isEmpty()) {
            pdfWarningsText.setText("");
            pdfWarningsText.setVisibility(View.GONE);
        } else {
            pdfWarningsText.setText(buildBulletText(messages));
            pdfWarningsText.setVisibility(View.VISIBLE);
        }
    }

    private void addCatalogPreviewMessages(
            List<String> messages,
            OfficialCatalogSyncPlan plan
    ) {
        if (plan == null) return;
        int shown = 0;
        for (OfficialCatalogCandidate candidate : plan.getMissingProducts()) {
            if (shown >= 5) break;
            messages.add(
                    "New/Missing: " + candidate.getLogicalName()
                            + " • " + candidate.getCategory()
                            + " • " + candidate.getVariantCount() + " official variant(s)"
                            + " • Full " + formatRupees(candidate.getFullPrice())
                            + " • @15 " + formatRupees(candidate.getPrice15())
                            + " • @25 " + formatRupees(candidate.getPrice25())
                            + " • @35 " + formatRupees(candidate.getPrice35())
                            + " • @42 " + formatRupees(candidate.getPrice42())
                            + " • @50 " + formatRupees(candidate.getPrice50())
            );
            shown++;
        }
    }

    private void showCatalogSyncDialog() {
        OfficialCatalogSyncPlan plan = latestCatalogPlan;
        if (plan == null || !plan.hasChanges()) {
            Toast.makeText(this, "No new official products detected", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("Effective Date: ")
                .append(plan.getEffectiveDate().isEmpty() ? "—" : plan.getEffectiveDate())
                .append("\n\nDetected ")
                .append(plan.getOfficialVariantCount())
                .append(" official product variant(s) in ")
                .append(plan.getMissingGroupCount())
                .append(" new/missing logical group(s).\n");

        if (!plan.getNewCategories().isEmpty()) {
            message.append("\nNEW CATEGORIES\n");
            for (String category : plan.getNewCategories()) {
                message.append("• ").append(category).append('\n');
            }
        }

        message.append("\nPRODUCTS\n");
        for (OfficialCatalogCandidate candidate : plan.getMissingProducts()) {
            message.append("\n• ")
                    .append(candidate.getLogicalName())
                    .append("\n  ")
                    .append(candidate.getCategory())
                    .append(" • ")
                    .append(candidate.getVariantCount())
                    .append(" official variant(s)")
                    .append("\n  Full ")
                    .append(formatRupees(candidate.getFullPrice()))
                    .append(" | @15 ")
                    .append(formatRupees(candidate.getPrice15()))
                    .append(" | @25 ")
                    .append(formatRupees(candidate.getPrice25()))
                    .append("\n  @35 ")
                    .append(formatRupees(candidate.getPrice35()))
                    .append(" | @42 ")
                    .append(formatRupees(candidate.getPrice42()))
                    .append(" | @50 ")
                    .append(formatRupees(candidate.getPrice50()));

            if (candidate.getVariantCount() > 1) {
                for (OfficialCatalogCandidate.Variant variant : candidate.getVariants()) {
                    message.append("\n    - ")
                            .append(variant.getStockNo())
                            .append("  ")
                            .append(variant.getProductName());
                }
            }
        }

        message.append(
                "\n\nSmart Groups keeps your existing simple catalogue style: same-price flavours become one logical product. "
                        + "Add Official Variants creates every official flavour as a separate product. "
                        + "Missing categories are created automatically."
        );

        new MaterialAlertDialogBuilder(this)
                .setTitle("New Official Products Detected")
                .setMessage(message.toString())
                .setNegativeButton("Cancel", null)
                .setNeutralButton(
                        "Add Official Variants",
                        (dialog, which) -> applyCatalogPlan(false)
                )
                .setPositiveButton(
                        "Add Smart Groups",
                        (dialog, which) -> applyCatalogPlan(true)
                )
                .show();
    }

    private void applyCatalogPlan(boolean smartGroups) {
        OfficialCatalogSyncPlan plan = latestCatalogPlan;
        if (plan == null || !plan.hasChanges()) return;

        setPdfAnalysisBusy(true);
        setCatalogButtonVisible(false, 0, 0);
        pdfSelectionSummaryText.setText("Adding official products to the app catalogue…");

        pdfExecutor.execute(() -> {
            try {
                OfficialCatalogSyncRepository.SyncResult result = smartGroups
                        ? catalogSyncRepository.addSmartGroups(plan.getMissingProducts())
                        : catalogSyncRepository.addOfficialVariants(plan.getMissingProducts());

                runOnUiThread(() -> {
                    setPdfAnalysisBusy(false);
                    refreshCategoryDropdown();

                    Toast.makeText(
                            this,
                            result.getProductsAdded() + " product(s) added • "
                                    + result.getCategoriesAdded() + " new category/categories",
                            Toast.LENGTH_LONG
                    ).show();

                    rebuildAllPlansFromCurrentPdfs();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    setPdfAnalysisBusy(false);
                    Toast.makeText(
                            this,
                            "Catalogue sync failed: " + errorMessage(exception),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void setCatalogButtonVisible(
            boolean visible,
            int groupCount,
            int variantCount
    ) {
        if (syncNewCatalogButton == null) return;

        if (visible && groupCount > 0) {
            syncNewCatalogButton.setText(
                    "Review " + variantCount + " New/Missing Official Product"
                            + (variantCount == 1 ? "" : "s")
            );
            syncNewCatalogButton.setVisibility(View.VISIBLE);
        } else {
            syncNewCatalogButton.setVisibility(View.GONE);
        }
    }

    private void showUnmatchedProductDialog() {
        if (latestImportPlan == null
                || associateDocument == null
                || preferredCustomerDocument == null) {
            Toast.makeText(this, "Analyze both PDFs first", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> unmatchedProducts = latestImportPlan.getUnmatchedProducts();
        if (unmatchedProducts.isEmpty()) {
            Toast.makeText(this, "There are no unmatched products", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = unmatchedProducts.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Link Unmatched Product")
                .setMessage(
                        "Choose an app product. You will link it once to the correct official company product group."
                )
                .setItems(items, (dialog, which) -> {
                    if (which < 0 || which >= items.length) return;
                    Product product = findExactActiveProduct(items[which]);
                    if (product == null) {
                        Toast.makeText(this, "App product was not found", Toast.LENGTH_LONG).show();
                        return;
                    }
                    showMappingCandidateDialog(product);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMappingCandidateDialog(Product product) {
        List<SmartCompanyPriceMatcher.MappingCandidate> candidates =
                SmartCompanyPriceMatcher.getMappingCandidates(
                        product,
                        associateDocument,
                        preferredCustomerDocument
                );

        if (candidates.isEmpty()) {
            Toast.makeText(
                    this,
                    "No safe official product groups were available",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String[] labels = new String[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            labels[index] = buildCandidateLabel(candidates.get(index));
        }

        CompanyProductMapping existing = mappingStore.get(product);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Link: " + product.getName())
                .setMessage(
                        "Select the correct official product. Same-price flavour groups are shown as one logical option."
                )
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < candidates.size()) {
                        confirmRememberMapping(product, candidates.get(which));
                    }
                })
                .setNegativeButton("Cancel", null);

        if (existing != null) {
            builder.setNeutralButton("Remove Saved Link", (dialog, which) -> {
                mappingStore.remove(product);
                Toast.makeText(this, "Saved mapping removed", Toast.LENGTH_SHORT).show();
                rebuildAllPlansFromCurrentPdfs();
            });
        }

        builder.show();
    }

    private void confirmRememberMapping(
            Product product,
            SmartCompanyPriceMatcher.MappingCandidate candidate
    ) {
        StringBuilder message = new StringBuilder();
        message.append("APP PRODUCT\n")
                .append(product.getName())
                .append("\n\nOFFICIAL PRODUCT\n")
                .append(candidate.getCompanyProductName())
                .append("\n")
                .append(candidate.getCategory())
                .append(" • Stock ")
                .append(candidate.getStockNo());

        if (candidate.getVariantCount() > 1) {
            message.append("\n")
                    .append(candidate.getVariantCount())
                    .append(" same-price flavour variants grouped together");
        }

        message.append("\n\nFull ")
                .append(formatRupees(candidate.getFullPrice()))
                .append(" • @15 ")
                .append(formatRupees(candidate.getPrice15()))
                .append(" • @25 ")
                .append(formatRupees(candidate.getPrice25()))
                .append("\n@35 ")
                .append(formatRupees(candidate.getPrice35()))
                .append(" • @42 ")
                .append(formatRupees(candidate.getPrice42()))
                .append(" • @50 ")
                .append(formatRupees(candidate.getPrice50()))
                .append("\n\nThis link will be remembered for future yearly price PDFs.");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Remember This Product Link?")
                .setMessage(message.toString())
                .setNegativeButton("Back", null)
                .setPositiveButton("Remember Link", (dialog, which) -> {
                    mappingStore.save(
                            product,
                            candidate.getCompanyGroupKey(),
                            candidate.getCompanyProductName(),
                            candidate.getStockNo()
                    );
                    Toast.makeText(
                            this,
                            "Permanent mapping saved for " + product.getName(),
                            Toast.LENGTH_LONG
                    ).show();
                    rebuildAllPlansFromCurrentPdfs();
                })
                .show();
    }

    private void rebuildAllPlansFromCurrentPdfs() {
        if (latestValidationResult == null
                || associateDocument == null
                || preferredCustomerDocument == null) {
            return;
        }

        latestValidationResult.associateDocument = associateDocument;
        latestValidationResult.preferredDocument = preferredCustomerDocument;
        buildPlans(latestValidationResult);
        showPdfValidationResult(latestValidationResult);
    }

    private Product findExactActiveProduct(String name) {
        String target = cleanText(name);
        if (target.isEmpty()) return null;

        for (Product product : db.getAllProducts(true)) {
            if (product != null
                    && target.equalsIgnoreCase(cleanText(product.getName()))) {
                return product;
            }
        }
        return null;
    }

    private String buildCandidateLabel(
            SmartCompanyPriceMatcher.MappingCandidate candidate
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(candidate.getCompanyProductName());

        if (candidate.getVariantCount() > 1) {
            builder.append("  •  ")
                    .append(candidate.getVariantCount())
                    .append(" flavours");
        }

        builder.append("\n")
                .append(candidate.getCategory())
                .append("  •  Stock ")
                .append(candidate.getStockNo())
                .append("\nFull ")
                .append(formatRupees(candidate.getFullPrice()))
                .append("  •  @15 ")
                .append(formatRupees(candidate.getPrice15()))
                .append("  •  @25 ")
                .append(formatRupees(candidate.getPrice25()))
                .append("  •  @35 ")
                .append(formatRupees(candidate.getPrice35()));
        return builder.toString();
    }

    private void setMappingButtonVisible(boolean visible, int unmatchedCount) {
        if (mapUnmatchedButton == null) return;

        if (visible && unmatchedCount > 0) {
            mapUnmatchedButton.setText(
                    "Link " + unmatchedCount + " Unmatched Product"
                            + (unmatchedCount == 1 ? "" : "s")
            );
            mapUnmatchedButton.setVisibility(View.VISIBLE);
        } else {
            mapUnmatchedButton.setVisibility(View.GONE);
        }
    }

    private void showImportPreviewDialog() {
        SmartPriceImportPlan plan = latestImportPlan;
        if (plan == null || !plan.isSafeToApply()) {
            Toast.makeText(this, "Analyze and validate both PDFs first", Toast.LENGTH_SHORT).show();
            return;
        }

        List<OfficialPriceUpdate> changedUpdates = new ArrayList<>();
        for (OfficialPriceUpdate update : plan.getMatchedUpdates()) {
            if (update != null && update.isChanged()) changedUpdates.add(update);
        }

        if (changedUpdates.isEmpty()) {
            Toast.makeText(this, "All matched prices are already up to date", Toast.LENGTH_SHORT).show();
            return;
        }

        PriceChangeIntelligence.Summary intelligence =
                PriceChangeIntelligence.summarize(changedUpdates);

        StringBuilder preview = new StringBuilder();
        preview.append("Effective Date: ")
                .append(plan.getEffectiveDate().isEmpty() ? "—" : plan.getEffectiveDate())
                .append("\n\nREVISION INTELLIGENCE\n")
                .append("↑ Increased: ").append(intelligence.getIncreasedProducts())
                .append("   •   ↓ Decreased: ").append(intelligence.getDecreasedProducts())
                .append("\nTier-only changes: ").append(intelligence.getTierOnlyChanges())
                .append("   •   Avg Full Price: ")
                .append(formatPercent(intelligence.getAverageFullPricePercent()))
                .append("\n\nMatched: ").append(plan.getMatchedCount())
                .append("   •   Changing: ").append(plan.getChangedCount())
                .append("   •   Unchanged: ").append(plan.getUnchangedCount())
                .append("\nUnmatched: ").append(plan.getUnmatchedProducts().size())
                .append("\n\nPRICE CHANGES\n");

        int shown = 0;
        for (OfficialPriceUpdate update : changedUpdates) {
            if (shown >= 10) break;

            int fullDelta = update.getNewFullPrice() - update.getOldFullPrice();
            double fullPercent = PriceChangeIntelligence.fullPricePercent(update);

            preview.append("\n• ")
                    .append(update.getProductName())
                    .append(" — ")
                    .append(PriceChangeIntelligence.directionLabel(update))
                    .append(" ")
                    .append(formatSignedRupees(fullDelta))
                    .append(" (")
                    .append(formatPercent(fullPercent))
                    .append(")")
                    .append("\n  Full ")
                    .append(formatRupees(update.getOldFullPrice()))
                    .append(" → ")
                    .append(formatRupees(update.getNewFullPrice()))
                    .append("  |  @15 ")
                    .append(formatRupees(update.getOldPrice15()))
                    .append(" → ")
                    .append(formatRupees(update.getNewPrice15()))
                    .append("\n  @25 ")
                    .append(formatRupees(update.getOldPrice25()))
                    .append(" → ")
                    .append(formatRupees(update.getNewPrice25()))
                    .append("  |  @35 ")
                    .append(formatRupees(update.getOldPrice35()))
                    .append(" → ")
                    .append(formatRupees(update.getNewPrice35()))
                    .append("\n  @42 ")
                    .append(formatRupees(update.getOldPrice42()))
                    .append(" → ")
                    .append(formatRupees(update.getNewPrice42()))
                    .append("  |  @50 ")
                    .append(formatRupees(update.getOldPrice50()))
                    .append(" → ")
                    .append(formatRupees(update.getNewPrice50()))
                    .append("\n  Match: ")
                    .append(Math.round(update.getConfidence() * 100d))
                    .append("% • Stock ")
                    .append(update.getStockNo());
            shown++;
        }

        if (changedUpdates.size() > shown) {
            preview.append("\n\n+")
                    .append(changedUpdates.size() - shown)
                    .append(" more safe change(s)");
        }

        if (!plan.getUnmatchedProducts().isEmpty()) {
            preview.append("\n\nNOT CHANGED\n");
            int unmatchedShown = 0;
            for (String productName : plan.getUnmatchedProducts()) {
                if (unmatchedShown >= 6) break;
                preview.append("\n• ").append(productName);
                unmatchedShown++;
            }
        }

        preview.append(
                "\n\nOnly the six price columns will change. VP, product name, category and active status stay untouched. A full undo snapshot will be saved first."
        );

        new MaterialAlertDialogBuilder(this)
                .setTitle("Review Official Price Update")
                .setMessage(preview.toString())
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Apply " + changedUpdates.size() + " Updates",
                        (dialog, which) -> applyOfficialPricePlan(plan, changedUpdates)
                )
                .show();
    }

    private void applyOfficialPricePlan(
            SmartPriceImportPlan plan,
            List<OfficialPriceUpdate> changedUpdates
    ) {
        if (plan == null || changedUpdates == null || changedUpdates.isEmpty()) return;

        setPdfAnalysisBusy(true);
        setMappingButtonVisible(false, 0);
        pdfSelectionSummaryText.setText("Saving official company prices safely…");

        pdfExecutor.execute(() -> {
            try {
                int updated = officialPriceImportRepository.applyOfficialPriceUpdates(
                        changedUpdates,
                        plan.getEffectiveDate()
                );

                if (updated > 0) {
                    getSharedPreferences("smart_price_update", MODE_PRIVATE)
                            .edit()
                            .putString("last_effective_date", plan.getEffectiveDate())
                            .putLong("last_imported_at", System.currentTimeMillis())
                            .putInt("last_updated_count", updated)
                            .apply();
                }

                runOnUiThread(() -> {
                    setPdfAnalysisBusy(false);
                    if (updated > 0) {
                        Toast.makeText(
                                this,
                                updated + " products updated from official PDFs",
                                Toast.LENGTH_LONG
                        ).show();
                        rebuildAllPlansFromCurrentPdfs();
                    } else {
                        pdfSelectionSummaryText.setText("No prices were changed");
                        analyzeCompanyPdfsButton.setText("Analyze PDFs Again");
                        analyzeCompanyPdfsButton.setEnabled(true);
                    }
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    setPdfAnalysisBusy(false);
                    Toast.makeText(
                            this,
                            "Official price update failed: " + errorMessage(exception),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void showPdfAnalysisFailure(Exception exception) {
        setPdfAnalysisBusy(false);
        associateDocument = null;
        preferredCustomerDocument = null;
        latestImportPlan = null;
        latestCatalogPlan = null;
        latestValidationResult = null;
        setMappingButtonVisible(false, 0);
        setCatalogButtonVisible(false, 0, 0);

        pdfAnalysisCard.setVisibility(View.VISIBLE);
        associatePdfStatusText.setText("Could not validate");
        preferredPdfStatusText.setText("Could not validate");
        pdfEffectiveDateText.setText("Effective Date: Not confirmed");
        pdfCrosscheckText.setText("PDF analysis failed");
        pdfWarningsText.setText(errorMessage(exception));
        pdfWarningsText.setVisibility(View.VISIBLE);
        analyzeCompanyPdfsButton.setText("Try Again");
        analyzeCompanyPdfsButton.setEnabled(true);
        Toast.makeText(this, "PDF analysis failed", Toast.LENGTH_LONG).show();
    }

    private void setPdfAnalysisBusy(boolean busy) {
        selectCompanyPdfsButton.setEnabled(!busy);
        analyzeCompanyPdfsButton.setEnabled(
                !busy && selectedCompanyPdfUris.size() == 2
        );
        if (syncNewCatalogButton != null && busy) syncNewCatalogButton.setEnabled(false);
        if (mapUnmatchedButton != null && busy) mapUnmatchedButton.setEnabled(false);
        if (!busy) {
            if (syncNewCatalogButton != null) syncNewCatalogButton.setEnabled(true);
            if (mapUnmatchedButton != null) mapUnmatchedButton.setEnabled(true);
        }
        pdfAnalysisProgress.setVisibility(busy ? View.VISIBLE : View.GONE);

        if (busy) {
            associatePdfStatusText.setText("Analyzing…");
            preferredPdfStatusText.setText("Analyzing…");
        }
    }

    private String getDisplayName(Uri uri) {
        if (uri == null) return "Selected PDF";

        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (columnIndex >= 0) {
                    String value = cursor.getString(columnIndex);
                    if (value != null && !value.trim().isEmpty()) return value.trim();
                }
            }
        } catch (Exception ignored) {
        }

        String lastSegment = uri.getLastPathSegment();
        return lastSegment == null || lastSegment.trim().isEmpty()
                ? "Selected PDF"
                : lastSegment.trim();
    }

    private String displayName(@Nullable CompanyPriceDocument document) {
        if (document == null) return "PDF";
        String sourceName = cleanText(document.getSourceName());
        return sourceName.isEmpty() ? "PDF" : sourceName;
    }

    private String normalizeDateKey(String value) {
        return cleanText(value)
                .toLowerCase(Locale.US)
                .replaceAll("(?<=\\d)(st|nd|rd|th)\\b", "")
                .replaceAll("[^a-z0-9]+", "");
    }

    private String normalizeStockNo(String value) {
        return cleanText(value)
                .toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]", "");
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildBulletText(List<String> messages) {
        StringBuilder builder = new StringBuilder();
        for (String message : messages) {
            String cleanMessage = cleanText(message);
            if (cleanMessage.isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append("• ").append(cleanMessage);
        }
        return builder.toString();
    }

    private String formatRupees(int amount) {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        return "₹" + format.format(Math.max(0, amount));
    }

    private String formatSignedRupees(int amount) {
        if (amount > 0) return "+" + formatRupees(amount);
        if (amount < 0) return "-" + formatRupees(Math.abs(amount));
        return "₹0";
    }

    private String formatPercent(double value) {
        return String.format(Locale.getDefault(), "%+.1f%%", value);
    }

    private String errorMessage(Exception exception) {
        if (exception == null
                || exception.getMessage() == null
                || exception.getMessage().trim().isEmpty()) {
            return "The selected PDFs could not be processed.";
        }
        return exception.getMessage().trim();
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) return false;
        for (String value : values) {
            if (target.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private void confirmScalePrices() {
        double percent = parse(changePercent, 0d);
        if (percent == 0d) {
            Toast.makeText(this, "Enter a non-zero percentage", Toast.LENGTH_SHORT).show();
            return;
        }

        String category = selectedCategory();
        String direction = percent > 0 ? "increase" : "decrease";
        String message = "This will " + direction + " saved prices by "
                + Math.abs(percent) + "% for " + category + ".";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Apply company price revision?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    int updated = db.scalePrices(
                            category,
                            percent,
                            includeFullPrice.isChecked(),
                            selectedRounding()
                    );
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
                .setTitle("Recalculate discount prices?")
                .setMessage(
                        "All selected discount prices will be calculated from Full Price. "
                                + "Official PDF values should be preferred when available. "
                                + "You can undo this bulk update."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Recalculate", (dialog, which) -> {
                    int updated = db.recalculateDiscounts(
                            selectedCategory(),
                            d15,
                            d25,
                            d35,
                            d42,
                            d50,
                            selectedRounding()
                    );
                    Toast.makeText(this, updated + " products recalculated", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void confirmUndo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Undo latest bulk update?")
                .setMessage(
                        "Prices will return to the snapshot saved immediately before the most recent bulk revision, including an official PDF import."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Undo", (dialog, which) -> {
                    int restored = db.undoLastBulkOperation();
                    Toast.makeText(
                            this,
                            restored > 0
                                    ? restored + " products restored"
                                    : "No bulk update available to undo",
                            Toast.LENGTH_LONG
                    ).show();
                    if (restored > 0 && latestValidationResult != null) {
                        rebuildAllPlansFromCurrentPdfs();
                    }
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
            if (input.getText() == null || input.getText().toString().trim().isEmpty()) {
                return fallback;
            }
            return Double.parseDouble(input.getText().toString().trim());
        } catch (Exception exception) {
            return fallback;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class CrossCheckResult {
        private int commonStockRows;
        private int sharedPriceChecks;
        private int sharedPriceConflicts;
    }

    private static class PdfValidationResult {
        private CompanyPriceDocument associateDocument;
        private CompanyPriceDocument preferredDocument;
        private String effectiveDate = "";
        private int commonStockRows;
        private int sharedPriceChecks;
        private int sharedPriceConflicts;
        private boolean valid;
        private SmartPriceImportPlan importPlan;
        private OfficialCatalogSyncPlan catalogPlan;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
    }
}
