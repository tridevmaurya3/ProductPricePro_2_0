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
 * Builds a clean, complete app catalogue directly from the two official PDF
 * price lists. No legacy app price is reused. Each official Stock No. becomes
 * one product so newly launched flavours/packs are automatically included.
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
        for (CompanyPriceRow row : preferred.getRows()) {
            if (row == null) continue;
            String stock = normalizeStock(row.getStockNo());
            if (!stock.isEmpty()) preferredByStock.put(stock, row);
        }

        Map<String, Product> productsByUniqueKey = new LinkedHashMap<>();
        int sharedConflictCount = 0;

        for (CompanyPriceRow associateRow : associate.getRows()) {
            if (associateRow == null) continue;

            String stock = normalizeStock(associateRow.getStockNo());
            if (stock.isEmpty()) {
                result.incrementSkippedIncompleteRows();
                continue;
            }

            CompanyPriceRow preferredRow = preferredByStock.get(stock);
            if (preferredRow == null) {
                result.incrementSkippedIncompleteRows();
                result.addWarning(
                        "Stock " + stock
                                + " is in Associate PDF but has no Preferred Customer row, so it was not imported because Price@15 is unavailable."
                );
                continue;
            }

            Integer price15 = preferredRow.getPrice15();
            Integer price25 = associateRow.getPrice25();
            Integer price35 = associateRow.getPrice35();
            Integer price42 = associateRow.getPrice42();
            Integer price50 = associateRow.getPrice50();

            boolean sharedPricesMatch = price25 != null
                    && preferredRow.getPrice25() != null
                    && price25.equals(preferredRow.getPrice25())
                    && price35 != null
                    && preferredRow.getPrice35() != null
                    && price35.equals(preferredRow.getPrice35());

            if (!sharedPricesMatch) {
                sharedConflictCount++;
                continue;
            }

            if (associateRow.getMrp() <= 0
                    || price15 == null
                    || price25 == null
                    || price35 == null
                    || price42 == null
                    || price50 == null) {
                result.incrementSkippedIncompleteRows();
                continue;
            }

            String productName = firstNonEmpty(
                    associateRow.getProductName(),
                    preferredRow.getProductName()
            );
            if (productName.isEmpty()) {
                result.incrementSkippedIncompleteRows();
                continue;
            }

            String category = chooseCategory(
                    associateRow.getCategory(),
                    preferredRow.getCategory(),
                    categoryByStock.get(stock),
                    productName
            );

            Product product = new Product();
            product.setCategory(category);
            product.setName(cleanProductName(productName));
            product.setVp(Math.max(
                    associateRow.getVolumePoint(),
                    preferredRow.getVolumePoint()
            ));
            product.setFullPrice(associateRow.getMrp());
            product.setPrice15(price15);
            product.setPrice25(price25);
            product.setPrice35(price35);
            product.setPrice42(price42);
            product.setPrice50(price50);
            product.setActive(true);
            product.setUpdatedAt(System.currentTimeMillis());

            String uniqueKey = normalizeName(product.getName());
            if (uniqueKey.isEmpty()) uniqueKey = "stock-" + stock;

            Product existing = productsByUniqueKey.get(uniqueKey);
            if (existing == null) {
                productsByUniqueKey.put(uniqueKey, product);
            } else if (!samePrices(existing, product)) {
                // Keep both official rows if names collide but prices differ.
                product.setName(product.getName() + " [" + stock + "]");
                productsByUniqueKey.put(uniqueKey + "-" + stock, product);
            }
        }

        if (sharedConflictCount > 0) {
            result.addError(
                    sharedConflictCount
                            + " product(s) have different @25/@35 values between the two PDFs. Catalog replacement is blocked."
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

        // Future company categories: keep a clean all-uppercase section heading.
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
}
