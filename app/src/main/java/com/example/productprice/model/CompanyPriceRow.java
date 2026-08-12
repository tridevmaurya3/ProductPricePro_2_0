package com.example.productprice.model;

public class CompanyPriceRow {

    private String stockNo;
    private String productName;
    private double volumePoint;

    private int mrp;
    private int retailPrice;
    private int earnBase;

    private Integer price15;
    private Integer price25;
    private Integer price35;
    private Integer price42;
    private Integer price50;

    private String rawLine;

    public CompanyPriceRow() {
        stockNo = "";
        productName = "";
        rawLine = "";
    }

    public String getStockNo() {
        return stockNo;
    }

    public void setStockNo(String stockNo) {
        this.stockNo = stockNo == null ? "" : stockNo.trim();
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName == null ? "" : productName.trim();
    }

    public double getVolumePoint() {
        return volumePoint;
    }

    public void setVolumePoint(double volumePoint) {
        this.volumePoint = Math.max(0d, volumePoint);
    }

    public int getMrp() {
        return mrp;
    }

    public void setMrp(int mrp) {
        this.mrp = Math.max(0, mrp);
    }

    public int getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(int retailPrice) {
        this.retailPrice = Math.max(0, retailPrice);
    }

    public int getEarnBase() {
        return earnBase;
    }

    public void setEarnBase(int earnBase) {
        this.earnBase = Math.max(0, earnBase);
    }

    public Integer getPrice15() {
        return price15;
    }

    public void setPrice15(Integer price15) {
        this.price15 = sanitizeNullablePrice(price15);
    }

    public Integer getPrice25() {
        return price25;
    }

    public void setPrice25(Integer price25) {
        this.price25 = sanitizeNullablePrice(price25);
    }

    public Integer getPrice35() {
        return price35;
    }

    public void setPrice35(Integer price35) {
        this.price35 = sanitizeNullablePrice(price35);
    }

    public Integer getPrice42() {
        return price42;
    }

    public void setPrice42(Integer price42) {
        this.price42 = sanitizeNullablePrice(price42);
    }

    public Integer getPrice50() {
        return price50;
    }

    public void setPrice50(Integer price50) {
        this.price50 = sanitizeNullablePrice(price50);
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine == null ? "" : rawLine;
    }

    public boolean hasCoreIdentity() {
        return !stockNo.isEmpty() && !productName.isEmpty();
    }

    public boolean hasAnyMappedPrice() {
        return mrp > 0
                || price15 != null
                || price25 != null
                || price35 != null
                || price42 != null
                || price50 != null;
    }

    private Integer sanitizeNullablePrice(Integer price) {
        if (price == null || price < 0) {
            return null;
        }

        return price;
    }
}
