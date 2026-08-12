package com.example.productprice.util;

import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.CompanyPriceRow;
import com.example.productprice.model.OfficialPriceUpdate;
import com.example.productprice.model.Product;
import com.example.productprice.model.SmartPriceImportPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * Safely maps the user's intentionally simplified catalogue to the official
 * company PDFs. The app can keep one logical item such as Formula 1-500 gms
 * even when the PDF contains many flavour Stock Nos. with the same prices.
 */
public final class SmartCompanyPriceMatcher {

    private static final double SAFE_MATCH_THRESHOLD = 0.58d;
    private static final double AMBIGUITY_WINDOW = 0.07d;

    private static final Pattern PACK_PATTERN = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(kg|gms|gm|grams|gram|g|ml|ltr|litre|litres|tablet|tablets|tab|tabs|capsule|capsules|caps|sachet|sachets|softgel|softgels)\\b"
    );

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "herbalife", "nutrition", "nutritional", "product", "products",
            "powder", "mix", "drink", "instant", "new", "plus",
            "tablet", "tablets", "tab", "tabs", "capsule", "capsules", "caps",
            "sachet", "sachets", "softgel", "softgels", "pack", "packs",
            "g", "gm", "gms", "gram", "grams", "ml", "kg", "with", "and", "the"
    ));

    private static final Set<String> FLAVOUR_WORDS = new HashSet<>(Arrays.asList(
            "vanilla", "chocolate", "chocolicious", "mango", "orange", "cream",
            "strawberry", "kulfi", "banana", "caramel", "rose", "kheer", "paan",
            "dates", "ginger", "elaichi", "lemon", "peach", "cinnamon", "kashmiri",
            "kahwa", "tulsi", "basil", "watermelon", "unflavoured", "unflavored",
            "original", "flavour", "flavor"
    ));

    private SmartCompanyPriceMatcher() {
    }

    public static SmartPriceImportPlan buildPlan(
            List<Product> appProducts,
            CompanyPriceDocument associateDocument,
            CompanyPriceDocument preferredDocument,
            String effectiveDate
    ) {
        SmartPriceImportPlan plan = new SmartPriceImportPlan(effectiveDate);

        if (associateDocument == null || preferredDocument == null) {
            plan.addConflict("Both validated official PDFs are required before matching products.");
            return plan;
        }

        List<MergedCompanyPrice> companyPrices = mergeOfficialRows(
                associateDocument,
                preferredDocument,
                plan
        );

        if (companyPrices.isEmpty()) {
            plan.addConflict("No complete company price rows were available for safe matching.");
            return plan;
        }

        if (appProducts == null || appProducts.isEmpty()) {
            plan.addConflict("No products are currently stored in the app.");
            return plan;
        }

        for (Product product : appProducts) {
            if (product == null || product.getId() <= 0 || safe(product.getName()).isEmpty()) {
                continue;
            }

            MatchDecision decision = findBestMatch(product, companyPrices);

            if (decision.conflictMessage != null) {
                plan.addConflict(decision.conflictMessage);
                continue;
            }

            if (decision.best == null || decision.score < SAFE_MATCH_THRESHOLD) {
                plan.addUnmatchedProduct(product.getName());
                continue;
            }

            MergedCompanyPrice match = decision.best;

            plan.addMatchedUpdate(
                    new OfficialPriceUpdate(
                            product.getId(),
                            product.getName(),
                            match.productName,
                            match.stockNo,
                            decision.score,
                            product.getFullPrice(),
                            product.getPrice15(),
                            product.getPrice25(),
                            product.getPrice35(),
                            product.getPrice42(),
                            product.getPrice50(),
                            match.fullPrice,
                            match.price15,
                            match.price25,
                            match.price35,
                            match.price42,
                            match.price50
                    )
            );
        }

        return plan;
    }

    private static List<MergedCompanyPrice> mergeOfficialRows(
            CompanyPriceDocument associateDocument,
            CompanyPriceDocument preferredDocument,
            SmartPriceImportPlan plan
    ) {
        Map<String, CompanyPriceRow> preferredByStock = new HashMap<>();

        for (CompanyPriceRow row : preferredDocument.getRows()) {
            String stock = normalizeStock(row == null ? null : row.getStockNo());
            if (!stock.isEmpty()) {
                preferredByStock.put(stock, row);
            }
        }

        Map<String, MergedCompanyPrice> merged = new LinkedHashMap<>();

        for (CompanyPriceRow associateRow : associateDocument.getRows()) {
            if (associateRow == null) {
                continue;
            }

            String stock = normalizeStock(associateRow.getStockNo());
            if (stock.isEmpty()) {
                continue;
            }

            CompanyPriceRow preferredRow = preferredByStock.get(stock);
            if (preferredRow == null) {
                continue;
            }

            if (!sameNullablePrice(associateRow.getPrice25(), preferredRow.getPrice25())
                    || !sameNullablePrice(associateRow.getPrice35(), preferredRow.getPrice35())) {
                plan.addConflict(
                        "Stock " + stock + " has different 25% or 35% prices between the two PDFs."
                );
                continue;
            }

            Integer p15 = preferredRow.getPrice15();
            Integer p25 = associateRow.getPrice25();
            Integer p35 = associateRow.getPrice35();
            Integer p42 = associateRow.getPrice42();
            Integer p50 = associateRow.getPrice50();

            if (associateRow.getMrp() <= 0
                    || p15 == null || p25 == null || p35 == null || p42 == null || p50 == null) {
                continue;
            }

            String productName = safe(associateRow.getProductName());
            if (productName.isEmpty()) {
                productName = safe(preferredRow.getProductName());
            }

            merged.put(
                    stock,
                    new MergedCompanyPrice(
                            stock,
                            productName,
                            Math.max(associateRow.getVolumePoint(), preferredRow.getVolumePoint()),
                            associateRow.getMrp(),
                            p15,
                            p25,
                            p35,
                            p42,
                            p50
                    )
            );
        }

        return new ArrayList<>(merged.values());
    }

    private static MatchDecision findBestMatch(
            Product product,
            List<MergedCompanyPrice> companyPrices
    ) {
        List<ScoredCandidate> scored = new ArrayList<>();

        for (MergedCompanyPrice companyPrice : companyPrices) {
            if (rejectKnownVariant(product, companyPrice)) {
                continue;
            }

            double score = scoreMatch(product, companyPrice);
            if (score > 0d) {
                scored.add(new ScoredCandidate(companyPrice, score));
            }
        }

        if (scored.isEmpty()) {
            return new MatchDecision(null, 0d, null);
        }

        Collections.sort(scored, (left, right) -> Double.compare(right.score, left.score));

        ScoredCandidate top = scored.get(0);
        if (top.score < SAFE_MATCH_THRESHOLD) {
            return new MatchDecision(null, top.score, null);
        }

        List<ScoredCandidate> nearTop = new ArrayList<>();
        for (ScoredCandidate candidate : scored) {
            if (candidate.score >= SAFE_MATCH_THRESHOLD
                    && top.score - candidate.score <= AMBIGUITY_WINDOW) {
                nearTop.add(candidate);
            }
        }

        if (nearTop.size() > 1 && hasDifferentPriceSets(nearTop)) {
            return new MatchDecision(
                    null,
                    top.score,
                    "Ambiguous company match for " + product.getName()
                            + ". Similar PDF variants have different prices, so this product was left unchanged."
            );
        }

        return new MatchDecision(top.companyPrice, top.score, null);
    }

    private static boolean rejectKnownVariant(
            Product appProduct,
            MergedCompanyPrice companyPrice
    ) {
        String app = normalizeName(appProduct == null ? null : appProduct.getName());
        String company = normalizeName(companyPrice == null ? null : companyPrice.productName);

        if (!"afresh".equals(canonicalFamily(app))
                || !"afresh".equals(canonicalFamily(company))) {
            return false;
        }

        boolean appTulsi = containsAny(app, "tulsi", "basil");
        boolean companyTulsi = containsAny(company, "tulsi", "basil");

        if (appTulsi) {
            return !companyTulsi;
        }

        // The user's generic Afresh item represents the standard merged-price
        // flavour group. Tulsi has its own separate company price row, so it must
        // never become the representative match for the generic Afresh item.
        return companyTulsi;
    }

    private static double scoreMatch(
            Product appProduct,
            MergedCompanyPrice companyPrice
    ) {
        String appNormalized = normalizeName(appProduct.getName());
        String companyNormalized = normalizeName(companyPrice.productName);

        Set<String> appTokens = coreTokens(appNormalized, false);
        Set<String> companyTokens = coreTokens(companyNormalized, false);

        if (appTokens.isEmpty() || companyTokens.isEmpty()) {
            return 0d;
        }

        double score = tokenSimilarity(appTokens, companyTokens) * 0.56d;

        String appFamily = canonicalFamily(appNormalized);
        String companyFamily = canonicalFamily(companyNormalized);
        boolean sameFamily = !appFamily.isEmpty() && appFamily.equals(companyFamily);

        if (sameFamily) {
            score += 0.24d;

            if (!containsFlavour(appNormalized)) {
                Set<String> appGeneric = coreTokens(appNormalized, true);
                Set<String> companyGeneric = coreTokens(companyNormalized, true);
                score += tokenSimilarity(appGeneric, companyGeneric) * 0.12d;
            }
        }

        PackCompatibility packCompatibility = comparePackSizes(
                appProduct.getName(),
                companyPrice.productName
        );

        if (packCompatibility == PackCompatibility.MATCH) {
            score += 0.16d;
        } else if (packCompatibility == PackCompatibility.MISMATCH) {
            if (isGenericStandardAfresh(appNormalized, companyNormalized)) {
                // In the official PDF the standard Afresh price columns are merged
                // across several flavours, including the 40 gms Kashmiri Kahwa row.
                // PDF text extraction can attach those shared values to a 50 gms
                // representative row. Do not punish that visual-table artifact.
                score += 0.04d;
            } else {
                score -= 0.34d;
            }
        }

        double vp = appProduct.getVp();
        if (vp > 0d && companyPrice.volumePoint > 0d) {
            double difference = Math.abs(vp - companyPrice.volumePoint);
            if (difference <= 0.15d) {
                score += 0.12d;
            } else if (difference <= 1.0d) {
                score += 0.07d;
            } else if (difference <= 3.0d) {
                score += 0.02d;
            }
        }

        String compactApp = appNormalized.replace(" ", "");
        String compactCompany = companyNormalized.replace(" ", "");
        if (!compactApp.isEmpty()
                && (compactCompany.contains(compactApp)
                || compactApp.contains(compactCompany))) {
            score += 0.06d;
        }

        return Math.max(0d, Math.min(1d, score));
    }

    private static boolean isGenericStandardAfresh(String appNormalized, String companyNormalized) {
        return "afresh".equals(canonicalFamily(appNormalized))
                && "afresh".equals(canonicalFamily(companyNormalized))
                && !containsFlavour(appNormalized)
                && !containsAny(companyNormalized, "tulsi", "basil");
    }

    private static double tokenSimilarity(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0d;
        }

        int intersection = 0;
        for (String token : left) {
            if (right.contains(token)) {
                intersection++;
            }
        }

        double coverage = intersection / (double) left.size();
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        double jaccard = union.isEmpty() ? 0d : intersection / (double) union.size();

        return coverage * 0.72d + jaccard * 0.28d;
    }

    private static boolean hasDifferentPriceSets(List<ScoredCandidate> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return false;
        }

        MergedCompanyPrice first = candidates.get(0).companyPrice;
        for (int i = 1; i < candidates.size(); i++) {
            if (!first.samePrices(candidates.get(i).companyPrice)) {
                return true;
            }
        }
        return false;
    }

    private static PackCompatibility comparePackSizes(String first, String second) {
        Set<String> firstSizes = extractPackSizes(first);
        Set<String> secondSizes = extractPackSizes(second);

        if (firstSizes.isEmpty() || secondSizes.isEmpty()) {
            return PackCompatibility.UNKNOWN;
        }

        for (String size : firstSizes) {
            if (secondSizes.contains(size)) {
                return PackCompatibility.MATCH;
            }
        }

        return PackCompatibility.MISMATCH;
    }

    private static Set<String> extractPackSizes(String value) {
        Set<String> result = new HashSet<>();
        String normalized = safe(value)
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

        Matcher matcher = PACK_PATTERN.matcher(normalized);
        while (matcher.find()) {
            result.add(matcher.group(1) + normalizeUnit(matcher.group(2)));
        }
        return result;
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

    private static Set<String> coreTokens(String normalizedName, boolean removeFlavours) {
        Set<String> tokens = new HashSet<>();
        if (normalizedName == null || normalizedName.trim().isEmpty()) {
            return tokens;
        }

        for (String token : normalizedName.split("\\s+")) {
            if (token.isEmpty() || STOP_WORDS.contains(token)) {
                continue;
            }
            if (removeFlavours && FLAVOUR_WORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static String canonicalFamily(String normalizedName) {
        String value = safe(normalizedName);
        if (value.contains("formula 1")) return "formula 1";
        if (value.contains("afresh")) return "afresh";
        if (value.contains("dinoshake")) return "dinoshake";
        if (value.contains("protein powder")) return "protein powder";
        if (value.contains("shake mate") || value.contains("shakemate")) return "shakemate";
        if (value.contains("liftoff")) return "liftoff";
        if (value.contains("skin booster")) return "skin booster";
        if (value.contains("aloe concentrate")) return "aloe concentrate";
        if (value.contains("active fiber complex")) return "active fiber complex";
        if (value.contains("activated fiber")) return "activated fiber";

        Set<String> generic = coreTokens(value, true);
        if (generic.isEmpty()) return "";
        List<String> sorted = new ArrayList<>(generic);
        Collections.sort(sorted);
        StringBuilder builder = new StringBuilder();
        for (String token : sorted) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(token);
        }
        return builder.toString();
    }

    private static boolean containsFlavour(String normalizedName) {
        for (String token : safe(normalizedName).split("\\s+")) {
            if (FLAVOUR_WORDS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... terms) {
        String source = safe(value);
        if (terms == null) {
            return false;
        }

        for (String term : terms) {
            if (term != null && !term.isEmpty() && source.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeName(String value) {
        String normalized = safe(value)
                .toLowerCase(Locale.US)
                .replace("®", "")
                .replace("™", "")
                .replace("&", " and ")
                .replace("’", "'")
                .replace("–", "-")
                .replace("—", "-");

        normalized = normalized
                .replace("formula-1", "formula 1")
                .replace("formula1", "formula 1")
                .replace("cell-u-loss", "cell u loss")
                .replace("celluloss", "cell u loss")
                .replace("nite works", "niteworks")
                .replace("afresh energy drink mix", "afresh")
                .replace("herbal aloe concentrate", "aloe concentrate")
                .replace("personalized protein powder", "protein powder")
                .replace("personalised protein powder", "protein powder")
                .replace("activated fibre", "activated fiber")
                .replace("active fibre", "active fiber")
                .replace("ocular defence", "ocular defense")
                .replace("dino shake", "dinoshake");

        return normalized
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String normalizeStock(String value) {
        return safe(value)
                .toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]", "");
    }

    private static boolean sameNullablePrice(Integer first, Integer second) {
        return first != null && first.equals(second);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private enum PackCompatibility {
        MATCH,
        MISMATCH,
        UNKNOWN
    }

    private static class ScoredCandidate {
        private final MergedCompanyPrice companyPrice;
        private final double score;

        private ScoredCandidate(MergedCompanyPrice companyPrice, double score) {
            this.companyPrice = companyPrice;
            this.score = score;
        }
    }

    private static class MatchDecision {
        private final MergedCompanyPrice best;
        private final double score;
        private final String conflictMessage;

        private MatchDecision(MergedCompanyPrice best, double score, String conflictMessage) {
            this.best = best;
            this.score = score;
            this.conflictMessage = conflictMessage;
        }
    }

    private static class MergedCompanyPrice {
        private final String stockNo;
        private final String productName;
        private final double volumePoint;
        private final int fullPrice;
        private final int price15;
        private final int price25;
        private final int price35;
        private final int price42;
        private final int price50;

        private MergedCompanyPrice(
                String stockNo,
                String productName,
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
            this.volumePoint = volumePoint;
            this.fullPrice = fullPrice;
            this.price15 = price15;
            this.price25 = price25;
            this.price35 = price35;
            this.price42 = price42;
            this.price50 = price50;
        }

        private boolean samePrices(MergedCompanyPrice other) {
            return other != null
                    && fullPrice == other.fullPrice
                    && price15 == other.price15
                    && price25 == other.price25
                    && price35 == other.price35
                    && price42 == other.price42
                    && price50 == other.price50;
        }
    }
}
