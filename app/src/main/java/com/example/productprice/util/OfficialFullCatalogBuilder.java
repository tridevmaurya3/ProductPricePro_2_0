package com.example.productprice.util;

import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.CompanyPriceRow;
import com.example.productprice.model.OfficialCatalogBuildResult;
import com.example.productprice.model.Product;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds a completely fresh app catalogue from the two official company PDFs.
 *
 * <p>Source of truth: - Associate PDF: MRP/Full, @25, @35, @42, @50 and VP. - Preferred Customer
 * PDF: @15/Bronze.
 *
 * <p>The two PDFs are intentionally not forced to have identical @25/@35 values for every parsed
 * row. PDF table extraction can attach a shared-price block to a neighbouring flavour row, and the
 * company may also publish presentation differences. Such differences are reported as warnings, not
 * as blockers. The app never reuses any legacy database price while building this catalog.
 */
public final class OfficialFullCatalogBuilder {

  private static final Pattern STOCK_LINE =
      Pattern.compile("^\\s*([0-9]{4}|[0-9]{3}[A-Z])\\b.*$", Pattern.CASE_INSENSITIVE);

  private OfficialFullCatalogBuilder() {}

  public static OfficialCatalogBuildResult build(
      CompanyPriceDocument associate, CompanyPriceDocument preferred, String effectiveDate) {
    OfficialCatalogBuildResult result = new OfficialCatalogBuildResult(effectiveDate);
    if (associate == null || preferred == null) {
      result.addError("Both PDFs are required.");
      return result;
    }
    if (associate.getDocumentType() != CompanyPriceDocument.DocumentType.ASSOCIATE
        || preferred.getDocumentType() != CompanyPriceDocument.DocumentType.PREFERRED_CUSTOMER
        || associate.getEffectiveDate().isEmpty()
        || preferred.getEffectiveDate().isEmpty()
        || !associate.getEffectiveDate().equalsIgnoreCase(preferred.getEffectiveDate())) {
      result.addError(
          "Select one Associate and one Preferred Customer PDF with the same effective date.");
      return result;
    }
    result.setAssociateRows(associate.getDetectedIdentityCount());
    result.setPreferredRows(preferred.getDetectedIdentityCount());
    Map<String, CompanyPriceRow> a = new LinkedHashMap<>(), b = new LinkedHashMap<>();
    for (CompanyPriceRow r : associate.getRows()) a.put(normalizeStock(r.getStockNo()), r);
    for (CompanyPriceRow r : preferred.getRows()) b.put(normalizeStock(r.getStockNo()), r);
    Set<String> stocks = new java.util.LinkedHashSet<>(a.keySet());
    stocks.addAll(b.keySet());
    Set<String> names = new java.util.HashSet<>();
    for (String stock : stocks) {
      CompanyPriceRow ar = a.get(stock), pr = b.get(stock);
      if (ar != null && pr != null) {
        if (!ar.hasAnyMappedPrice() && pr.hasAnyMappedPrice())
          fillGroup(ar, pr, associate.getPriceGroups(), true);
        if (!pr.hasAnyMappedPrice() && ar.hasAnyMappedPrice())
          fillGroup(pr, ar, preferred.getPriceGroups(), false);
      }
      CompanyPriceRow identity = ar != null ? ar : pr;
      String label = stock + " / " + identity.getProductName();
      if (ar == null
          || pr == null
          || ar.getMrp() <= 0
          || pr.getMrp() <= 0
          || pr.getPrice15() == null
          || ar.getPrice25() == null
          || ar.getPrice35() == null
          || ar.getPrice42() == null
          || ar.getPrice50() == null) {
        result.incrementSkippedIncompleteRows();
        result.addError(label + ": incomplete PDF price data; catalog has NOT been applied.");
        continue;
      }
      if (ar.getMrp() != pr.getMrp()
          || !samePrice(ar.getPrice25(), pr.getPrice25())
          || !samePrice(ar.getPrice35(), pr.getPrice35())
          || Math.abs(ar.getVolumePoint() - pr.getVolumePoint()) > .011) {
        result.addError(label + ": the two PDFs disagree on MRP, VP or shared prices.");
        continue;
      }
      String category =
          firstNonEmpty(pr.getCategory(), ar.getCategory()).replaceFirst("^VRITILIFE ", "");
      if (category.isEmpty()) {
        result.addError(label + ": category heading missing.");
        continue;
      }
      Product product = new Product();
      product.setStockNo(stock);
      product.setEffectiveDate(effectiveDate);
      product.setName(cleanProductName(identity.getProductName()));
      if (!names.add(normalizeName(product.getName())))
        product.setName(product.getName() + " [" + stock + "]");
      product.setCategory(category);
      product.setVp(ar.getVolumePoint());
      product.setFullPrice(ar.getMrp());
      product.setPrice15(pr.getPrice15());
      product.setPrice25(ar.getPrice25());
      product.setPrice35(ar.getPrice35());
      product.setPrice42(ar.getPrice42());
      product.setPrice50(ar.getPrice50());
      product.setActive(true);
      result.addProduct(product);
    }
    for (String warning : associate.getWarnings())
      if (warning.contains("conflicting duplicate")) result.addError(warning);
    for (String warning : preferred.getWarnings())
      if (warning.contains("conflicting duplicate")) result.addError(warning);
    if (result.getErrors().isEmpty())
      result.addWarning(
          "Every detected Stock No. was reconciled across both PDFs; shared price blocks were"
              + " completed using matching MRP, VP, Silver and Gold prices.");
    if (stocks.isEmpty()) result.addError("No product identities found.");
    return result;
  }

  private static void fillGroup(
      CompanyPriceRow target,
      CompanyPriceRow reference,
      List<CompanyPriceRow> groups,
      boolean associate) {
    CompanyPriceRow found = null;
    for (CompanyPriceRow group : groups) {
      if (group.getMrp() != reference.getMrp()
          || Math.abs(group.getVolumePoint() - reference.getVolumePoint()) > .011
          || !samePrice(group.getPrice25(), reference.getPrice25())
          || !samePrice(group.getPrice35(), reference.getPrice35())) continue;
      if (found != null
          && (!samePrice(found.getPrice15(), group.getPrice15())
              || !samePrice(found.getPrice42(), group.getPrice42())
              || !samePrice(found.getPrice50(), group.getPrice50()))) return;
      found = group;
    }
    if (found == null) return;
    target.setMrp(found.getMrp());
    target.setVolumePoint(found.getVolumePoint());
    target.setPrice25(found.getPrice25());
    target.setPrice35(found.getPrice35());
    if (associate) {
      target.setPrice42(found.getPrice42());
      target.setPrice50(found.getPrice50());
    } else target.setPrice15(found.getPrice15());
  }

  private static String normalizeStock(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.US);
  }

  private static String normalizeName(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
  }

  private static boolean samePrice(Integer a, Integer b) {
    return java.util.Objects.equals(a, b);
  }

  private static String firstNonEmpty(String a, String b) {
    return a != null && !a.trim().isEmpty() ? a.trim() : b == null ? "" : b.trim();
  }

  private static String cleanProductName(String value) {
    return value == null
        ? ""
        : value.replace("®", "").replace("™", "").replace("–", "-").replaceAll("\\s+", " ").trim();
  }
}
