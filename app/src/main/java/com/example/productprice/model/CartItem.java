package com.example.productprice.model;

public class CartItem {

  private final Product product;
  private int quantity;
  private final String tier;
  private final int unitPrice;

  /** सामान्य order बनाते समय इस्तेमाल होगा। Unit price selected product tier से लिया जाएगा। */
  public CartItem(Product product, int quantity, String tier) {
    this(
        product,
        quantity,
        tier,
        product == null ? 0 : product.getPriceForTier(normalizeTier(tier)));
  }

  /**
   * Saved quotation reopen करते समय इस्तेमाल होगा। इसमें quotation save होने के समय का exact unit
   * price दिया जा सकता है।
   */
  public CartItem(Product product, int quantity, String tier, int unitPrice) {
    this.product = product == null ? new Product() : product;

    this.quantity = Math.max(1, quantity);

    this.tier = normalizeTier(tier);

    this.unitPrice = Math.max(0, unitPrice);
  }

  public Product getProduct() {
    return product;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = Math.max(1, quantity);
  }

  public String getTier() {
    return tier;
  }

  public int getUnitPrice() {
    return unitPrice;
  }

  public int getTotalPrice() {
    return unitPrice * quantity;
  }

  public double getTotalVp() {
    return product.getVp() * quantity;
  }

  private static String normalizeTier(String tier) {
    return Product.normalizeTier(tier);
  }
}
