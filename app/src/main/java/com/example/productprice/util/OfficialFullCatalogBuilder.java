package com.example.productprice.util;

import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.CompanyPriceRow;
import com.example.productprice.model.OfficialCatalogBuildResult;
import com.example.productprice.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a completely fresh app catalogue from the two official company PDFs.
 *
 * Source of truth:
 * - Associate PDF: MRP/Full, @25, @35, @42, @50 and VP.
 * - Preferred Customer PDF: @15/Bronze.
 *
 * The two PDFs are intentionally not forced to have identical @25/@35 values
 * for every parsed row. PDF table extraction can attach a shared-price block to
 * a neighbouring flavour row, and the company may also publish presentation
 * differences. Such differences are reported as warnings, not as blockers.
 * The app never reuses any legacy database price while building this catalog.
 */
public final class OfficialFullCatalogBuilder {

    private static final Pattern STOCK_LINE = Pattern.compile(
            "^\\s*([0-9]{4}|[0-9]{3}[A-Z])\\b.*$",
            Pattern.CASE_INSENSITIVE
    );

    private OfficialFullCatalogBuilder() {
    }

    public static OfficialCatalogBuildResult build(
            CompanyPriceDocument associate,
            CompanyPriceDocument preferred,
            String effectiveDate
    ) {
        OfficialCatalogBuildResult result =
                new OfficialCatalogBuildResult(effectiveDate);

        if (associate == null || preferred == null) {
            result.addError("Both official PDFs are required.");
            return result;
        }

        result.setAssociateRows(associate.getRows().size());
        result.setPreferredRows(preferred.getRows().size());

        Map<String, String> categoryByStock = new HashMap<>();
        categoryByStock.putAll(extractCategoryByStock(associate.getRawText()));
        Map<String, String> preferredCategories =
                extractCategoryByStock(preferred.getRawText());
        for (Map.Entry<String, String> entry : preferredCategories.entrySet()) {
            categoryByStock.putIfAbsent(entry.getKey(), entry.getValue());
        }

        Map<String, CompanyPriceRow> preferredByStock = new HashMap<>();
        List<CompanyPriceRow> preferredRows = new ArrayList<>();
        for (CompanyPriceRow row : preferred.getRows()) {
            if (row == null) continue;
            preferredRows.add(row);
            String stock = normalizeStock(row.getStockNo());
            if (!stock.isEmpty()) preferredByStock.put(stock, row);
        }

        Map<String, Product> productsByUniqueKey = new LinkedHashMap<>();
        int crossPdfDifferenceCount = 0;
        int price15FallbackCount = 0;

        for (CompanyPriceRow associateRow : associate.getRows()) {
            if (associateRow == null) continue;

            String stock = normalizeStock(associateRow.getStockNo());
            if (stock.isEmpty()) {
                result.incrementSkippedIncompleteRows();
                continue;
            }

            Integer price25 = associateRow.getPrice25();
            Integer price35 = associateRow.getPrice35();
            Integer price42 = associateRow.getPrice42();
            Integer price50 = associateRow.getPrice50();

            if (associateRow.getMrp() <= 0
                    || price25 == null
                    || price35 == null
                    || price42 == null
                    || price50 == null) {
                // Non-nutrition/application/promotion rows do not have the six
                // app price levels and therefore are not part of Product Manager.
                result.incrementSkippedIncompleteRows();
                continue;
            }

            CompanyPriceRow exactPreferred = preferredByStock.get(stock);
            Price15Resolution price15Resolution = resolvePrice15(
                    associateRow,
                    exactPreferred,
                    preferredRows
            );

            if (price15Resolution.price15 == null) {
                result.incrementSkippedIncompleteRows();
                result.addWarning(
                        "Stock " + stock + " (" + cleanProductName(associateRow.getProductName())
                                + ") was not imported because a reliable Preferred Customer Bronze/@15 price could not be found."
                );
                continue;
            }

            if (exactPreferred != null && hasSharedPriceDifference(associateRow, exactPreferred)) {
                crossPdfDifferenceCount++;
            }
            if (price15Resolution.usedFallback) {
                price15FallbackCount++;
            }

            String productName = firstNonEmpty(
                    associateRow.getProductName(),
                    price15Resolution.preferredRow == null
                            ? ""
                            : price15Resolution.preferredRow.getProductName()
            );
            if (productName.isEmpty()) {
                result.incrementSkippedIncompleteRows();
                continue;
            }

            String preferredCategory = price15Resolution.preferredRow == null
                    ? ""
                    : price15Resolution.preferredRow.getCategory();

            String category = chooseCategory(
                    associateRow.getCategory(),
                    preferredCategory,
                    categoryByStock.get(stock),
                    productName
            );

            Product product = new Product();
            product.setCategory(category);
            product.setName(cleanProductName(productName));
            product.setVp(associateRow.getVolumePoint());
            product.setFullPrice(associateRow.getMrp());
            product.setPrice15(price15Resolution.price15);
            product.setPrice25(price25);
            product.setPrice35(price35);
            product.setPrice42(price42);
            product.setPrice50(price50);
            product.setActive(true);
            product.setUpdatedAt(System.currentTimeMillis());

            // Product names are unique in the current DB schema. If a future
            // company PDF repeats a display name for different Stock Nos., keep
            // both by appending Stock No. only to the duplicate display name.
            String uniqueKey = normalizeName(product.getName());
            if (uniqueKey.isEmpty()) uniqueKey = "stock-" + stock;

            Product existing = productsByUniqueKey.get(uniqueKey);
            if (existing == null) {
                productsByUniqueKey.put(uniqueKey, product);
            } else if (!samePrices(existing, product)) {
                product.setName(product.getName() + " [" + stock + "]");
                productsByUniqueKey.put(uniqueKey + "-" + stock, product);
            }
        }

        if (crossPdfDifferenceCount > 0) {
            result.addWarning(
                    crossPdfDifferenceCount
                            + " row(s) showed different @25/@35 values between the parsed PDFs. "
                            + "They no longer block import: Associate PDF is used for @25/@35, while Preferred Customer PDF is used only for Bronze/@15."
            );
        }

        if (price15FallbackCount > 0) {
            result.addWarning(
                    price15FallbackCount
                            + " product(s) used smart Bronze/@15 group matching because the Preferred Customer PDF shares price blocks across flavour rows."
            );
        }

        List<Product> ordered = new ArrayList<>(productsByUniqueKey.values());
        Collections.sort(
                ordered,
                Comparator.comparing(
                        Product::getCategory,
                        String.CASE_INSENSITIVE_ORDER
                ).thenComparing(
                        Product::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        for (Product product : ordered) {
            result.addProduct(product);
        }

        if (result.getProductCount() == 0) {
            result.addError("No complete products with all six app price levels were found.");
        }

        return result;
    }

    /**
     * Preferred PDF contributes only Bronze/@15. Exact Stock No. is preferred.
     * If PDF extraction attached that stock to a neighbouring shared group, we
     * recover the correct Bronze value using the company price signature:
     * MRP + Associate @25 + Associate @35 (+ VP when available).
     */
    private static Price15Resolution resolvePrice15(
            CompanyPriceRow associateRow,
            CompanyPriceRow exactPreferred,
            List<CompanyPriceRow> preferredRows
    ) {
        if (associateRow == null) return new Price15Resolution(null, null, false);

        // Strong exact match: same stock + same MRP. Even if Silver/Gold differ,
        // Bronze belongs to this exact official stock and remains usable.
        if (exactPreferred != null
                && exactPreferred.getPrice15() != null
                && exactPreferred.getMrp() == associateRow.getMrp()) {
            return new Price15Resolution(
                    exactPreferred.getPrice15(),
                    exactPreferred,
                    false
            );
        }

        CompanyPriceRow best = null;
        int bestScore = Integer.MIN_VALUE;

        for (CompanyPriceRow preferredRow : preferredRows) {
            if (preferredRow == null || preferredRow.getPrice15() == null) continue;
            if (preferredRow.getMrp() != associateRow.getMrp()) continue;

            int score = 10;

            if (samePrice(associateRow.getPrice25(), preferredRow.getPrice25())) {
                score += 8;
            }
            if (samePrice(associateRow.getPrice35(), preferredRow.getPrice35())) {
                score += 8;
            }

            double vpDifference = Math.abs(
                    associateRow.getVolumePoint() - preferredRow.getVolumePoint()
            );
            if (associateRow.getVolumePoint() > 0d
                    && preferredRow.getVolumePoint() > 0d
                    && vpDifference < 0.011d) {
                score += 5;
            }

            if (sameFamilyAndPack(
                    associateRow.getProductName(),
                    preferredRow.getProductName()
            )) {
                score += 6;
            }

            if (score > bestScore) {
                bestScore = score;
                best = preferredRow;
            }
        }

        // MRP match is mandatory for fallback. A score of 18 normally means
        // MRP + either @25 or @35 matched; 24+ means a strong price group match.
        if (best != null && bestScore >= 18) {
            return new Price15Resolution(best.getPrice15(), best, true);
        }

        // Last safe exact-stock fallback: accept Bronze if Stock No. is exact
        // and the row exists, even when PDF text extraction lost its MRP field.
        if (exactPreferred != null && exactPreferred.getPrice15() != null) {
            return new Price15Resolution(
                    exactPreferred.getPrice15(),
                    exactPreferred,
                    true
            );
        }

        return new Price15Resolution(null, null, false);
    }

    private static boolean hasSharedPriceDifference(
            CompanyPriceRow associateRow,
            CompanyPriceRow preferredRow
    ) {
        if (associateRow == null || preferredRow == null) return false;

        boolean price25Comparable = associateRow.getPrice25() != null
                && preferredRow.getPrice25() != null;
        boolean price35Comparable = associateRow.getPrice35() != null
                && preferredRow.getPrice35() != null;

        return (price25Comparable
                && !associateRow.getPrice25().equals(preferredRow.getPrice25()))
                || (price35Comparable
                && !associateRow.getPrice35().equals(preferredRow.getPrice35()));
    }

    private static boolean samePrice(Integer first, Integer second) {
        return first != null && second != null && first.equals(second);
    }

    private static boolean sameFamilyAndPack(String first, String second) {
        String firstName = normalizeName(first);
        String secondName = normalizeName(second);
        if (firstName.isEmpty() || secondName.isEmpty()) return false;

        String firstFamily = familyKey(firstName);
        String secondFamily = familyKey(secondName);
        if (!firstFamily.equals(secondFamily)) return false;

        String firstPack = packKey(firstName);
        String secondPack = packKey(secondName);
        return firstPack.isEmpty()
                || secondPack.isEmpty()
                || firstPack.equals(secondPack);
    }

    private static String familyKey(String normalizedName) {
        String value = normalizedName;
        if (value.contains("formula 1")) return "formula 1";
        if (value.contains("protein powder")) return "protein powder";
        if (value.contains("afresh")) return "afresh";
        if (value.contains("dinoshake")) return "dinoshake";
        if (value.contains("liftoff")) return "liftoff";
        if (value.contains("shakemate") || value.contains("shake mate")) return "shakemate";

        return value
                .replaceAll(
                        "\\b(vanilla|chocolate|chocolicious|mango|orange|cream|strawberry|kulfi|banana|caramel|rose|kheer|paan|dates|ginger|elaichi|lemon|peach|cinnamon|kashmiri|kahwa|tulsi|basil|watermelon|unflavoured|unflavored|original)\\b",
                        " "
                )
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\s*(?:kg|g|ml|tab|tablet|tablets|caps|capsule|capsules|sachet|sachets|softgel|softgels)\\b", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String packKey(String normalizedName) {
        Matcher matcher = Pattern.compile(
                "\\b(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|tab|tablet|tablets|caps|capsule|capsules|sachet|sachets|softgel|softgels)\\b",
                Pattern.CASE_INSENSITIVE
        ).matcher(normalizedName);

        if (!matcher.find()) return "";
        return matcher.group(1) + normalizeUnit(matcher.group(2));
    }

    private static String normalizeUnit(String unit) {
        String value = unit == null ? "" : unit.toLowerCase(Locale.US);
        if (value.equals("tablet") || value.equals("tablets")) return "tab";
        if (value.equals("capsule") || value.equals("capsules")) return "caps";
        if (value.equals("sachets")) return "sachet";
        if (value.equals("softgels")) return "softgel";
        return value;
    }

    private static Map<String, String> extractCategoryByStock(String rawText) {
        Map<String, String> result = new HashMap<>();
        if (rawText == null || rawText.trim().isEmpty()) return result;

        String currentCategory = "";
        for (String rawLine : rawText.split("\\r?\\n")) {
            String line = cleanSpaces(rawLine);
            if (line.isEmpty()) continue;

            Matcher stockMatcher = STOCK_LINE.matcher(line);
            if (stockMatcher.matches()) {
                String stock = normalizeStock(stockMatcher.group(1));
                if (!stock.isEmpty() && !currentCategory.isEmpty()) {
                    result.put(stock, currentCategory);
                }
                continue;
            }

            String heading = detectCategoryHeading(line);
            if (!heading.isEmpty()) {
                currentCategory = heading;
            }
        }
        return result;
    }

    private static String detectCategoryHeading(String line) {
        String cleaned = cleanSpaces(line)
                .replace('’', '\'')
                .trim();
        String upper = cleaned.toUpperCase(Locale.US);

        if (upper.equals("WEIGHT MANAGEMENT PRODUCTS")
                || upper.equals("WEIGHT MANAGEMENT")) return "WEIGHT MANAGEMENT";
        if (upper.equals("ENERGY PRODUCTS")) return "ENERGY PRODUCTS";
        if (upper.equals("SPORTS NUTRITION")) return "SPORTS NUTRITION";
        if (upper.equals("CHILDREN'S HEALTH")) return "CHILDREN'S HEALTH";
        if (upper.equals("DIGESTIVE HEALTH")) return "DIGESTIVE HEALTH";
        if (upper.equals("BONE & JOINT HEALTH")) return "BONE & JOINT HEALTH";
        if (upper.equals("CARDIOVASCULAR HEALTH")) return "CARDIOVASCULAR HEALTH";
        if (upper.equals("ENHANCERS")) return "ENHANCERS";
        if (upper.equals("EYE HEALTH")) return "EYE HEALTH";
        if (upper.equals("MEN'S HEALTH")) return "MEN'S HEALTH";
        if (upper.equals("WOMEN'S HEALTH")) return "WOMEN'S HEALTH";
        if (upper.equals("BRAIN HEALTH") || upper.equals("VRITILIFE BRAIN HEALTH")) {
            return "BRAIN HEALTH";
        }
        if (upper.equals("IMMUNE HEALTH") || upper.equals("VRITILIFE IMMUNE HEALTH")) {
            return "IMMUNE HEALTH";
        }
        if (upper.equals("SKIN & BODY CARE") || upper.equals("VRITILIFE SKIN & BODY CARE")) {
            return "SKIN HEALTH";
        }
        if (upper.equals("SLEEP SUPPORT")) return "SLEEP SUPPORT";

        if (looksLikeGenericCategoryHeading(upper)) {
            return upper
                    .replaceFirst("\\s+PRODUCTS$", "")
                    .trim();
        }

        return "";
    }

    private static boolean looksLikeGenericCategoryHeading(String upper) {
        if (upper.length() < 3 || upper.length() > 48) return false;
        if (upper.matches(".*\\d.*")) return false;
        if (!upper.matches("[A-Z &'\\-/]+")) return false;

        String[] blocked = {
                "ASSOCIATE PRICE LIST",
                "PREFERRED CUSTOMER",
                "PREFERRED CUSTOMERS",
                "PRODUCT NAME",
                "STOCK NO",
                "VOLUME POINTS",
                "RETAIL PRICE",
                "EARN BASE",
                "BRONZE PREFERRED CUSTOMER",
                "SILVER PREFERRED CUSTOMER",
                "GOLD PREFERRED CUSTOMER",
                "NOTES",
                "APPLICATIONS",
                "ART OF PROMOTION"
        };
        for (String value : blocked) {
            if (upper.equals(value) || upper.startsWith(value + " ")) return false;
        }

        return upper.contains("HEALTH")
                || upper.contains("NUTRITION")
                || upper.contains("MANAGEMENT")
                || upper.contains("ENERGY")
                || upper.contains("CARE")
                || upper.contains("SUPPORT")
                || upper.contains("WELLNESS")
                || upper.endsWith(" PRODUCTS");
    }

    private static String chooseCategory(
            String associateCategory,
            String preferredCategory,
            String rawCategory,
            String productName
    ) {
        String category = normalizeCategory(associateCategory);
        if (category.isEmpty()) category = normalizeCategory(preferredCategory);
        if (category.isEmpty()) category = normalizeCategory(rawCategory);
        if (category.isEmpty()) category = inferCategoryFromName(productName);
        return category.isEmpty() ? "OTHER PRODUCTS" : category;
    }

    private static String normalizeCategory(String value) {
        String category = cleanSpaces(value)
                .toUpperCase(Locale.US)
                .replace('’', '\'')
                .trim();
        if (category.equals("WEIGHT MANAGEMENT PRODUCTS")) return "WEIGHT MANAGEMENT";
        if (category.equals("VRITILIFE SKIN & BODY CARE") || category.equals("SKIN & BODY CARE")) {
            return "SKIN HEALTH";
        }
        if (category.equals("VRITILIFE BRAIN HEALTH")) return "BRAIN HEALTH";
        if (category.equals("VRITILIFE IMMUNE HEALTH")) return "IMMUNE HEALTH";
        return category;
    }

    private static String inferCategoryFromName(String name) {
        String value = normalizeName(name);
        if (containsAny(value, "formula 1", "protein powder", "shakemate", "shake mate")) {
            return "WEIGHT MANAGEMENT";
        }
        if (containsAny(value, "male factor")) return "MEN'S HEALTH";
        if (containsAny(value, "woman s choice", "womans choice")) return "WOMEN'S HEALTH";
        if (containsAny(value, "brain health")) return "BRAIN HEALTH";
        if (containsAny(value, "immune health")) return "IMMUNE HEALTH";
        if (containsAny(value, "afresh", "liftoff")) return "ENERGY PRODUCTS";
        if (containsAny(value, "h24 hydrate", "h24 rebuild")) return "SPORTS NUTRITION";
        if (containsAny(value, "dinoshake")) return "CHILDREN'S HEALTH";
        if (containsAny(value, "activated fiber", "active fiber", "aloe", "probiotic", "triphala")) {
            return "DIGESTIVE HEALTH";
        }
        if (containsAny(value, "calcium", "joint support")) return "BONE & JOINT HEALTH";
        if (containsAny(value, "niteworks", "herbalifeline", "beta heart")) {
            return "CARDIOVASCULAR HEALTH";
        }
        if (containsAny(value, "multivitamin", "cell activator", "cell u loss", "herbal control")) {
            return "ENHANCERS";
        }
        if (containsAny(value, "ocular defense")) return "EYE HEALTH";
        if (containsAny(value, "skin booster", "facial cleanser", "facial toner", "facial serum", "moisturizer")) {
            return "SKIN HEALTH";
        }
        if (containsAny(value, "sleep enhance")) return "SLEEP SUPPORT";
        return "";
    }

    private static boolean samePrices(Product first, Product second) {
        return first.getFullPrice() == second.getFullPrice()
                && first.getPrice15() == second.getPrice15()
                && first.getPrice25() == second.getPrice25()
                && first.getPrice35() == second.getPrice35()
                && first.getPrice42() == second.getPrice42()
                && first.getPrice50() == second.getPrice50();
    }

    private static String cleanProductName(String value) {
        return cleanSpaces(value)
                .replace("™", "")
                .replace("®", "")
                .replace("–", "-")
                .replace("—", "-")
                .trim();
    }

    private static String normalizeName(String value) {
        return cleanProductName(value)
                .toLowerCase(Locale.US)
                .replace("personalised", "personalized")
                .replace("fibre", "fiber")
                .replace("defence", "defense")
                .replace("formula-1", "formula 1")
                .replace("formula1", "formula 1")
                .replace("dino shake", "dinoshake")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean containsAny(String source, String... terms) {
        if (source == null) return false;
        for (String term : terms) {
            if (term != null && !term.isEmpty() && source.contains(term)) return true;
        }
        return false;
    }

    private static String normalizeStock(String value) {
        return value == null
                ? ""
                : value.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
    }

    private static String firstNonEmpty(String first, String second) {
        String a = first == null ? "" : first.trim();
        if (!a.isEmpty()) return a;
        return second == null ? "" : second.trim();
    }

    private static String cleanSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static class Price15Resolution {
        private final Integer price15;
        private final CompanyPriceRow preferredRow;
        private final boolean usedFallback;

        private Price15Resolution(
                Integer price15,
                CompanyPriceRow preferredRow,
                boolean usedFallback
        ) {
            this.price15 = price15;
            this.preferredRow = preferredRow;
            this.usedFallback = usedFallback;
        }
    }
}
