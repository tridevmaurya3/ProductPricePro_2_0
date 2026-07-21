package com.example.productprice.model;

import java.util.Locale;
import java.util.Objects;

public class SavedQuotationItem {

    private long id;
    private long quotationId;

    private long productId;
    private String category;
    private String productName;

    private double volumePoint;

    private int fullPrice;
    private int price15;
    private int price25;
    private int price35;
    private int price42;
    private int price50;

    private String selectedTier;
    private int unitPrice;
    private int quantity;
    private int sortOrder;

    private long createdAt;

    public SavedQuotationItem() {
        this.id = 0;
        this.quotationId = 0;

        this.productId = 0;
        this.category = "";
        this.productName = "";

        this.volumePoint = 0d;

        this.fullPrice = 0;
        this.price15 = 0;
        this.price25 = 0;
        this.price35 = 0;
        this.price42 = 0;
        this.price50 = 0;

        this.selectedTier = "Full Price";
        this.unitPrice = 0;
        this.quantity = 1;
        this.sortOrder = 0;

        this.createdAt = System.currentTimeMillis();
    }

    public SavedQuotationItem(
            long quotationId,
            Product product,
            String selectedTier,
            int quantity,
            int sortOrder
    ) {
        this();

        setQuotationId(quotationId);
        setProduct(product);
        setSelectedTier(selectedTier);
        setQuantity(quantity);
        setSortOrder(sortOrder);

        if (product != null) {
            setUnitPrice(
                    product.getPriceForTier(
                            getSelectedTier()
                    )
            );
        }
    }

    public SavedQuotationItem(
            long id,
            long quotationId,
            long productId,
            String category,
            String productName,
            double volumePoint,
            int fullPrice,
            int price15,
            int price25,
            int price35,
            int price42,
            int price50,
            String selectedTier,
            int unitPrice,
            int quantity,
            int sortOrder,
            long createdAt
    ) {
        this.id = Math.max(0, id);
        this.quotationId = Math.max(0, quotationId);

        this.productId = Math.max(0, productId);
        this.category = safeText(category);
        this.productName = safeText(productName);

        this.volumePoint = Math.max(0d, volumePoint);

        this.fullPrice = Math.max(0, fullPrice);
        this.price15 = Math.max(0, price15);
        this.price25 = Math.max(0, price25);
        this.price35 = Math.max(0, price35);
        this.price42 = Math.max(0, price42);
        this.price50 = Math.max(0, price50);

        setSelectedTier(selectedTier);

        this.unitPrice = Math.max(0, unitPrice);
        this.quantity = Math.max(1, quantity);
        this.sortOrder = Math.max(0, sortOrder);

        this.createdAt = createdAt > 0
                ? createdAt
                : System.currentTimeMillis();
    }

    public static SavedQuotationItem fromCartItem(
            long quotationId,
            CartItem cartItem,
            int sortOrder
    ) {
        SavedQuotationItem savedItem =
                new SavedQuotationItem();

        savedItem.setQuotationId(
                quotationId
        );

        savedItem.setSortOrder(
                sortOrder
        );

        if (cartItem == null) {
            return savedItem;
        }

        Product product =
                cartItem.getProduct();

        savedItem.setProduct(
                product
        );

        savedItem.setSelectedTier(
                cartItem.getTier()
        );

        savedItem.setQuantity(
                cartItem.getQuantity()
        );

        savedItem.setUnitPrice(
                cartItem.getUnitPrice()
        );

        return savedItem;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = Math.max(0, id);
    }

    public long getQuotationId() {
        return quotationId;
    }

    public void setQuotationId(long quotationId) {
        this.quotationId = Math.max(
                0,
                quotationId
        );
    }

    public long getProductId() {
        return productId;
    }

    public void setProductId(long productId) {
        this.productId = Math.max(
                0,
                productId
        );
    }

    public String getCategory() {
        return safeText(category);
    }

    public void setCategory(String category) {
        this.category = safeText(category);
    }

    public String getProductName() {
        return safeText(productName);
    }

    public void setProductName(String productName) {
        this.productName = safeText(
                productName
        );
    }

    public double getVolumePoint() {
        return volumePoint;
    }

    public void setVolumePoint(double volumePoint) {
        this.volumePoint = Math.max(
                0d,
                volumePoint
        );
    }

    public int getFullPrice() {
        return fullPrice;
    }

