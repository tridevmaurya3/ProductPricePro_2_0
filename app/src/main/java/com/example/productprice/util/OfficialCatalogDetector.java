package com.example.productprice.util;

import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.CompanyPriceRow;
import com.example.productprice.model.OfficialCatalogCandidate;
import com.example.productprice.model.OfficialCatalogSyncPlan;
import com.example.productprice.model.Product;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds official PDF products that are not represented in the user's current
 * simplified catalogue. Same-price flavours are grouped first, so an existing
 * logical product such as Formula 1-500 gms covers all of its same-price 500 g
 * flavours. A new pack such as Formula 1-750 gms is detected independently.
 */
public final class OfficialCatalogDetector {

    private static final Pattern PACK_PATTERN = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(kg|gms|gm|grams|gram|g|ml|ltr|litre|litres|tablet|tablets|tab|tabs|capsule|capsules|caps|sachet|sachets|softgel|softgels)\\b"
    );

    private static final Set<String> FLAVOUR_WORDS = new HashSet<>(Arrays.asList(
            "vanilla", "chocolate", "chocolicious", "mango", "orange", "cream",
            "strawberry", "kulfi", "banana", "caramel", "rose", "kheer", "paan",
            "dates", "ginger", "elaichi", "lemon", "peach", "cinnamon", "kashmiri",
            "kahwa", "tulsi", "basil", "watermelon", "unflavoured", "unflavored",
            "original"
    ));

    private OfficialCatalogDetector() {
    }

    public static OfficialCatalogSyncPlan buildPlan(
            List<Product> existingProducts,
            List<String> existingCategories,
            CompanyPriceDocument associateDocument,
            CompanyPriceDocument preferredDocument,
            String effectiveDate
    ) {
        OfficialCatalogSyncPlan plan = new OfficialCatalogSyncPlan(effectiveDate);

        if (associateDocument == null || preferredDocument == null) {
            plan.addWarning("Both official PDFs are required for catalogue discovery.");
            return plan;
        }

        List<MergedRow> mergedRows = mergeOfficialRows(
                associateDocument,
                preferredDocument,
                plan
        );

        if (mergedRows.isEmpty()) {
            plan.addWarning("No complete official products were available for catalogue discovery.");
            return plan;
        }

        Map<String, OfficialGroup> grouped = new LinkedHashMap<>();
        for (MergedRow row : mergedRows) {
            String family = canonicalFamily(normalizeName(row.productName));
            if (family.isEmpty()) {
                family = fallbackFamily(normalizeName(row.productName));
            }

            String pack = groupPackKey(family, row.productName);
            String category = resolveCategory(row.category, existingCategories);
            String groupKey = category
                    + "|" + family
                    + "|" + pack
                    + "|" + row.priceSignature();

            OfficialGroup group = grouped.get(groupKey);
            if (group == null) {
                group = new OfficialGroup(
                        groupKey,
                        family,
                        pack,
                        category,
                        row
                );
                grouped.put(groupKey, group);
            } else {
                group.addVariant(row.stockNo, row.productName);
            }
        }

        Map<String, List<OfficialGroup>> groupsByBucket = new LinkedHashMap<>();
        for (OfficialGroup group : grouped.values()) {
            groupsByBucket
                    .computeIfAbsent(group.coverageBucket(), key -> new ArrayList<>())
                    .add(group);
        }

        List<Product> safeExistingProducts = existingProducts == null
                ? Collections.emptyList()
                : existingProducts;

        for (Map.Entry<String, List<OfficialGroup>> bucketEntry : groupsByBucket.entrySet()) {
            List<OfficialGroup> bucketGroups = bucketEntry.getValue();
            List<Product> bucketProducts = matchingExistingProducts(
                    bucketEntry.getKey(),
                    safeExistingProducts
            );

            markCoveredGroups(bucketGroups, bucketProducts);

            for (OfficialGroup group : bucketGroups) {
                if (group.covered) {
                    continue;
                }

                OfficialCatalogCandidate candidate = group.toCandidate();
                plan.addMissingProduct(candidate);

                if (!categoryExists(candidate.getCategory(), existingCategories)) {
                    plan.addNewCategory(candidate.getCategory());
                }
            }
        }

        return plan;
    }

    private static List<MergedRow> mergeOfficialRows(
            CompanyPriceDocument associateDocument,
            CompanyPriceDocument preferredDocument,
            OfficialCatalogSyncPlan plan
    ) {
        Map<String, CompanyPriceRow> preferredByStock = new HashMap<>();
        for (CompanyPriceRow row : preferredDocument.getRows()) {
            if (row == null) continue;
            String stock = normalizeStock(row.getStockNo());
            if (!stock.isEmpty()) preferredByStock.put(stock, row);
        }

        List<MergedRow> result = new ArrayList<>();

        for (CompanyPriceRow associateRow : associateDocument.getRows()) {
            if (associateRow == null) continue;

            String stock = normalizeStock(associateRow.getStockNo());
            CompanyPriceRow preferredRow = preferredByStock.get(stock);
            if (stock.isEmpty() || preferredRow == null) continue;

            if (associateRow.getPrice25() == null
                    || preferredRow.getPrice25() == null
                    || !associateRow.getPrice25().equals(preferredRow.getPrice25())
                    || associateRow.getPrice35() == null
                    || preferredRow.getPrice35() == null
                    || !associateRow.getPrice35().equals(preferredRow.getPrice35())) {
                continue;
            }

            Integer price15 = preferredRow.getPrice15();
            Integer price25 = associateRow.getPrice25();
            Integer price35 = associateRow.getPrice35();
            Integer price42 = associateRow.getPrice42();
            Integer price50 = associateRow.getPrice50();

            if (associateRow.getMrp() <= 0
                    || price15 == null
                    || price25 == null
                    || price35 == null
                    || price42 == null
                    || price50 == null) {
                continue;
            }

            String productName = safe(associateRow.getProductName());
            if (productName.isEmpty()) productName = safe(preferredRow.getProductName());

            String category = safe(associateRow.getCategory());
            if (category.isEmpty()) category = safe(preferredRow.getCategory());

            result.add(
                    new MergedRow(
                            stock,
                            productName,
                            category,
                            Math.max(
                                    associateRow.getVolumePoint(),
                                    preferredRow.getVolumePoint()
                            ),
                            associateRow.getMrp(),
                            price15,
                            price25,
                            price35,
                            price42,
                            price50
                    )
            );
        }

        if (result.isEmpty()) {
            plan.addWarning("Associate and Preferred Customer PDFs had no complete common product rows.");
        }

        return result;
    }

    private static void markCoveredGroups(
            List<OfficialGroup> groups,
            List<Product> products
    ) {
        if (groups == null || groups.isEmpty()) return;
        if (products == null || products.isEmpty()) return;

        Set<Integer> usedProductIndexes = new HashSet<>();

        // First use explicit flavour/product names. This correctly associates
        // entries such as Afresh-Tulsi with the Tulsi price group.
        for (int productIndex = 0; productIndex < products.size(); productIndex++) {
            Product product = products.get(productIndex);
            if (product == null || !hasSpecificVariantWords(product.getName())) continue;

            int bestGroup = bestNameGroup(product, groups, true);
            if (bestGroup >= 0) {
                groups.get(bestGroup).covered = true;
                usedProductIndexes.add(productIndex);
            }
        }

        // A generic logical app product represents one same-price official
        // group. Prefer the largest flavour group when several price groups
        // exist in the same family/pack bucket.
        List<OfficialGroup> remainingGroups = new ArrayList<>();
        for (OfficialGroup group : groups) {
            if (!group.covered) remainingGroups.add(group);
        }
        remainingGroups.sort(
                Comparator.comparingInt(OfficialGroup::variantCount).reversed()
        );

        for (int productIndex = 0; productIndex < products.size(); productIndex++) {
            if (usedProductIndexes.contains(productIndex)) continue;
            if (remainingGroups.isEmpty()) break;

            Product product = products.get(productIndex);
            if (product == null) continue;

            int bestIndex = bestNameGroup(product, remainingGroups, false);
            if (bestIndex < 0) bestIndex = 0;

            OfficialGroup covered = remainingGroups.remove(bestIndex);
            covered.covered = true;
        }
    }

    private static int bestNameGroup(
            Product product,
            List<OfficialGroup> groups,
            boolean requirePositiveSimilarity
    ) {
        double bestScore = -1d;
        int bestIndex = -1;

        for (int index = 0; index < groups.size(); index++) {
            OfficialGroup group = groups.get(index);
            if (group.covered) continue;

            double score = nameSimilarity(product.getName(), group.representativeName);
            for (OfficialCatalogCandidate.Variant variant : group.variants) {
                score = Math.max(
                        score,
                        nameSimilarity(product.getName(), variant.getProductName())
                );
            }

            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }

        if (requirePositiveSimilarity && bestScore < 0.35d) return -1;
        return bestIndex;
    }

    private static List<Product> matchingExistingProducts(
            String coverageBucket,
            List<Product> existingProducts
    ) {
        List<Product> result = new ArrayList<>();

        for (Product product : existingProducts) {
            if (product == null || safe(product.getName()).isEmpty()) continue;

            String normalized = normalizeName(product.getName());
            String family = canonicalFamily(normalized);
            if (family.isEmpty()) family = fallbackFamily(normalized);

            String bucket = family + "|" + coveragePackKey(family, product.getName());
            if (coverageBucket.equals(bucket)) {
                result.add(product);
            }
        }

        return result;
    }

    private static String resolveCategory(
            String officialCategory,
            List<String> existingCategories
    ) {
        String normalized = normalizeCategory(officialCategory);

        if (existingCategories != null) {
            for (String existing : existingCategories) {
                if (normalizeCategory(existing).equals(normalized)) {
                    return safe(existing);
                }
            }
        }

        return normalized.isEmpty() ? "OTHER PRODUCTS" : normalized;
    }

    private static boolean categoryExists(
            String category,
            List<String> existingCategories
    ) {
        String target = normalizeCategory(category);
        if (existingCategories == null) return false;

        for (String existing : existingCategories) {
            if (normalizeCategory(existing).equals(target)) return true;
        }
        return false;
    }

    private static String normalizeCategory(String value) {
        String category = safe(value)
                .toUpperCase(Locale.US)
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .trim();

        if (category.contains("WEIGHT MANAGEMENT")) return "WEIGHT MANAGEMENT";
        if (category.contains("MEN'S HEALTH")) return "MEN'S HEALTH";
        if (category.contains("WOMEN'S HEALTH")) return "WOMEN'S HEALTH";
        if (category.contains("BRAIN HEALTH")) return "BRAIN HEALTH";
        if (category.contains("IMMUNE HEALTH")) return "IMMUNE HEALTH";
        if (category.contains("ENERGY")) return "ENERGY PRODUCTS";
        if (category.contains("SPORTS")) return "SPORTS NUTRITION";
        if (category.contains("CHILDREN")) return "CHILDREN'S HEALTH";
        if (category.contains("DIGESTIVE")) return "DIGESTIVE HEALTH";
        if (category.contains("BONE") || category.contains("JOINT")) return "BONE & JOINT HEALTH";
        if (category.contains("CARDIOVASCULAR")) return "CARDIOVASCULAR HEALTH";
        if (category.contains("ENHANCER")) return "ENHANCERS";
        if (category.contains("EYE")) return "EYE HEALTH";
        if (category.contains("SKIN")) return "SKIN HEALTH";
        if (category.contains("SLEEP")) return "SLEEP SUPPORT";
        if (category.contains("APPLICATION")) return "APPLICATIONS";
        if (category.contains("ART OF PROMOTION")) return "ART OF PROMOTION";
        return category;
    }

    private static String groupPackKey(String family, String productName) {
        // Standard Afresh flavours mix 40 g and 50 g in one shared price group.
        // Price signature still keeps Tulsi or any future different-price group separate.
        if ("afresh".equals(family)) return "afresh-mixed-pack";
        return packSignature(productName);
    }

    private static String coveragePackKey(String family, String productName) {
        if ("afresh".equals(family)) return "afresh-mixed-pack";
        return packSignature(productName);
    }

    private static String packSignature(String value) {
        List<String> packs = new ArrayList<>();
        Matcher matcher = PACK_PATTERN.matcher(normalizePackText(value));
        while (matcher.find()) {
            packs.add(matcher.group(1) + normalizeUnit(matcher.group(2)));
        }
        Collections.sort(packs);
        return packs.isEmpty() ? "no-pack" : join(packs, "+");
    }

    private static String prettyPrimaryPack(String productName) {
        Matcher matcher = PACK_PATTERN.matcher(normalizePackText(productName));
        if (!matcher.find()) return "";

        String number = matcher.group(1);
        String unit = normalizeUnit(matcher.group(2));
        if ("g".equals(unit)) return number + " gms";
        if ("tab".equals(unit)) return number + " Tablets";
        if ("caps".equals(unit)) return number + " Capsules";
        if ("sachet".equals(unit)) return number + " Sachets";
        if ("softgel".equals(unit)) return number + " Softgels";
        return number + " " + unit;
    }

    private static String normalizePackText(String value) {
        return safe(value)
                .toLowerCase(Locale.US)
                .replace("grams", "g")
                .replace("gram", "g")
                .replace("gms", "g")
                .replace("gm", "g")
                .replace("tablets", "tab")
                .replace("tablet", "tab")
                .replace("tabs", "tab")
                .replace("capsules", "caps")
                .replace("capsule", "caps")
                .replace("sachets", "sachet")
                .replace("softgels", "softgel");
    }

    private static String normalizeUnit(String unit) {
        String value = safe(unit).toLowerCase(Locale.US);
        if (value.equals("gms") || value.equals("gm") || value.equals("grams") || value.equals("gram")) return "g";
        if (value.equals("tablets") || value.equals("tablet") || value.equals("tabs")) return "tab";
        if (value.equals("capsules") || value.equals("capsule")) return "caps";
        if (value.equals("sachets")) return "sachet";
        if (value.equals("softgels")) return "softgel";
        return value;
    }

    private static String canonicalFamily(String normalizedName) {
        String value = safe(normalizedName);
        if (value.contains("formula 1")) return "formula 1";
        if (value.contains("afresh")) return "afresh";
        if (value.contains("dinoshake")) return "dinoshake";
        if (value.contains("protein powder")) return "protein powder";
        if (value.contains("shake mate") || value.contains("shakemate")) return "shakemate";
        if (value.contains("male factor")) return "male factor";
        if (value.contains("woman s choice") || value.contains("womans choice")) return "womans choice";
        if (value.contains("brain health")) return "brain health";
        if (value.contains("immune health")) return "immune health";
        if (value.contains("liftoff")) return "liftoff";
        if (value.contains("h24 hydrate")) return "h24 hydrate";
        if (value.contains("h24 rebuild")) return "h24 rebuild";
        if (value.contains("skin booster")) return "skin booster";
        if (value.contains("facial cleanser")) return "facial cleanser";
        if (value.contains("facial toner")) return "facial toner";
        if (value.contains("facial serum")) return "facial serum";
        if (value.contains("moisturizer")) return "moisturizer";
        if (value.contains("activated fiber")) return "activated fiber";
        if (value.contains("active fiber complex")) return "active fiber complex";
        if (value.contains("aloe plus")) return "aloe plus";
        if (value.contains("aloe concentrate")) return "aloe concentrate";
        if (value.contains("simply probiotic")) return "simply probiotic";
        if (value.contains("triphala")) return "triphala";
        if (value.contains("calcium")) return "calcium";
        if (value.contains("joint support")) return "joint support";
        if (value.contains("niteworks")) return "niteworks";
        if (value.contains("herbalifeline")) return "herbalifeline";
        if (value.contains("beta heart")) return "beta heart";
        if (value.contains("multivitamin mineral")) return "multivitamin mineral herbal";
        if (value.contains("cell activator")) return "cell activator";
        if (value.contains("cell u loss")) return "cell u loss";
        if (value.contains("herbal control")) return "herbal control";
        if (value.contains("ocular defense")) return "ocular defense";
        if (value.contains("sleep enhance")) return "sleep enhance";
        return "";
    }

    private static String fallbackFamily(String normalizedName) {
        String value = safe(normalizedName)
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\s*(?:kg|g|ml|tab|caps|sachet|softgel)\\b", " ");

        StringBuilder builder = new StringBuilder();
        for (String token : value.split("\\s+")) {
            if (token.isEmpty() || FLAVOUR_WORDS.contains(token)) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(token);
        }
        return builder.toString().trim();
    }

    private static String normalizeName(String value) {
        String normalized = safe(value)
                .toLowerCase(Locale.US)
                .replace("®", "")
                .replace("™", "")
                .replace("&", " and ")
                .replace("’", "'")
                .replace("–", "-")
                .replace("—", "-")
                .replace("formula-1", "formula 1")
                .replace("formula1", "formula 1")
                .replace("dino shake", "dinoshake")
                .replace("personalized protein powder", "protein powder")
                .replace("personalised protein powder", "protein powder")
                .replace("activated fibre", "activated fiber")
                .replace("active fibre", "active fiber")
                .replace("ocular defence", "ocular defense")
                .replace("cell-u-loss", "cell u loss")
                .replace("celluloss", "cell u loss")
                .replace("nite works", "niteworks");

        return normalized
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean hasSpecificVariantWords(String value) {
        String normalized = normalizeName(value);
        for (String token : normalized.split("\\s+")) {
            if (FLAVOUR_WORDS.contains(token)) return true;
        }
        return false;
    }

    private static double nameSimilarity(String first, String second) {
        Set<String> left = tokens(normalizeName(first));
        Set<String> right = tokens(normalizeName(second));
        if (left.isEmpty() || right.isEmpty()) return 0d;

        int intersection = 0;
        for (String token : left) if (right.contains(token)) intersection++;
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0d : intersection / (double) union;
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        for (String token : safe(value).split("\\s+")) {
            if (!token.isEmpty()) result.add(token);
        }
        return result;
    }

    private static String logicalName(
            String family,
            String representative,
            int variantCount
    ) {
        if (variantCount <= 1) return safe(representative);

        String pack = prettyPrimaryPack(representative);
        if ("formula 1".equals(family)) {
            return pack.isEmpty() ? "Formula 1" : "Formula 1-" + pack;
        }
        if ("dinoshake".equals(family)) {
            return pack.isEmpty() ? "Dinoshake" : "Dinoshake-" + pack;
        }
        if ("protein powder".equals(family)) {
            return pack.isEmpty() ? "Protein Powder" : "Protein Powder-" + pack;
        }
        if ("afresh".equals(family)) {
            return "Afresh";
        }
        return safe(representative);
    }

    private static String normalizeStock(String value) {
        return safe(value).toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
    }

    private static String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(separator);
            builder.append(value);
        }
        return builder.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class MergedRow {
        private final String stockNo;
        private final String productName;
        private final String category;
        private final double volumePoint;
        private final int fullPrice;
        private final int price15;
        private final int price25;
        private final int price35;
        private final int price42;
        private final int price50;

        private MergedRow(
                String stockNo,
                String productName,
                String category,
                double volumePoint,
                int fullPrice,
                int price15,
                int price25,
                int price35,
                int price42,
                int price50
        ) {
            this.stockNo = stockNo;
            this.productName = productName;
            this.category = category;
            this.volumePoint = volumePoint;
            this.fullPrice = fullPrice;
            this.price15 = price15;
            this.price25 = price25;
            this.price35 = price35;
            this.price42 = price42;
            this.price50 = price50;
        }

        private String priceSignature() {
            return fullPrice + ":" + price15 + ":" + price25 + ":"
                    + price35 + ":" + price42 + ":" + price50;
        }
    }

    private static class OfficialGroup {
        private final String groupKey;
        private final String family;
        private final String packKey;
        private final String category;
        private final String representativeName;
        private final double volumePoint;
        private final int fullPrice;
        private final int price15;
        private final int price25;
        private final int price35;
        private final int price42;
        private final int price50;
        private final List<OfficialCatalogCandidate.Variant> variants = new ArrayList<>();
        private boolean covered;

        private OfficialGroup(
                String groupKey,
                String family,
                String packKey,
                String category,
                MergedRow row
        ) {
            this.groupKey = groupKey;
            this.family = family;
            this.packKey = packKey;
            this.category = category;
            this.representativeName = row.productName;
            this.volumePoint = row.volumePoint;
            this.fullPrice = row.fullPrice;
            this.price15 = row.price15;
            this.price25 = row.price25;
            this.price35 = row.price35;
            this.price42 = row.price42;
            this.price50 = row.price50;
            addVariant(row.stockNo, row.productName);
        }

        private void addVariant(String stockNo, String productName) {
            variants.add(new OfficialCatalogCandidate.Variant(stockNo, productName));
        }

        private int variantCount() {
            return variants.size();
        }

        private String coverageBucket() {
            return family + "|" + ("afresh".equals(family) ? "afresh-mixed-pack" : packKey);
        }

        private OfficialCatalogCandidate toCandidate() {
            return new OfficialCatalogCandidate(
                    groupKey,
                    category,
                    logicalName(family, representativeName, variants.size()),
                    volumePoint,
                    fullPrice,
                    price15,
                    price25,
                    price35,
                    price42,
                    price50,
                    variants
            );
        }
    }
}
