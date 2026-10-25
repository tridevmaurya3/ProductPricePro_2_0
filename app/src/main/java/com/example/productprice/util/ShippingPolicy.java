package com.example.productprice.util;

public final class ShippingPolicy {
  private ShippingPolicy() {}

  public static int calculate(double vp, boolean pickup, int inclusiveCharge) {
    return !pickup && vp > 0 && vp < 100 - 0.000001 ? Math.max(0, inclusiveCharge) : 0;
  }
}
