package com.example.productprice.util;

import java.text.SimpleDateFormat;
import java.util.*;

public final class CatalogDates {
  private CatalogDates() {}

  public static boolean isOlder(String next, String current) {
    Date a = parse(next), b = parse(current);
    return a != null && b != null && a.before(b);
  }

  private static Date parse(String s) {
    try {
      SimpleDateFormat f = new SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH);
      f.setLenient(false);
      return f.parse(s.replaceAll("(?<=\\d)(st|nd|rd|th)", ""));
    } catch (Exception e) {
      return null;
    }
  }
}
