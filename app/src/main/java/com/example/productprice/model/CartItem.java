package com.example.productprice.model;

public class CartItem {
    private final Product product;
    private int quantity;
    private final String tier;
    private final int unitPrice;

    public CartItem(Product product, int quantity, String tier) {
        this.product = product;
        this.quantity = quantity;
        this.tier = tier;
        this.unitPrice = product.getPriceForTier(tier);
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getTier() { return tier; }
    public int getUnitPrice() { return unitPrice; }
    public int getTotalPrice() { return unitPrice * quantity; }
    public double getTotalVp() { return product.getVp() * quantity; }
}
