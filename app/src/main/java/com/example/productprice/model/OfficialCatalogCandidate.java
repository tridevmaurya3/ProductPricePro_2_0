package com.example.productprice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A product or same-price flavour group discovered in the official PDFs that
 * is not yet represented in the app catalogue.
 */
public class OfficialCatalogCandidate {

    private final String groupKey;
    private final String category;
    private final String logicalName;
    private final double volumePoint;
    private final int fullPrice;
    private final int price15;
    private final int price25;
    private final int price35;
    private final int price42;
    private final int price50;
    private final List<Variant> variants;

    public OfficialCatalogCandidate(
            String groupKey,
            String category,
            String logicalName,
            double volumePoint,
            int fullPrice,
            int price15,
            int price25,
            int price35,
            int price42,
            int price50,
            List<Variant> variants
    ) {
        this.groupKey = safe(groupKey);
        this.category = safe(category);
        this.logicalName = safe(logicalName);
        this.volumePoint = Math.max(0d, volumePoint);
        this.fullPrice = Math.max(0, fullPrice);
        this.price15 = Math.max(0, price15);
        this.price25 = Math.max(0, price25);
        this.price35 = Math.max(0, price35);
        this.price42 = Math.max(0, price42);
        this.price50 = Math.max(0, price50);
        this.variants = variants == null
                ? new ArrayList<>()
                : new ArrayList<>(variants);
    }

    public String getGroupKey() {
        return groupKey;
    }

    public String getCategory() {
        return category;
    }

    public String getLogicalName() {
        return logicalName;
    }

    public double getVolumePoint() {
        return volumePoint;
    }

    public int getFullPrice() {
        return fullPrice;
    }

    public int getPrice15() {
        return price15;
    }

    public int getPrice25() {
        return price25;
    }

    public int getPrice35() {
        return price35;
    }

    public int getPrice42() {
        return price42;
    }

    public int getPrice50() {
        return price50;
    }

    public List<Variant> getVariants() {
        return Collections.unmodifiableList(variants);
    }

    public int getVariantCount() {
        return variants.size();
    }

    public Product toSmartGroupProduct() {
        Product product = new Product();
        product.setCategory(category);
        product.setName(logicalName);
        product.setVp(volumePoint);
        product.setFullPrice(fullPrice);
        product.setPrice15(price15);
        product.setPrice25(price25);
        product.setPrice35(price35);
        product.setPrice42(price42);
        product.setPrice50(price50);
        product.setActive(true);
        return product;
    }

    public List<Product> toOfficialVariantProducts() {
        List<Product> products = new ArrayList<>();

        if (variants.isEmpty()) {
            products.add(toSmartGroupProduct());
            return products;
        }

        for (Variant variant : variants) {
            Product product = new Product();
            product.setCategory(category);
            product.setName(variant.getProductName());
            product.setVp(volumePoint);
            product.setFullPrice(fullPrice);
            product.setPrice15(price15);
            product.setPrice25(price25);
            product.setPrice35(price35);
            product.setPrice42(price42);
            product.setPrice50(price50);
            product.setActive(true);
            products.add(product);
        }

        return products;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static class Variant {
        private final String stockNo;
        private final String productName;

        public Variant(String stockNo, String productName) {
            this.stockNo = safe(stockNo);
            this.productName = safe(productName);
        }

        public String getStockNo() {
            return stockNo;
        }

        public String getProductName() {
            return productName;
        }
    }
}