    public void setFullPrice(int fullPrice) {
        this.fullPrice = Math.max(
                0,
                fullPrice
        );
    }

    public int getPrice15() {
        return price15;
    }

    public void setPrice15(int price15) {
        this.price15 = Math.max(
                0,
                price15
        );
    }

    public int getPrice25() {
        return price25;
    }

    public void setPrice25(int price25) {
        this.price25 = Math.max(
                0,
                price25
        );
    }

    public int getPrice35() {
        return price35;
    }

    public void setPrice35(int price35) {
        this.price35 = Math.max(
                0,
                price35
        );
    }

    public int getPrice42() {
        return price42;
    }

    public void setPrice42(int price42) {
        this.price42 = Math.max(
                0,
                price42
        );
    }

    public int getPrice50() {
        return price50;
    }

    public void setPrice50(int price50) {
        this.price50 = Math.max(
                0,
                price50
        );
    }

    public String getSelectedTier() {
        String cleanTier =
                safeText(selectedTier);

        switch (cleanTier) {
            case "Price@15":
            case "Price@25":
            case "Price@35":
            case "Price@42":
            case "Price@50":
                return cleanTier;

            default:
                return "Full Price";
        }
    }

    public void setSelectedTier(
            String selectedTier
    ) {
        String cleanTier =
                safeText(selectedTier);

        switch (cleanTier) {
            case "Price@15":
            case "Price@25":
            case "Price@35":
            case "Price@42":
            case "Price@50":
                this.selectedTier = cleanTier;
                break;

            default:
                this.selectedTier = "Full Price";
                break;
        }
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(int unitPrice) {
        this.unitPrice = Math.max(
                0,
                unitPrice
        );
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(
                1,
                quantity
        );
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = Math.max(
                0,
                sortOrder
        );
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt > 0
                ? createdAt
                : System.currentTimeMillis();
    }

    public void setProduct(Product product) {
        if (product == null) {
            return;
        }

        this.productId =
                product.getId();

        this.category =
                safeText(
                        product.getCategory()
                );

        this.productName =
                safeText(
                        product.getName()
                );

        this.volumePoint =
                Math.max(
                        0d,
                        product.getVp()
                );

        this.fullPrice =
                Math.max(
                        0,
                        product.getFullPrice()
                );

        this.price15 =
                Math.max(
                        0,
                        product.getPrice15()
                );

        this.price25 =
                Math.max(
                        0,
                        product.getPrice25()
                );

        this.price35 =
                Math.max(
                        0,
                        product.getPrice35()
                );

        this.price42 =
                Math.max(
                        0,
                        product.getPrice42()
                );

        this.price50 =
                Math.max(
                        0,
                        product.getPrice50()
                );
    }

    public int getPriceForSelectedTier() {
        switch (getSelectedTier()) {
            case "Price@15":
                return getPrice15();

            case "Price@25":
                return getPrice25();

            case "Price@35":
                return getPrice35();

            case "Price@42":
                return getPrice42();

            case "Price@50":
                return getPrice50();

            default:
                return getFullPrice();
        }
    }

    public int getEffectiveUnitPrice() {
        if (getUnitPrice() > 0) {
            return getUnitPrice();
        }

        return getPriceForSelectedTier();
    }

    public int getTotalPrice() {
        return getEffectiveUnitPrice()
                * getQuantity();
    }

    public double getTotalVp() {
        return getVolumePoint()
                * getQuantity();
    }

    public String getTotalVpDisplay() {
        return String.format(
                Locale.getDefault(),
                "%.2f VP",
                getTotalVp()
        );
    }

    public boolean isValid() {
        return !getProductName().isEmpty()
                && getQuantity() > 0
                && getEffectiveUnitPrice() >= 0;
    }

    private static String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    @Override
    public String toString() {
        return getProductName()
                + " × "
                + getQuantity();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof SavedQuotationItem)) {
            return false;
        }

        SavedQuotationItem item =
                (SavedQuotationItem) object;

        if (id > 0 && item.id > 0) {
            return id == item.id;
        }

        return quotationId == item.quotationId
                && productId == item.productId
                && sortOrder == item.sortOrder
                && Objects.equals(
                getSelectedTier(),
                item.getSelectedTier()
        );
    }

    @Override
    public int hashCode() {
        if (id > 0) {
            return Long.valueOf(id).hashCode();
        }

        return Objects.hash(
                quotationId,
                productId,
                getSelectedTier(),
                sortOrder
        );
    }
}