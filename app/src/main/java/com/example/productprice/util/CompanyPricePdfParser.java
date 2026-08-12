package com.example.productprice.util;

import android.content.Context;
import android.net.Uri;

import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.CompanyPriceRow;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the two official company price-list PDFs.
 *
 * Important: the official PDFs frequently show several flavour Stock Nos. with
 * one shared price cell. PDF text extraction therefore produces several product
 * lines without prices and only one line with the numeric price columns. This
 * parser reconstructs those shared-price groups so a simplified app product
 * such as "Formula 1-500 gms" can safely match any of the equivalent flavours.
 */
public final class CompanyPricePdfParser {

    private static final Pattern STOCK_ROW_PATTERN =
            Pattern.compile("^([A-Z0-9]{3,5})\\s+(.+)$");

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(?<![A-Za-z])\\(?-?\\d+(?:\\.\\d+)?\\)?");

    private static final Pattern PACK_PATTERN = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(kg|gms|gm|grams|gram|g|ml|ltr|litre|litres|tablet|tablets|tab|tabs|capsule|capsules|caps|sachet|sachets|softgel|softgels)\\b"
    );

    private static final Pattern EFFECTIVE_DATE_PATTERN =
            Pattern.compile(
                    "(?i)(?:effective|eective|efective)\\s*date\\s*[:\\-]?\\s*"
                            + "([0-9]{1,2}(?:st|nd|rd|th)?\\s+[A-Za-z]+\\s+[0-9]{4})"
            );

    private static final Pattern FALLBACK_DATE_PATTERN =
            Pattern.compile(
                    "(?i)\\b([0-9]{1,2}(?:st|nd|rd|th)?\\s+"
                            + "(?:January|February|March|April|May|June|July|August|September|October|November|December)"
                            + "\\s+[0-9]{4})\\b"
            );

    private static final Set<String> FLAVOUR_WORDS = new HashSet<>(Arrays.asList(
            "vanilla", "chocolate", "chocolicious", "mango", "orange", "cream",
            "strawberry", "kulfi", "banana", "caramel", "rose", "kheer", "paan",
            "dates", "ginger", "elaichi", "lemon", "peach", "cinnamon", "kashmiri",
            "kahwa", "tulsi", "basil", "watermelon", "unflavoured", "unflavored",
            "original", "flavour", "flavor"
    ));

    private CompanyPricePdfParser() {
    }

    public static CompanyPriceDocument parse(
            Context context,
            Uri pdfUri,
            String sourceName
    ) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }

        if (pdfUri == null) {
            throw new IllegalArgumentException("PDF file is required");
        }

        PDFBoxResourceLoader.init(context.getApplicationContext());

        CompanyPriceDocument result = new CompanyPriceDocument();
        result.setSourceName(sourceName);

        String rawText;

        try (InputStream inputStream = context.getContentResolver().openInputStream(pdfUri)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Unable to open selected PDF");
            }

            try (PDDocument document = PDDocument.load(inputStream)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                rawText = stripper.getText(document);
            }
        }

        String normalizedText = normalizeExtractedText(rawText);
        result.setRawText(normalizedText);

        CompanyPriceDocument.DocumentType documentType = detectDocumentType(normalizedText);
        result.setDocumentType(documentType);
        result.setEffectiveDate(extractEffectiveDate(normalizedText));

        if (documentType == CompanyPriceDocument.DocumentType.UNKNOWN) {
            result.addWarning(
                    "This PDF is not recognized as an Associate or Preferred Customer price list."
            );
            return result;
        }

        Map<String, CompanyPriceRow> parsedRows = parseRows(normalizedText, documentType);
        for (CompanyPriceRow row : parsedRows.values()) {
            result.addRow(row);
        }

        if (result.getEffectiveDate().isEmpty()) {
            result.addWarning("Effective Date could not be detected.");
        }

        if (result.getRows().isEmpty()) {
            result.addWarning("No product price rows could be read from this PDF.");
        }

        return result;
    }

    private static CompanyPriceDocument.DocumentType detectDocumentType(String text) {
        String upper = text == null ? "" : text.toUpperCase(Locale.US);

        if (upper.contains("ASSOCIATE PRICE LIST")
                || (upper.contains("ASSOCIATES")
                && upper.contains("SUPERVISOR")
                && upper.contains("42%")
                && upper.contains("50%"))) {
            return CompanyPriceDocument.DocumentType.ASSOCIATE;
        }

        if (upper.contains("PREFERRED CUSTOMERS")
                || (upper.contains("BRONZE PREFERRED")
                && upper.contains("SILVER PREFERRED")
                && upper.contains("GOLD PREFERRED"))) {
            return CompanyPriceDocument.DocumentType.PREFERRED_CUSTOMER;
        }

        return CompanyPriceDocument.DocumentType.UNKNOWN;
    }

    private static String extractEffectiveDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        Matcher matcher = EFFECTIVE_DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            return cleanSpaces(matcher.group(1));
        }

        matcher = FALLBACK_DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            return cleanSpaces(matcher.group(1));
        }

        return "";
    }

    private static Map<String, CompanyPriceRow> parseRows(
            String text,
            CompanyPriceDocument.DocumentType documentType
    ) {
        Map<String, CompanyPriceRow> rows = new LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) {
            return rows;
        }

        List<IdentityRow> pendingIdentityRows = new ArrayList<>();
        CompanyPriceRow previousPricedRow = null;

        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = cleanSpaces(rawLine);

            if (line.isEmpty()) {
                continue;
            }

            if (isSectionBoundary(line)) {
                flushPendingToPrevious(rows, pendingIdentityRows, previousPricedRow);
                pendingIdentityRows.clear();
                previousPricedRow = null;
                continue;
            }

            if (shouldIgnoreLine(line)) {
                continue;
            }

            CompanyPriceRow fullRow = parseFullPriceRow(line, documentType);
            if (fullRow != null) {
                flushPendingBetweenPriceRows(
                        rows,
                        pendingIdentityRows,
                        previousPricedRow,
                        fullRow
                );
                pendingIdentityRows.clear();

                putBestRow(rows, fullRow);
                previousPricedRow = fullRow;
                continue;
            }

            IdentityRow identityRow = parseIdentityOnlyRow(line);
            if (identityRow != null) {
                pendingIdentityRows.add(identityRow);
            }
        }

        flushPendingToPrevious(rows, pendingIdentityRows, previousPricedRow);
        return rows;
    }

    private static CompanyPriceRow parseFullPriceRow(
            String line,
            CompanyPriceDocument.DocumentType documentType
    ) {
        Matcher rowMatcher = STOCK_ROW_PATTERN.matcher(line);
        if (!rowMatcher.matches()) {
            return null;
        }

        String stockNo = rowMatcher.group(1).trim();
        String body = rowMatcher.group(2).trim();

        int requiredTailCount = documentType == CompanyPriceDocument.DocumentType.ASSOCIATE
                ? 9
                : 6;

        List<NumberToken> numberTokens = collectNumberTokens(body);
        if (numberTokens.size() < requiredTailCount) {
            return null;
        }

        int tailStartIndex = numberTokens.size() - requiredTailCount;
        NumberToken firstTailToken = numberTokens.get(tailStartIndex);

        String productName = cleanupProductName(
                body.substring(0, firstTailToken.start)
        );

        if (productName.isEmpty() || looksLikeHeaderProductName(productName)) {
            return null;
        }

        CompanyPriceRow row = new CompanyPriceRow();
        row.setStockNo(stockNo);
        row.setProductName(productName);
        row.setRawLine(line);

        if (documentType == CompanyPriceDocument.DocumentType.ASSOCIATE) {
            fillAssociateRow(row, numberTokens, tailStartIndex);
        } else {
            fillPreferredCustomerRow(row, numberTokens, tailStartIndex);
        }

        return isPlausibleProductRow(row) ? row : null;
    }

    private static IdentityRow parseIdentityOnlyRow(String line) {
        Matcher rowMatcher = STOCK_ROW_PATTERN.matcher(line);
        if (!rowMatcher.matches()) {
            return null;
        }

        String stockNo = rowMatcher.group(1).trim();
        String body = cleanupProductName(rowMatcher.group(2));

        if (body.isEmpty() || looksLikeHeaderProductName(body)) {
            return null;
        }

        // Reject obvious non-product numeric fragments.
        if (body.length() < 3) {
            return null;
        }

        return new IdentityRow(stockNo, body, line);
    }

    /**
     * Handles the common PDF shape:
     * priced row A -> several identity-only rows -> priced row B.
     *
     * If the product family is the same, pack size decides whether an identity
     * row belongs to A or B. This is what correctly separates Formula 1 500 gms
     * from Formula 1 750 gms while still allowing flavour rows to share prices.
     */
    private static void flushPendingBetweenPriceRows(
            Map<String, CompanyPriceRow> rows,
            List<IdentityRow> pending,
            CompanyPriceRow previous,
            CompanyPriceRow current
    ) {
        for (IdentityRow identity : pending) {
            CompanyPriceRow source = choosePriceSource(identity, previous, current);
            if (source != null) {
                putBestRow(rows, cloneWithIdentity(identity, source));
            }
        }
    }

    private static void flushPendingToPrevious(
            Map<String, CompanyPriceRow> rows,
            List<IdentityRow> pending,
            CompanyPriceRow previous
    ) {
        if (previous == null) {
            return;
        }

        for (IdentityRow identity : pending) {
            if (sameFamily(identity.productName, previous.getProductName())) {
                putBestRow(rows, cloneWithIdentity(identity, previous));
            }
        }
    }

    private static CompanyPriceRow choosePriceSource(
            IdentityRow identity,
            CompanyPriceRow previous,
            CompanyPriceRow current
    ) {
        if (identity == null || current == null) {
            return null;
        }

        boolean currentFamily = sameFamily(identity.productName, current.getProductName());
        boolean previousFamily = previous != null
                && sameFamily(identity.productName, previous.getProductName());

        if (currentFamily && !previousFamily) {
            return current;
        }

        if (previousFamily && !currentFamily) {
            return previous;
        }

        if (!currentFamily && !previousFamily) {
            return null;
        }

        if (previous == null) {
            return current;
        }

        PackCompatibility previousPack = comparePack(identity.productName, previous.getProductName());
        PackCompatibility currentPack = comparePack(identity.productName, current.getProductName());

        if (previousPack == PackCompatibility.MATCH
                && currentPack != PackCompatibility.MATCH) {
            return previous;
        }

        if (currentPack == PackCompatibility.MATCH
                && previousPack != PackCompatibility.MATCH) {
            return current;
        }

        if (sameMappedPrices(previous, current)) {
            return current;
        }

        // Same family + same apparent pack + different price signatures is not
        // safe enough to auto-assign. Leave it unmatched rather than guessing.
        return null;
    }

    private static CompanyPriceRow cloneWithIdentity(
            IdentityRow identity,
            CompanyPriceRow priceSource
    ) {
        CompanyPriceRow row = new CompanyPriceRow();
        row.setStockNo(identity.stockNo);
        row.setProductName(identity.productName);
        row.setRawLine(identity.rawLine + " [shared price group]");
        row.setVolumePoint(priceSource.getVolumePoint());
        row.setMrp(priceSource.getMrp());
        row.setRetailPrice(priceSource.getRetailPrice());
        row.setEarnBase(priceSource.getEarnBase());
        row.setPrice15(priceSource.getPrice15());
        row.setPrice25(priceSource.getPrice25());
        row.setPrice35(priceSource.getPrice35());
        row.setPrice42(priceSource.getPrice42());
        row.setPrice50(priceSource.getPrice50());
        return row;
    }

    private static void putBestRow(
            Map<String, CompanyPriceRow> rows,
            CompanyPriceRow row
    ) {
        if (row == null || !row.hasCoreIdentity() || !row.hasAnyMappedPrice()) {
            return;
        }

        String key = normalizeStock(row.getStockNo());
        if (key.isEmpty()) {
            key = normalizeProductKey(row.getProductName());
        }

        CompanyPriceRow existing = rows.get(key);
        if (existing == null || mappedPriceCount(row) > mappedPriceCount(existing)) {
            rows.put(key, row);
        }
    }

    private static void fillAssociateRow(
            CompanyPriceRow row,
            List<NumberToken> tokens,
            int start
    ) {
        row.setVolumePoint(tokens.get(start + 1).value);
        row.setMrp(roundedInt(tokens.get(start + 2).value));
        row.setRetailPrice(roundedInt(tokens.get(start + 3).value));
        row.setEarnBase(roundedInt(tokens.get(start + 4).value));
        row.setPrice25(roundedInt(tokens.get(start + 5).value));
        row.setPrice35(roundedInt(tokens.get(start + 6).value));
        row.setPrice42(roundedInt(tokens.get(start + 7).value));
        row.setPrice50(roundedInt(tokens.get(start + 8).value));
    }

    private static void fillPreferredCustomerRow(
            CompanyPriceRow row,
            List<NumberToken> tokens,
            int start
    ) {
        row.setVolumePoint(tokens.get(start + 1).value);
        row.setMrp(roundedInt(tokens.get(start + 2).value));
        // Company terminology: Bronze = 15%, Silver = 25%, Gold = 35%.
        row.setPrice15(roundedInt(tokens.get(start + 3).value));
        row.setPrice25(roundedInt(tokens.get(start + 4).value));
        row.setPrice35(roundedInt(tokens.get(start + 5).value));
    }

    private static List<NumberToken> collectNumberTokens(String body) {
        List<NumberToken> tokens = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(body);

        while (matcher.find()) {
            String valueText = matcher.group()
                    .replace("(", "")
                    .replace(")", "")
                    .trim();
            try {
                tokens.add(new NumberToken(
                        matcher.start(),
                        matcher.end(),
                        Double.parseDouble(valueText)
                ));
            } catch (Exception ignored) {
                // Ignore malformed numeric fragments.
            }
        }

        return tokens;
    }

    private static boolean isPlausibleProductRow(CompanyPriceRow row) {
        if (row == null || row.getMrp() <= 0 || row.getVolumePoint() < 0d) {
            return false;
        }

        if (row.getProductName().length() < 3) {
            return false;
        }

        return notAboveMrp(row.getPrice15(), row.getMrp())
                && notAboveMrp(row.getPrice25(), row.getMrp())
                && notAboveMrp(row.getPrice35(), row.getMrp())
                && notAboveMrp(row.getPrice42(), row.getMrp())
                && notAboveMrp(row.getPrice50(), row.getMrp());
    }

    private static boolean notAboveMrp(Integer value, int mrp) {
        return value == null || value <= mrp;
    }

    private static boolean shouldIgnoreLine(String line) {
        String upper = line.toUpperCase(Locale.US);
        return upper.startsWith("NOTES")
                || upper.startsWith("STOCK NO")
                || upper.startsWith("STOCK ")
                || upper.startsWith("PRODUCT NAME")
                || upper.contains("HERBALIFE INTERNATIONAL INDIA")
                || upper.contains("TOLL FREE")
                || upper.contains("ONLINE ORDERS")
                || upper.contains("EFFECTIVE DATE")
                || upper.contains("PREFERRED CUSTOMER APPLICATION")
                || upper.contains("ASSOCIATE APPLICATION")
                || upper.contains("INCLUSIVE OF GST");
    }

    private static boolean isSectionBoundary(String line) {
        String upper = line.toUpperCase(Locale.US);
        return upper.equals("WEIGHT MANAGEMENT PRODUCTS")
                || upper.equals("WEIGHT MANAGEMENT")
                || upper.equals("ENERGY PRODUCTS")
                || upper.equals("SPORTS NUTRITION")
                || upper.equals("CHILDREN'S HEALTH")
                || upper.equals("CHILDREN’S HEALTH")
                || upper.equals("DIGESTIVE HEALTH")
                || upper.equals("BONE & JOINT HEALTH")
                || upper.equals("CARDIOVASCULAR HEALTH")
                || upper.equals("ENHANCERS")
                || upper.equals("EYE HEALTH")
                || upper.equals("MEN'S HEALTH")
                || upper.equals("MEN’S HEALTH")
                || upper.equals("WOMEN'S HEALTH")
                || upper.equals("WOMEN’S HEALTH")
                || upper.equals("BRAIN HEALTH")
                || upper.equals("VRITILIFE BRAIN HEALTH")
                || upper.equals("IMMUNE HEALTH")
                || upper.equals("VRITILIFE IMMUNE HEALTH")
                || upper.equals("SKIN & BODY CARE")
                || upper.equals("VRITILIFE SKIN & BODY CARE")
                || upper.equals("SLEEP SUPPORT")
                || upper.equals("APPLICATIONS")
                || upper.equals("ART OF PROMOTION");
    }

    private static boolean looksLikeHeaderProductName(String name) {
        String upper = name.toUpperCase(Locale.US);
        return upper.equals("PRODUCT")
                || upper.equals("PRODUCT NAME")
                || upper.contains("PRICE LIST")
                || upper.contains("APPLICATIONS");
    }

    private static boolean sameFamily(String first, String second) {
        String firstKey = familyKey(first);
        String secondKey = familyKey(second);
        return !firstKey.isEmpty() && firstKey.equals(secondKey);
    }

    private static String familyKey(String value) {
        String normalized = normalizeProductKey(value)
                .replace("formula1", "formula 1")
                .replace("formula-1", "formula 1")
                .replace("afresh energy drink mix", "afresh")
                .replace("dino shake", "dinoshake")
                .replace("personalized protein powder", "protein powder")
                .replace("activated fibre", "activated fiber")
                .replace("active fibre", "active fiber")
                .replace("ocular defence", "ocular defense")
                .replace("liftoff", "liftoff");

        if (normalized.contains("formula 1")) return "formula 1";
        if (normalized.contains("afresh")) return "afresh";
        if (normalized.contains("dinoshake")) return "dinoshake";
        if (normalized.contains("liftoff")) return "liftoff";

        normalized = normalized.replaceAll(
                "\\b\\d+(?:\\.\\d+)?\\s*(?:kg|gms|gm|g|ml|tablet|tablets|tab|tabs|capsule|capsules|caps|sachet|sachets|softgel|softgels)\\b",
                " "
        );

        StringBuilder builder = new StringBuilder();
        for (String token : normalized.split("\\s+")) {
            if (token.isEmpty() || FLAVOUR_WORDS.contains(token)) {
                continue;
            }
            if (builder.length() > 0) builder.append(' ');
            builder.append(token);
        }

        return builder.toString().trim().replaceAll("\\s+", " ");
    }

    private static PackCompatibility comparePack(String first, String second) {
        Set<String> firstPacks = extractPackSizes(first);
        Set<String> secondPacks = extractPackSizes(second);

        if (firstPacks.isEmpty() || secondPacks.isEmpty()) {
            return PackCompatibility.UNKNOWN;
        }

        for (String pack : firstPacks) {
            if (secondPacks.contains(pack)) {
                return PackCompatibility.MATCH;
            }
        }

        return PackCompatibility.MISMATCH;
    }

    private static Set<String> extractPackSizes(String value) {
        Set<String> packs = new HashSet<>();
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
            packs.add(matcher.group(1) + normalizeUnit(matcher.group(2)));
        }
        return packs;
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

    private static boolean sameMappedPrices(CompanyPriceRow first, CompanyPriceRow second) {
        if (first == null || second == null) return false;
        return first.getMrp() == second.getMrp()
                && sameOptional(first.getPrice15(), second.getPrice15())
                && sameOptional(first.getPrice25(), second.getPrice25())
                && sameOptional(first.getPrice35(), second.getPrice35())
                && sameOptional(first.getPrice42(), second.getPrice42())
                && sameOptional(first.getPrice50(), second.getPrice50());
    }

    private static boolean sameOptional(Integer first, Integer second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String cleanupProductName(String value) {
        String cleaned = cleanSpaces(value)
                .replace("™", "")
                .replace("®", "")
                .replace("–", "-")
                .replace("—", "-");

        while (cleaned.endsWith("-") || cleaned.endsWith(":")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private static String normalizeProductKey(String value) {
        return safe(value)
                .toLowerCase(Locale.US)
                .replace("™", "")
                .replace("®", "")
                .replace("’", "'")
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String normalizeStock(String value) {
        return safe(value)
                .toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]", "");
    }

    private static String normalizeExtractedText(String text) {
        if (text == null) return "";

        String normalized = text
                .replace("ﬀ", "ff")
                .replace("ﬁ", "fi")
                .replace("ﬂ", "fl")
                .replace("–", "-")
                .replace("—", "-")
                .replace('\u00A0', ' ')
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "");

        return normalized
                .replaceAll("(?i)\\bEective\\s+Date\\b", "Effective Date")
                .replaceAll("(?i)\\bEfective\\s+Date\\b", "Effective Date");
    }

    private static String cleanSpaces(String value) {
        return safe(value)
                .replace('\t', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int roundedInt(double value) {
        return Math.max(0, (int) Math.round(value));
    }

    private static int mappedPriceCount(CompanyPriceRow row) {
        int count = row.getMrp() > 0 ? 1 : 0;
        if (row.getPrice15() != null) count++;
        if (row.getPrice25() != null) count++;
        if (row.getPrice35() != null) count++;
        if (row.getPrice42() != null) count++;
        if (row.getPrice50() != null) count++;
        return count;
    }

    private enum PackCompatibility {
        MATCH,
        MISMATCH,
        UNKNOWN
    }

    private static class IdentityRow {
        private final String stockNo;
        private final String productName;
        private final String rawLine;

        private IdentityRow(String stockNo, String productName, String rawLine) {
            this.stockNo = stockNo;
            this.productName = productName;
            this.rawLine = rawLine;
        }
    }

    private static class NumberToken {
        private final int start;
        @SuppressWarnings("unused")
        private final int end;
        private final double value;

        private NumberToken(int start, int end, double value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }
}
