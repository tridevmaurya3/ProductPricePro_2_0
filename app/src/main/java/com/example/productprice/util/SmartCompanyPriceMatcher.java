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

public final class SmartCompanyPriceMatcher {

    private static final double SAFE_MATCH_THRESHOLD = 0.60d;
    private static final double AMBIGUITY_WINDOW = 0.05d;

    private static final Pattern PACK_PATTERN = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(kg|gms|gm|grams|gram|g|ml|ltr|litre|litres|tablet|tablets|tab|tabs|capsule|capsules|caps|sachet|sachets|softgel|softgels)\\b"
    );

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "herbalife", "nutrition", "nutritional", "product", "products",
            "powder", "mix", "flavour", "flavor", "drink", "instant",
            "tablet", "tablets", "tab", "tabs", "capsule", "capsules", "caps",
            "sachet", "sachets", "softgel", "softgels", "pack", "packs",
            "g", "gm", "gms", "gram", "grams", "ml", "kg", "with", "and", "the",
            "personalized", "new"
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
            FamilyMatch familyMatch = evaluateFamilyMatch(product, companyPrice);
            if (familyMatch.rejected) {
                continue;
            }

            double score = scoreMatch(product, companyPrice, familyMatch);
            if (score > 0d) {
                scored.add(new ScoredCandidate(companyPrice, score));
            }
        }

        if (scored.isEmpty()) {
            return new MatchDecision(null, 0d, null);
        }

        Collections.sort(
                scored,
                (left, right) -> Double.compare(right.score, left.score)
        );

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
                            + ". Multiple similar PDF product groups have different prices, so nothing was changed."
            );
        }

        return new MatchDecision(top.companyPrice, top.score, null);
    }

    private static FamilyMatch evaluateFamilyMatch(
            Product appProduct,
            MergedCompanyPrice companyPrice
    ) {
        String appName = normalizeName(appProduct.getName());
        String companyName = normalizeName(companyPrice.productName);

        if (isFormulaOne(appName)) {
            if (!isFormulaOne(companyName)) {
                return FamilyMatch.reject();
            }

            PackCompatibility pack = comparePackSizes(
                    appProduct.getName(),
                    companyPrice.productName
            );

            if (pack == PackCompatibility.MISMATCH) {
                return FamilyMatch.reject();
            }

            return FamilyMatch.preferred(0.30d, true);
        }

        if (isAfresh(appName)) {
            if (!isAfresh(companyName)) {
                return FamilyMatch.reject();
            }

            boolean appTulsi = containsAny(appName, "tulsi", "basil");
            boolean companyTulsi = containsAny(companyName, "tulsi", "basil");

            if (appTulsi != companyTulsi) {
                return FamilyMatch.reject();
            }

            // The official price list uses merged price cells for the standard Afresh
            // flavour group. The text extractor may attach that shared price to one
            // representative flavour (for example Lemon) even though Kashmiri Kahwa
            // is 40 gms. For a generic Afresh app product, family identity is therefore
            // intentionally more important than the representative flavour/pack text.
            return FamilyMatch.preferred(
                    appTulsi ? 0.34d : 0.38d,
                    true
            );
        }

        if (isDinoshake(appName)) {
            if (!isDinoshake(companyName)) {
                return FamilyMatch.reject();
            }

            boolean appHasKnownFlavor = containsAny(
                    appName,
                    "chocolicious",
                    "chocolate",
                    "strawberry"
            );

            if (!appHasKnownFlavor) {
                return FamilyMatch.preferred(0.30d, true);
            }
        }

        return FamilyMatch.normal();
    }

    private static double scoreMatch(
            Product appProduct,
            MergedCompanyPrice companyPrice,
            FamilyMatch familyMatch
    ) {
        String appNormalized = normalizeName(appProduct.getName());
        String companyNormalized = normalizeName(companyPrice.productName);

        Set<String> appTokens = coreTokens(appNormalized);
        Set<String> companyTokens = coreTokens(companyNormalized);

        if (appTokens.isEmpty() || companyTokens.isEmpty()) {
            return 0d;
        }

        int intersection = 0;
        for (String token : appTokens) {
            if (companyTokens.contains(token)) {
                intersection++;
            }
        }

        double coverage = intersection / (double) appTokens.size();

        Set<String> union = new HashSet<>(appTokens);
        union.addAll(companyTokens);
        double jaccard = union.isEmpty() ? 0d : intersection / (double) union.size();

        double score = coverage * 0.52d + jaccard * 0.22d;
        score += familyMatch.bonus;

        String appCorePhrase = joinTokens(appTokens);
        String companyCorePhrase = joinTokens(companyTokens);

        if (!appCorePhrase.isEmpty()
                && (companyCorePhrase.contains(appCorePhrase)
                || appCorePhrase.contains(companyCorePhrase))) {
            score += 0.10d;
        }

        PackCompatibility packCompatibility = comparePackSizes(
                appProduct.getName(),
                companyPrice.productName
        );

        if (packCompatibility == PackCompatibility.MATCH) {
            score += 0.12d;
        } else if (packCompatibility == PackCompatibility.MISMATCH
                && !familyMatch.ignorePackMismatch) {
            score -= 0.28d;
        }

        double vp = appProduct.getVp();
        if (vp > 0d && companyPrice.volumePoint > 0d) {
            double difference = Math.abs(vp - companyPrice.volumePoint);
            if (difference <= 0.15d) {
                score += 0.10d;
            } else if (difference <= 1.0d) {
                score += 0.06d;
            } else if (difference <= 3.0d) {
                score += 0.02d;
            }
        }

        String compactApp = appNormalized.replace(" ", "");
        String compactCompany = companyNormalized.replace(" ", "");
        if (!compactApp.isEmpty()
                && (compactCompany.contains(compactApp)
                || compactApp.contains(compactCompany))) {
            score += 0.08d;
        }

        return Math.max(0d, Math.min(1d, score));
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
            String number = matcher.group(1);
            String unit = matcher.group(2).toLowerCase(Locale.US);
            unit = normalizeUnit(unit);
            result.add(number + unit);
        }
        return result;
    }

    private static String normalizeUnit(String unit) {
        if (unit == null) return "";
        String value = unit.toLowerCase(Locale.US);
        if (value.equals("gms") || value.equals("gm") || value.equals("grams") || value.equals("gram")) return "g";
        if (value.equals("tablets") || value.equals("tablet") || value.equals("tabs")) return "tab";
        if (value.equals("capsules") || value.equals("capsule")) return "caps";
        if (value.equals("sachets")) return "sachet";
        if (value.equals("softgels")) return "softgel";
        return value;
    }

    private static Set<String> coreTokens(String normalizedName) {
        Set<String> tokens = new HashSet<>();
        if (normalizedName == null || normalizedName.trim().isEmpty()) {
            return tokens;
        }

        for (String token : normalizedName.split("\\s+")) {
            if (token.isEmpty() || STOP_WORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static String joinTokens(Set<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return "";
        List<String> sorted = new ArrayList<>(tokens);
        Collections.sort(sorted);
        StringBuilder builder = new StringBuilder();
        for (String token : sorted) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(token);
        }
        return builder.toString();
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
                .replace("activated fibre", "activated fiber")
                .replace("active fibre", "active fiber")
                .replace("ocular defence", "ocular defense")
                .replace("unflavoured", "unflavored")
                .replace("chocolicious", "chocolate");

        return normalized
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean isFormulaOne(String normalizedName) {
        return normalizedName != null
                && normalizedName.matches(".*\\bformula\\s+1\\b.*");
    }

    private static boolean isAfresh(String normalizedName) {
        return normalizedName != null
                && normalizedName.matches(".*\\bafresh\\b.*");
    }

    private static boolean isDinoshake(String normalizedName) {
        return normalizedName != null
                && normalizedName.replace(" ", "").contains("dinoshake");
    }

    private static boolean containsAny(String value, String... terms) {
        if (value == null || terms == null) {
            return false;
        }

        for (String term : terms) {
            if (term != null && !term.isEmpty() && value.contains(term)) {
                return true;
            }
        }

        return false;
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

    private static class FamilyMatch {
        private final boolean rejected;
        private final double bonus;
        private final boolean ignorePackMismatch;

        private FamilyMatch(boolean rejected, double bonus, boolean ignorePackMismatch) {
            this.rejected = rejected;
            this.bonus = bonus;
            this.ignorePackMismatch = ignorePackMismatch;
        }

        private static FamilyMatch reject() {
            return new FamilyMatch(true, 0d, false);
        }

        private static FamilyMatch normal() {
            return new FamilyMatch(false, 0d, false);
        }

        private static FamilyMatch preferred(double bonus, boolean ignorePackMismatch) {
            return new FamilyMatch(false, bonus, ignorePackMismatch);
        }
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
