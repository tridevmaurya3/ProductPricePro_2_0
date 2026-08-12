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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompanyPricePdfParser {

    private static final Pattern STOCK_ROW_PATTERN =
            Pattern.compile("^([A-Z0-9]{3,5})\\s+(.+)$");

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(?<![A-Za-z])\\(?-?\\d+(?:\\.\\d+)?\\)?");

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

        PDFBoxResourceLoader.init(
                context.getApplicationContext()
        );

        CompanyPriceDocument result =
                new CompanyPriceDocument();

        result.setSourceName(sourceName);

        String rawText;

        try (
                InputStream inputStream = context
                        .getContentResolver()
                        .openInputStream(pdfUri)
        ) {
            if (inputStream == null) {
                throw new IllegalArgumentException(
                        "Unable to open selected PDF"
                );
            }

            try (
                    PDDocument document =
                            PDDocument.load(inputStream)
            ) {
                PDFTextStripper textStripper =
                        new PDFTextStripper();

                textStripper.setSortByPosition(true);

                rawText = textStripper.getText(
                        document
                );
            }
        }

        String normalizedText =
                normalizeExtractedText(rawText);

        result.setRawText(normalizedText);

        CompanyPriceDocument.DocumentType documentType =
                detectDocumentType(normalizedText);

        result.setDocumentType(
                documentType
        );

        result.setEffectiveDate(
                extractEffectiveDate(
                        normalizedText
                )
        );

        if (documentType
                == CompanyPriceDocument.DocumentType.UNKNOWN) {
            result.addWarning(
                    "This PDF is not recognized as an Associate or Preferred Customer price list."
            );

            return result;
        }

        Map<String, CompanyPriceRow> parsedRows =
                parseRows(
                        normalizedText,
                        documentType
                );

        for (
                CompanyPriceRow row :
                parsedRows.values()
        ) {
            result.addRow(row);
        }

        if (result.getEffectiveDate().isEmpty()) {
            result.addWarning(
                    "Effective Date could not be detected."
            );
        }

        if (result.getRows().isEmpty()) {
            result.addWarning(
                    "No product price rows could be read from this PDF."
            );
        }

        return result;
    }

    private static CompanyPriceDocument.DocumentType detectDocumentType(
            String text
    ) {
        String upper = text == null
                ? ""
                : text.toUpperCase(Locale.US);

        if (upper.contains(
                "ASSOCIATE PRICE LIST"
        ) || (
                upper.contains("ASSOCIATES")
                        && upper.contains("SUPERVISOR")
                        && upper.contains("42%")
                        && upper.contains("50%")
        )) {
            return CompanyPriceDocument.DocumentType.ASSOCIATE;
        }

        if (upper.contains(
                "PREFERRED CUSTOMERS"
        ) || (
                upper.contains("BRONZE PREFERRED")
                        && upper.contains("SILVER PREFERRED")
                        && upper.contains("GOLD PREFERRED")
        )) {
            return CompanyPriceDocument.DocumentType.PREFERRED_CUSTOMER;
        }

        return CompanyPriceDocument.DocumentType.UNKNOWN;
    }

    private static String extractEffectiveDate(
            String text
    ) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        Matcher effectiveDateMatcher =
                EFFECTIVE_DATE_PATTERN.matcher(text);

        if (effectiveDateMatcher.find()) {
            return cleanSpaces(
                    effectiveDateMatcher.group(1)
            );
        }

        Matcher fallbackMatcher =
                FALLBACK_DATE_PATTERN.matcher(text);

        if (fallbackMatcher.find()) {
            return cleanSpaces(
                    fallbackMatcher.group(1)
            );
        }

        return "";
    }

    private static Map<String, CompanyPriceRow> parseRows(
            String text,
            CompanyPriceDocument.DocumentType documentType
    ) {
        Map<String, CompanyPriceRow> rows =
                new LinkedHashMap<>();

        if (text == null || text.trim().isEmpty()) {
            return rows;
        }

        String[] lines =
                text.split("\\r?\\n");

        for (String rawLine : lines) {
            String line = cleanSpaces(rawLine);

            if (line.isEmpty()
                    || shouldIgnoreLine(line)) {
                continue;
            }

            CompanyPriceRow row =
                    parseSingleRow(
                            line,
                            documentType
                    );

            if (row == null
                    || !row.hasCoreIdentity()
                    || !row.hasAnyMappedPrice()) {
                continue;
            }

            String key =
                    row.getStockNo()
                            .toUpperCase(Locale.US)
                            + "|"
                            + normalizeProductKey(
                            row.getProductName()
                    );

            CompanyPriceRow existing =
                    rows.get(key);

            if (existing == null
                    || mappedPriceCount(row)
                    > mappedPriceCount(existing)) {
                rows.put(
                        key,
                        row
                );
            }
        }

        return rows;
    }

    private static CompanyPriceRow parseSingleRow(
            String line,
            CompanyPriceDocument.DocumentType documentType
    ) {
        Matcher rowMatcher =
                STOCK_ROW_PATTERN.matcher(line);

        if (!rowMatcher.matches()) {
            return null;
        }

        String stockNo =
                rowMatcher.group(1).trim();

        String body =
                rowMatcher.group(2).trim();

        int requiredTailCount =
                documentType
                        == CompanyPriceDocument.DocumentType.ASSOCIATE
                        ? 9
                        : 6;

        List<NumberToken> numberTokens =
                collectNumberTokens(body);

        if (numberTokens.size()
                < requiredTailCount) {
            return null;
        }

        int tailStartIndex =
                numberTokens.size()
                        - requiredTailCount;

        NumberToken firstTailToken =
                numberTokens.get(
                        tailStartIndex
                );

        String productName =
                cleanupProductName(
                        body.substring(
                                0,
                                firstTailToken.start
                        )
                );

        if (productName.isEmpty()
                || looksLikeHeaderProductName(productName)) {
            return null;
        }

        CompanyPriceRow row =
                new CompanyPriceRow();

        row.setStockNo(stockNo);
        row.setProductName(productName);
        row.setRawLine(line);

        if (documentType
                == CompanyPriceDocument.DocumentType.ASSOCIATE) {
            fillAssociateRow(
                    row,
                    numberTokens,
                    tailStartIndex
            );

        } else {
            fillPreferredCustomerRow(
                    row,
                    numberTokens,
                    tailStartIndex
            );
        }

        if (!isPlausibleProductRow(row)) {
            return null;
        }

        return row;
    }

    private static void fillAssociateRow(
            CompanyPriceRow row,
            List<NumberToken> tokens,
            int start
    ) {
        row.setVolumePoint(
                tokens.get(start + 1).value
        );

        row.setMrp(
                roundedInt(
                        tokens.get(start + 2).value
                )
        );

        row.setRetailPrice(
                roundedInt(
                        tokens.get(start + 3).value
                )
        );

        row.setEarnBase(
                roundedInt(
                        tokens.get(start + 4).value
                )
        );

        row.setPrice25(
                roundedInt(
                        tokens.get(start + 5).value
                )
        );

        row.setPrice35(
                roundedInt(
                        tokens.get(start + 6).value
                )
        );

        row.setPrice42(
                roundedInt(
                        tokens.get(start + 7).value
                )
        );

        row.setPrice50(
                roundedInt(
                        tokens.get(start + 8).value
                )
        );
    }

    private static void fillPreferredCustomerRow(
            CompanyPriceRow row,
            List<NumberToken> tokens,
            int start
    ) {
        row.setVolumePoint(
                tokens.get(start + 1).value
        );

        row.setMrp(
                roundedInt(
                        tokens.get(start + 2).value
                )
        );

        // Company terminology:
        // Bronze = 15%, Silver = 25%, Gold = 35%.
        row.setPrice15(
                roundedInt(
                        tokens.get(start + 3).value
                )
        );

        row.setPrice25(
                roundedInt(
                        tokens.get(start + 4).value
                )
        );

        row.setPrice35(
                roundedInt(
                        tokens.get(start + 5).value
                )
        );
    }

    private static List<NumberToken> collectNumberTokens(
            String body
    ) {
        List<NumberToken> tokens =
                new ArrayList<>();

        Matcher matcher =
                NUMBER_PATTERN.matcher(body);

        while (matcher.find()) {
            String valueText =
                    matcher.group()
                            .replace("(", "")
                            .replace(")", "")
                            .trim();

            try {
                double value =
                        Double.parseDouble(
                                valueText
                        );

                tokens.add(
                        new NumberToken(
                                matcher.start(),
                                matcher.end(),
                                value
                        )
                );

            } catch (Exception ignored) {
                // Ignore malformed numeric fragments.
            }
        }

        return tokens;
    }

    private static boolean isPlausibleProductRow(
            CompanyPriceRow row
    ) {
        if (row == null
                || row.getMrp() <= 0
                || row.getVolumePoint() < 0d) {
            return false;
        }

        if (row.getProductName().length() < 3) {
            return false;
        }

        Integer p15 = row.getPrice15();
        Integer p25 = row.getPrice25();
        Integer p35 = row.getPrice35();
        Integer p42 = row.getPrice42();
        Integer p50 = row.getPrice50();

        if (p15 != null && p15 > row.getMrp()) {
            return false;
        }

        if (p25 != null && p25 > row.getMrp()) {
            return false;
        }

        if (p35 != null && p35 > row.getMrp()) {
            return false;
        }

        if (p42 != null && p42 > row.getMrp()) {
            return false;
        }

        if (p50 != null && p50 > row.getMrp()) {
            return false;
        }

        return true;
    }

    private static boolean shouldIgnoreLine(
            String line
    ) {
        String upper =
                line.toUpperCase(Locale.US);

        return upper.startsWith("NOTES")
                || upper.startsWith("STOCK NO")
                || upper.startsWith("STOCK")
                || upper.startsWith("PRODUCT NAME")
                || upper.contains("HERBALIFE INTERNATIONAL INDIA")
                || upper.contains("TOLL FREE")
                || upper.contains("ONLINE ORDERS")
                || upper.contains("EFFECTIVE DATE")
                || upper.contains("PREFERRED CUSTOMER APPLICATION")
                || upper.contains("ASSOCIATE APPLICATION");
    }

    private static boolean looksLikeHeaderProductName(
            String name
    ) {
        String upper =
                name.toUpperCase(Locale.US);

        return upper.equals("PRODUCT")
                || upper.equals("PRODUCT NAME")
                || upper.contains("PRICE LIST")
                || upper.contains("APPLICATIONS");
    }

    private static String cleanupProductName(
            String value
    ) {
        String cleaned = cleanSpaces(value);

        cleaned = cleaned
                .replace("™", "")
                .replace("®", "")
                .replace("–", "-")
                .replace("—", "-");

        cleaned = cleanSpaces(cleaned);

        while (cleaned.endsWith("-")
                || cleaned.endsWith(":")) {
            cleaned = cleaned
                    .substring(
                            0,
                            cleaned.length() - 1
                    )
                    .trim();
        }

        return cleaned;
    }

    private static String normalizeProductKey(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase(Locale.US)
                .replace("™", "")
                .replace("®", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String normalizeExtractedText(
            String text
    ) {
        if (text == null) {
            return "";
        }

        String normalized = text
                .replace("ﬀ", "ff")
                .replace("ﬁ", "fi")
                .replace("ﬂ", "fl")
                .replace("–", "-")
                .replace("—", "-")
                .replace('\u00A0', ' ');

        normalized = normalized
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "");

        // Some official PDFs expose the 'ff' ligature in Effective as a control glyph.
        normalized = normalized
                .replaceAll("(?i)\\bEective\\s+Date\\b", "Effective Date")
                .replaceAll("(?i)\\bEfective\\s+Date\\b", "Effective Date");

        return normalized;
    }

    private static String cleanSpaces(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .replace('\t', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static int roundedInt(
            double value
    ) {
        return Math.max(
                0,
                (int) Math.round(value)
        );
    }

    private static int mappedPriceCount(
            CompanyPriceRow row
    ) {
        int count = row.getMrp() > 0 ? 1 : 0;

        if (row.getPrice15() != null) count++;
        if (row.getPrice25() != null) count++;
        if (row.getPrice35() != null) count++;
        if (row.getPrice42() != null) count++;
        if (row.getPrice50() != null) count++;

        return count;
    }

    private static class NumberToken {

        private final int start;
        private final int end;
        private final double value;

        private NumberToken(
                int start,
                int end,
                double value
        ) {
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }
}
