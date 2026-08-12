package com.example.productprice.model;

public class OfficialPriceUpdate {

    private final long productId;
    private final String productName;
    private final String companyProductName;
    private final String stockNo;
    private final double confidence;

    private final int oldFullPrice;
    private final int oldPrice15;
    private final int oldPrice25;
    private final int oldPrice35;
    private final int oldPrice42;
    private final int oldPrice50;

    private final int newFullPrice;
    private final int newPrice15;
    private final int newPrice25;
    private final int newPrice35;
    private final int newPrice42;
    private final int newPrice50;

    public OfficialPriceUpdate(
            long productId,
            String productName,
            String companyProductName,
            String stockNo,
            double confidence,
            int oldFullPrice,
            int oldPrice15,
            int oldPrice25,
            int oldPrice35,
            int oldPrice42,
            int oldPrice50,
            int newFullPrice,
            int newPrice15,
            int newPrice25,
            int newPrice35,
            int newPrice42,
            int newPrice50
    ) {
        this.productId = productId;
        this.productName = safe(productName);
        this.companyProductName = safe(companyProductName);
        this.stockNo = safe(stockNo);
        this.confidence = Math.max(0d, Math.min(1d, confidence));

        this.oldFullPrice = Math.max(0, oldFullPrice);
        this.oldPrice15 = Math.max(0, oldPrice15);
        this.oldPrice25 = Math.max(0, oldPrice25);
        this.oldPrice35 = Math.max(0, oldPrice35);
        this.oldPrice42 = Math.max(0, oldPrice42);
        this.oldPrice50 = Math.max(0, oldPrice50);

        this.newFullPrice = Math.max(0, newFullPrice);
        this.newPrice15 = Math.max(0, newPrice15);
        this.newPrice25 = Math.max(0, newPrice25);
        this.newPrice35 = Math.max(0, newPrice35);
        this.newPrice42 = Math.max(0, newPrice42);
        this.newPrice50 = Math.max(0, newPrice50);
    }

    public long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCompanyProductName() {
        return companyProductName;
    }

    public String getStockNo() {
        return stockNo;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getOldFullPrice() {
        return oldFullPrice;
    }

    public int getOldPrice15() {
        return oldPrice15;
    }

    public int getOldPrice25() {
        return oldPrice25;
    }

    public int getOldPrice35() {
        return oldPrice35;
    }

    public int getOldPrice42() {
        return oldPrice42;
    }

    public int getOldPrice50() {
        return oldPrice50;
    }

    public int getNewFullPrice() {
        return newFullPrice;
    }

    public int getNewPrice15() {
        return newPrice15;
    }

    public int getNewPrice25() {
        return newPrice25;
    }

    public int getNewPrice35() {
        return newPrice35;
    }

    public int getNewPrice42() {
        return newPrice42;
    }

    public int getNewPrice50() {
        return newPrice50;
    }

    public boolean isChanged() {
        return oldFullPrice != newFullPrice
                || oldPrice15 != newPrice15
                || oldPrice25 != newPrice25
                || oldPrice35 != newPrice35
                || oldPrice42 != newPrice42
                || oldPrice50 != newPrice50;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
