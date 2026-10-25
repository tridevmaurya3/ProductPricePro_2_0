package com.example.productprice;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.productprice.data.OfficialCatalogReplaceRepository;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.OfficialCatalogBuildResult;
import com.example.productprice.model.Product;
import com.example.productprice.util.CompanyPricePdfParser;
import com.example.productprice.util.OfficialFullCatalogBuilder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OfficialPdfCatalogActivity extends AppCompatActivity {

  private ProductDbHelper db;
  private OfficialCatalogReplaceRepository replaceRepository;

  private MaterialButton restoreButton;
  private MaterialButton selectButton;
  private MaterialButton reviewReplaceButton;
  private MaterialButton manualToolsButton;
  private LinearProgressIndicator progress;
  private MaterialCardView resultCard;
  private TextView selectionText;
  private TextView associateStatus;
  private TextView preferredStatus;
  private TextView resultTitle;
  private TextView resultSummary;
  private TextView categorySummary;
  private TextView warningText;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final List<Uri> selectedUris = new ArrayList<>();
  private final List<String> selectedNames = new ArrayList<>();

  private CompanyPriceDocument associateDocument;
  private CompanyPriceDocument preferredDocument;
  private OfficialCatalogBuildResult catalogResult;

  private final ActivityResultLauncher<String[]> pickerLauncher =
      registerForActivityResult(
          new ActivityResultContracts.OpenMultipleDocuments(), this::onPdfsSelected);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_official_pdf_catalog);

    db = ProductDbHelper.getInstance(this);
    db.initialize();
    replaceRepository = new OfficialCatalogReplaceRepository(this, db);

    bindViews();
    bindActions();
    MaterialButton restore = new MaterialButton(this);
    restoreButton = restore;
    restore.setText("Restore catalog before last PDF import");
    ((android.view.ViewGroup) manualToolsButton.getParent()).addView(restore);
    restore.setEnabled(replaceRepository.hasBackup());
    restore.setOnClickListener(
        v ->
            new MaterialAlertDialogBuilder(this)
                .setTitle("Restore previous catalog?")
                .setMessage(
                    "Restores all products, categories and price history to before the last import."
                        + " Subsequent catalog edits will be replaced. Customers and saved"
                        + " quotations stay unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Restore",
                    (d, w) -> {
                      executor.execute(
                          () -> {
                            try {
                              replaceRepository.restoreLastCatalog();
                              runOnUiThread(
                                  () -> {
                                    restore.setEnabled(false);
                                    Toast.makeText(
                                            this, "Previous catalog restored", Toast.LENGTH_LONG)
                                        .show();
                                  });
                            } catch (Exception e) {
                              runOnUiThread(
                                  () ->
                                      Toast.makeText(this, errorMessage(e), Toast.LENGTH_LONG)
                                          .show());
                            }
                          });
                    })
                .show());
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    executor.shutdownNow();
  }

  private void bindViews() {
    MaterialToolbar toolbar = findViewById(R.id.toolbar_official_catalog);
    toolbar.setNavigationOnClickListener(view -> finish());

    selectButton = findViewById(R.id.button_select_official_catalog_pdfs);
    reviewReplaceButton = findViewById(R.id.button_review_replace_catalog);
    manualToolsButton = findViewById(R.id.button_open_manual_price_tools);
    progress = findViewById(R.id.progress_official_catalog);
    resultCard = findViewById(R.id.card_official_catalog_result);
    selectionText = findViewById(R.id.text_official_catalog_selection);
    associateStatus = findViewById(R.id.text_official_associate_status);
    preferredStatus = findViewById(R.id.text_official_preferred_status);
    resultTitle = findViewById(R.id.text_official_catalog_title);
    resultSummary = findViewById(R.id.text_official_catalog_summary);
    categorySummary = findViewById(R.id.text_official_catalog_categories);
    warningText = findViewById(R.id.text_official_catalog_warnings);
  }

  private void bindActions() {
    selectButton.setOnClickListener(
        view -> pickerLauncher.launch(new String[] {"application/pdf"}));

    reviewReplaceButton.setOnClickListener(view -> showCatalogReview());

    manualToolsButton.setOnClickListener(
        view -> startActivity(new Intent(this, PriceUpdateActivity.class)));
  }

  private void onPdfsSelected(List<Uri> uris) {
    selectedUris.clear();
    selectedNames.clear();
    associateDocument = null;
    preferredDocument = null;
    catalogResult = null;
    resultCard.setVisibility(View.GONE);
    reviewReplaceButton.setVisibility(View.GONE);

    if (uris == null || uris.size() != 2) {
      selectionText.setText("Please select exactly 2 PDFs together");
      Toast.makeText(this, "Select Associate + Preferred Customer PDFs together", Toast.LENGTH_LONG)
          .show();
      return;
    }

    for (Uri uri : uris) {
      if (uri == null) continue;
      selectedUris.add(uri);
      selectedNames.add(displayName(uri));
      try {
        getContentResolver()
            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (Exception ignored) {
      }
    }

    if (selectedUris.size() != 2) {
      selectionText.setText("Could not read both selected PDFs");
      return;
    }

    selectionText.setText("2 PDFs selected • analyzing automatically…");
    associateStatus.setText("Detecting…");
    preferredStatus.setText("Detecting…");
    analyzeSelectedPdfs();
  }

  private void analyzeSelectedPdfs() {
    if (selectedUris.size() != 2) return;

    setBusy(true);
    Uri firstUri = selectedUris.get(0);
    Uri secondUri = selectedUris.get(1);
    String firstName = selectedNames.get(0);
    String secondName = selectedNames.get(1);

    executor.execute(
        () -> {
          try {
            CompanyPriceDocument first = CompanyPricePdfParser.parse(this, firstUri, firstName);
            CompanyPriceDocument second = CompanyPricePdfParser.parse(this, secondUri, secondName);

            Validation validation = validatePair(first, second);
            if (!validation.errors.isEmpty()) {
              runOnUiThread(() -> showValidationError(validation));
              return;
            }

            OfficialCatalogBuildResult built =
                OfficialFullCatalogBuilder.build(
                    validation.associate, validation.preferred, validation.effectiveDate);

            runOnUiThread(() -> showCatalogResult(validation, built));
          } catch (Exception exception) {
            runOnUiThread(() -> showFailure(exception));
          }
        });
  }

  private Validation validatePair(CompanyPriceDocument first, CompanyPriceDocument second) {
    Validation result = new Validation();

    for (CompanyPriceDocument document : new CompanyPriceDocument[] {first, second}) {
      if (document == null) continue;

      if (document.getDocumentType() == CompanyPriceDocument.DocumentType.ASSOCIATE) {
        if (result.associate != null) {
          result.errors.add("Both files look like Associate price lists.");
        }
        result.associate = document;
      } else if (document.getDocumentType()
          == CompanyPriceDocument.DocumentType.PREFERRED_CUSTOMER) {
        if (result.preferred != null) {
          result.errors.add("Both files look like Preferred Customer price lists.");
        }
        result.preferred = document;
      } else {
        result.errors.add("One selected PDF is not recognized as an official price list.");
      }
    }

    if (result.associate == null) {
      result.errors.add("Associate PDF is missing.");
    }
    if (result.preferred == null) {
      result.errors.add("Preferred Customer PDF is missing.");
    }
    if (result.associate == null || result.preferred == null) return result;

    String associateDate = clean(result.associate.getEffectiveDate());
    String preferredDate = clean(result.preferred.getEffectiveDate());

    if (associateDate.isEmpty() || preferredDate.isEmpty()) {
      result.errors.add("Effective Date was not found in both PDFs.");
    } else if (!dateKey(associateDate).equals(dateKey(preferredDate))) {
      result.errors.add("The two PDFs have different Effective Dates.");
    } else {
      result.effectiveDate = associateDate;
    }

    return result;
  }

  private void showValidationError(Validation validation) {
    setBusy(false);
    resultCard.setVisibility(View.VISIBLE);
    resultTitle.setText("PDF validation stopped");
    resultSummary.setText(joinBullets(validation.errors));
    categorySummary.setText("");
    warningText.setVisibility(View.GONE);
    reviewReplaceButton.setVisibility(View.GONE);
    selectionText.setText("PDF validation needs attention");

    associateStatus.setText(
        validation.associate == null
            ? "Not detected"
            : validation.associate.getRows().size() + " rows detected");
    preferredStatus.setText(
        validation.preferred == null
            ? "Not detected"
            : validation.preferred.getRows().size() + " rows detected");
  }

  private void showFailure(Exception exception) {
    setBusy(false);
    catalogResult = null;
    resultCard.setVisibility(View.VISIBLE);
    resultTitle.setText("PDF analysis failed");
    resultSummary.setText(errorMessage(exception));
    categorySummary.setText("");
    warningText.setVisibility(View.GONE);
    reviewReplaceButton.setVisibility(View.GONE);
    selectionText.setText("Could not analyze the selected PDFs");
    associateStatus.setText("Could not validate");
    preferredStatus.setText("Could not validate");
    Toast.makeText(this, "PDF analysis failed: " + errorMessage(exception), Toast.LENGTH_LONG)
        .show();
  }

  private void showCatalogResult(Validation validation, OfficialCatalogBuildResult built) {
    setBusy(false);
    associateDocument = validation.associate;
    preferredDocument = validation.preferred;
    catalogResult = built;
    resultCard.setVisibility(View.VISIBLE);

    associateStatus.setText(
        selectedNameFor(CompanyPriceDocument.DocumentType.ASSOCIATE)
            + "\n"
            + built.getAssociateRows()
            + " parsed rows");
    preferredStatus.setText(
        selectedNameFor(CompanyPriceDocument.DocumentType.PREFERRED_CUSTOMER)
            + "\n"
            + built.getPreferredRows()
            + " parsed rows");

    if (!built.isReadyToReplace()) {
      resultTitle.setText("Catalog cannot be replaced yet");
      resultSummary.setText(joinBullets(built.getErrors()));
      categorySummary.setText("");
      warningText.setVisibility(View.GONE);
      reviewReplaceButton.setVisibility(View.GONE);
      selectionText.setText("Analysis found blocking issues");
      categorySummary.setText(
          "Detected identities: "
              + built.getAssociateRows()
              + " Associate / "
              + built.getPreferredRows()
              + " Preferred. Complete products: "
              + built.getProductCount());
      return;
    }

    resultTitle.setText("Complete official catalog is ready");
    resultSummary.setText(
        "Effective "
            + built.getEffectiveDate()
            + " • "
            + built.getProductCount()
            + " products"
            + " • "
            + built.getCategoryCount()
            + " categories\n"
            + "All app prices will come only from these PDFs: Full/MRP + @15 + @25 + @35 + @42 +"
            + " @50.");
    categorySummary.setText(buildCategorySummary(built));

    if (built.getWarnings().isEmpty() && built.getSkippedIncompleteRows() == 0) {
      warningText.setVisibility(View.GONE);
    } else {
      String warning =
          built.getSkippedIncompleteRows() > 0
              ? built.getSkippedIncompleteRows()
                  + " incomplete/non-common row(s) were excluded because all six app price levels"
                  + " were not safely available."
              : "";
      if (!built.getWarnings().isEmpty()) {
        if (!warning.isEmpty()) warning += "\n";
        warning += joinBullets(built.getWarnings());
      }
      warningText.setText(warning);
      warningText.setVisibility(View.VISIBLE);
    }

    reviewReplaceButton.setVisibility(View.VISIBLE);
    selectionText.setText(
        built.getProductCount() + " official products ready • final review required");
  }

  private void showCatalogReview() {
    OfficialCatalogBuildResult built = catalogResult;
    if (built == null || !built.isReadyToReplace()) {
      Toast.makeText(this, "Select and validate both PDFs first", Toast.LENGTH_SHORT).show();
      return;
    }

    StringBuilder review = new StringBuilder();
    review
        .append("Effective Date: ")
        .append(built.getEffectiveDate())
        .append("\n\nCURRENT APP CATALOG\n")
        .append(db.getAllProducts(false).size())
        .append(" products • ")
        .append(db.getCategories().size())
        .append(" categories")
        .append("\n\nNEW OFFICIAL CATALOG\n")
        .append(built.getProductCount())
        .append(" products • ")
        .append(built.getCategoryCount())
        .append(" categories\n")
        .append(buildCategorySummary(built))
        .append("\n\nPRODUCT & PRICE REVIEW\n");

    int shown = 0;
    for (Product product : built.getProducts()) {

      review
          .append("\n• ")
          .append(product.getName())
          .append("\n  ")
          .append(product.getCategory())
          .append(" • VP ")
          .append(formatVp(product.getVp()))
          .append("\n  Full ")
          .append(formatRupees(product.getFullPrice()))
          .append(" | @15 ")
          .append(formatRupees(product.getPrice15()))
          .append(" | @25 ")
          .append(formatRupees(product.getPrice25()))
          .append("\n  @35 ")
          .append(formatRupees(product.getPrice35()))
          .append(" | @42 ")
          .append(formatRupees(product.getPrice42()))
          .append(" | @50 ")
          .append(formatRupees(product.getPrice50()));
      shown++;
    }

    if (built.getProductCount() > shown) {
      review.append("\n\n+").append(built.getProductCount() - shown).append(" more product(s)");
    }

    review.append("\n\nDETAILS\n").append(joinBullets(built.getWarnings()));
    review.append(
        "\n\nNothing is changed yet. Continue only after checking the official prices above.");

    new MaterialAlertDialogBuilder(this)
        .setTitle("Final Official Catalog Review")
        .setMessage(review.toString())
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Continue", (dialog, which) -> showFinalReplaceConfirmation())
        .show();
  }

  private void showFinalReplaceConfirmation() {
    String previous =
        getSharedPreferences("smart_price_update", MODE_PRIVATE)
            .getString("last_effective_date", "");
    if (catalogResult != null
        && com.example.productprice.util.CatalogDates.isOlder(
            catalogResult.getEffectiveDate(), previous)) {
      new MaterialAlertDialogBuilder(this)
          .setTitle("Older price list")
          .setMessage(
              "Selected: "
                  + catalogResult.getEffectiveDate()
                  + "\nCurrent: "
                  + previous
                  + "\nContinue only if you intend to restore older prices.")
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Review older import", (d, w) -> confirmCatalogSync())
          .show();
      return;
    }
    confirmCatalogSync();
  }

  private void confirmCatalogSync() {
    OfficialCatalogBuildResult built = catalogResult;
    if (built == null || !built.isReadyToReplace()) return;

    int oldProductCount = db.getAllProducts(false).size();
    int oldCategoryCount = db.getCategories().size();

    new MaterialAlertDialogBuilder(this)
        .setTitle("Sync Complete Official Catalog?")
        .setMessage(
            "Automatically adds new Stock Nos. and categories, updates official names, VP and exact"
                + " prices, and archives official items absent from the new PDFs. Custom products"
                + " are preserved.\n\n"
                + "A complete catalog + price-history snapshot is saved for Restore. Customers,"
                + " quotations and App Lock stay unchanged.")
        .setNegativeButton("Back", null)
        .setPositiveButton("APPLY COMPLETE CATALOG", (dialog, which) -> replaceCatalogNow())
        .show();
  }

  private void replaceCatalogNow() {
    OfficialCatalogBuildResult built = catalogResult;
    if (built == null || !built.isReadyToReplace()) return;

    setBusy(true);
    reviewReplaceButton.setVisibility(View.GONE);
    selectionText.setText("Replacing old catalog with official PDF catalog…");

    executor.execute(
        () -> {
          try {
            OfficialCatalogReplaceRepository.ReplaceResult replaceResult =
                replaceRepository.replaceWithOfficialCatalog(
                    built.getProducts(), built.getEffectiveDate());

            getSharedPreferences("official_price_import_audit", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

            runOnUiThread(() -> showReplaceSuccess(replaceResult));
          } catch (Exception exception) {
            runOnUiThread(
                () -> {
                  setBusy(false);
                  reviewReplaceButton.setVisibility(View.VISIBLE);
                  selectionText.setText("Catalog sync failed • previous data kept");
                  Toast.makeText(
                          this,
                          "Catalog replacement failed: " + errorMessage(exception),
                          Toast.LENGTH_LONG)
                      .show();
                });
          }
        });
  }

  private void showReplaceSuccess(OfficialCatalogReplaceRepository.ReplaceResult replaceResult) {
    restoreButton.setEnabled(true);
    setBusy(false);
    resultTitle.setText("Official catalog applied successfully");
    resultSummary.setText(
        replaceResult.getAddedCount()
            + " new products • "
            + replaceResult.getUpdatedCount()
            + " products updated\n"
            + replaceResult.getNewCategoryCount()
            + " categories • Effective "
            + replaceResult.getEffectiveDate());
    categorySummary.setText(
        "All official PDF products and categories are active. Your custom products and saved"
            + " quotations are preserved.");
    warningText.setVisibility(View.GONE);
    reviewReplaceButton.setVisibility(View.GONE);
    selectionText.setText("Official catalog is now active");

    new MaterialAlertDialogBuilder(this)
        .setTitle("Catalog Updated")
        .setMessage(
            replaceResult.getNewProductCount()
                + " official products are now active with their PDF Full, @15, @25, @35, @42 and"
                + " @50 prices.")
        .setNegativeButton("Stay Here", null)
        .setPositiveButton(
            "Open Product Manager",
            (dialog, which) -> startActivity(new Intent(this, ProductManagementActivity.class)))
        .show();
  }

  private String selectedNameFor(CompanyPriceDocument.DocumentType type) {
    CompanyPriceDocument document =
        type == CompanyPriceDocument.DocumentType.ASSOCIATE ? associateDocument : preferredDocument;
    if (document == null || clean(document.getSourceName()).isEmpty()) {
      return type == CompanyPriceDocument.DocumentType.ASSOCIATE
          ? "Associate PDF"
          : "Preferred Customer PDF";
    }
    return document.getSourceName();
  }

  private String buildCategorySummary(OfficialCatalogBuildResult built) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, Integer> entry : built.getCategoryCounts().entrySet()) {
      if (builder.length() > 0) builder.append(" • ");
      builder.append(entry.getKey()).append(" ").append(entry.getValue());
    }
    return builder.toString();
  }

  private void setBusy(boolean busy) {
    if (restoreButton != null) restoreButton.setEnabled(!busy && replaceRepository.hasBackup());
    selectButton.setEnabled(!busy);
    manualToolsButton.setEnabled(!busy);
    reviewReplaceButton.setEnabled(!busy);
    progress.setVisibility(busy ? View.VISIBLE : View.GONE);
  }

  private String displayName(Uri uri) {
    if (uri == null) return "Selected PDF";
    try (Cursor cursor =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0) {
          String value = cursor.getString(index);
          if (value != null && !value.trim().isEmpty()) return value.trim();
        }
      }
    } catch (Exception ignored) {
    }
    return "Selected PDF";
  }

  private String dateKey(String value) {
    return clean(value)
        .toLowerCase(Locale.US)
        .replaceAll("(?<=\\d)(st|nd|rd|th)\\b", "")
        .replaceAll("[^a-z0-9]+", "");
  }

  private String joinBullets(List<String> values) {
    StringBuilder builder = new StringBuilder();
    if (values == null) return "";
    for (String value : values) {
      if (value == null || value.trim().isEmpty()) continue;
      if (builder.length() > 0) builder.append('\n');
      builder.append("• ").append(value.trim());
    }
    return builder.toString();
  }

  private String formatRupees(int value) {
    NumberFormat format = NumberFormat.getNumberInstance(new Locale("en", "IN"));
    format.setMaximumFractionDigits(0);
    return "₹" + format.format(Math.max(0, value));
  }

  private String formatVp(double value) {
    return String.format(Locale.getDefault(), "%.2f", Math.max(0d, value));
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String errorMessage(Exception exception) {
    if (exception == null
        || exception.getMessage() == null
        || exception.getMessage().trim().isEmpty()) {
      return "Unknown error";
    }
    return exception.getMessage().trim();
  }

  private static class Validation {
    private CompanyPriceDocument associate;
    private CompanyPriceDocument preferred;
    private String effectiveDate = "";
    private final List<String> errors = new ArrayList<>();
  }
}
