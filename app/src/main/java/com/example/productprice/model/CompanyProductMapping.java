package com.example.productprice.model;

import java.util.Locale;

/**
 * A permanent one-time mapping between one logical app product and one
 * official company product group. The group key is intentionally independent
 * from yearly prices so the same mapping can be reused after future price-list
 * revisions.
 */
public class CompanyProductMapping {

    private final String appProductKey;
    private final String appProductName;
    private final String appCategory;
    private final String companyGroupKey;
    private final String companyProductName;
    private final String stockNo;
    private final long savedAt;

    public CompanyProductMapping(
            String appProductKey,
            String appProductName,
            String appCategory,
            String companyGroupKey,
            String companyProductName,
            String stockNo,
            long savedAt
    ) {
        this.appProductKey = safe(appProductKey);
        this.appProductName = safe(appProductName);
        this.appCategory = safe(appCategory);
        this.companyGroupKey = safe(companyGroupKey);
        this.companyProductName = safe(companyProductName);
        this.stockNo = safe(stockNo);
        this.savedAt = Math.max(0L, savedAt);
    }

    public String getAppProductKey() {
        return appProductKey;
    }

    public String getAppProductName() {
        return appProductName;
    }

    public String getAppCategory() {
        return appCategory;
    }

    public String getCompanyGroupKey() {
        return companyGroupKey;
    }

    public String getCompanyProductName() {
        return companyProductName;
    }

    public String getStockNo() {
        return stockNo;
    }

    public long getSavedAt() {
        return savedAt;
    }

    public static String keyFor(Product product) {
        if (product == null) {
            return "";
        }
        return createKey(product.getCategory(), product.getName());
    }

    public static String createKey(String category, String productName) {
        return normalize(category) + "|" + normalize(productName);
    }

    private static String normalize(String value) {
        return safe(value)
                .toLowerCase(Locale.US)
                .replace("®", "")
                .replace("™", "")
                .replace("’", "'")
                .replace("–", "-")
                .replace("—", "-")
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
