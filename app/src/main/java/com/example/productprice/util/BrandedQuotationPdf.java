package com.example.productprice.util;

import android.content.ContentResolver;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import com.example.productprice.ProductPriceApplication;
import com.example.productprice.data.AppProfileStore;
import com.example.productprice.model.*;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/** One paginated renderer for export, native print preview and sharing. */
public final class BrandedQuotationPdf {
  private BrandedQuotationPdf() {}

  private static final int W = 595, H = 842;
  private static final float LEFT = 32, RIGHT = 563;
  private static final float[] X = {32, 58, 287, 320, 377, 463, 563};
  private final PdfDocument pdf = new PdfDocument();
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  private Canvas canvas;
  private PdfDocument.Page page;
  private float y;
  private int number;
  private int green = Color.rgb(22, 94, 67),
      ink = Color.rgb(30, 48, 42),
      muted = Color.rgb(91, 112, 103),
      light = Color.rgb(240, 247, 243);
  private String brand = "Health Care Wellness Club",
      contact = "",
      person = "Self",
      address = "",
      reference,
      date,
      validUntil;

  public static void write(
      ContentResolver resolver,
      Uri uri,
      List<CartItem> items,
      int quantity,
      double vp,
      int logistics,
      int grandTotal,
      String orderType,
      Customer customer)
      throws Exception {
    BrandedQuotationPdf r = new BrandedQuotationPdf();
    try {
      r.render(items, quantity, vp, logistics, grandTotal, orderType, customer);
      try (OutputStream out = resolver.openOutputStream(uri)) {
        if (out == null) throw new IllegalStateException("Cannot open PDF output");
        r.pdf.writeTo(out);
      }
    } finally {
      r.pdf.close();
    }
  }

  private void render(
      List<CartItem> items,
      int quantity,
      double vp,
      int logistics,
      int grandTotal,
      String orderType,
      Customer customer) {
    Date now = new Date();
    SimpleDateFormat f = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
    date = f.format(now);
    Calendar valid = Calendar.getInstance();
    valid.setTime(now);
    valid.add(Calendar.DATE, 7);
    validUntil = f.format(valid.getTime());
    if (ProductPriceApplication.getAppContext() != null) {
      AppProfileStore profile = new AppProfileStore(ProductPriceApplication.getAppContext());
      if (!profile.getOrganization().isEmpty()) brand = profile.getOrganization();
      contact = join(profile.getPhone(), profile.getEmail());
      if (ProductPriceApplication.getAppContext()
          .getSharedPreferences("quotation_design", 0)
          .getBoolean("mono", false)) {
        green = Color.BLACK;
        ink = Color.BLACK;
        muted = Color.DKGRAY;
        light = Color.rgb(245, 245, 245);
      }
    }
    if ("Customer".equalsIgnoreCase(orderType) && customer != null) {
      person = customer.getName();
      address = join(customer.getMobile(), customer.getAddress());
    }
    StringBuilder fingerprint =
        new StringBuilder(person + "|" + address + "|" + grandTotal + "|" + logistics);
    for (CartItem item : items)
      fingerprint
          .append('|')
          .append(item.getProduct().getName())
          .append(':')
          .append(item.getQuantity())
          .append(':')
          .append(item.getUnitPrice());
    reference =
        "Q-"
            + new SimpleDateFormat("yyyyMMdd", Locale.US).format(now)
            + "-"
            + Integer.toHexString(fingerprint.toString().hashCode()).toUpperCase(Locale.US);
    nextPage();
    for (int index = 0; index < items.size(); index++) {
      CartItem item = items.get(index);
      List<String> nameLines = wrap(item.getProduct().getName(), X[2] - X[1] - 16, 10);
      List<String> detail =
          wrap(join(item.getProduct().getCategory(), tier(item.getTier())), X[2] - X[1] - 16, 7.5f);
      int offset = 0;
      do {
        if (y + 52 > 716) nextPage();
        int capacity = Math.max(1, (int) ((716 - y - 30) / 14));
        int lines = Math.min(capacity, nameLines.size() - offset);
        boolean last = offset + lines >= nameLines.size();
        float rowHeight = Math.max(44, 12 + lines * 14 + (last ? detail.size() * 11 : 11));
        if (y + rowHeight > 724) {
          nextPage();
          continue;
        }
        rect(LEFT, y, RIGHT, y + rowHeight, index % 2 == 0 ? light : Color.WHITE);
        text(String.valueOf(index + 1), X[0] + 7, y + 18, 9, muted, false);
        for (int n = 0; n < lines; n++)
          text(nameLines.get(offset + n), X[1] + 8, y + 18 + n * 14, 10, ink, true);
        float detailY = y + 18 + lines * 14;
        if (last)
          for (String line : detail) {
            text(line, X[1] + 8, detailY, 7.5f, muted, false);
            detailY += 11;
          }
        else text("Continued on next page", X[1] + 8, detailY, 7.5f, muted, false);
        if (offset == 0) {
          right(String.valueOf(item.getQuantity()), X[3] - 8, y + 19, 10, ink);
          right(decimal(item.getTotalVp()), X[4] - 8, y + 19, 9, ink);
          right(money(item.getUnitPrice()), X[5] - 8, y + 19, 10, ink);
          right(money(item.getTotalPrice()), X[6] - 8, y + 19, 10, green);
        }
        line(LEFT, y + rowHeight, RIGHT, y + rowHeight, Color.rgb(219, 231, 223));
        y += rowHeight;
        offset += lines;
      } while (offset < nameLines.size());
    }
    if (y + 175 > 748) nextPage();
    y += 18;
    text("ORDER SUMMARY", LEFT, y + 14, 9, muted, true);
    text(quantity + " items  |  " + decimal(vp) + " total VP", LEFT, y + 34, 11, ink, true);
    List<String> note =
        wrap(
            "Prices include applicable GST. Delivery is shown separately. Valid for 7 days, subject"
                + " to availability and company price revisions.",
            236,
            8.5f);
    float ny = y + 57;
    for (String s : note) {
      text(s, LEFT, ny, 8.5f, muted, false);
      ny += 12;
    }
    rect(309, y, RIGHT, y + 112, light);
    text("Subtotal", 323, y + 22, 10, muted, false);
    right(money(grandTotal - logistics), RIGHT - 14, y + 22, 10, ink);
    text("Delivery / logistics", 323, y + 44, 10, muted, false);
    right(money(logistics), RIGHT - 14, y + 44, 10, ink);
    line(323, y + 55, RIGHT - 14, y + 55, Color.rgb(207, 224, 214));
    text("TOTAL", 323, y + 82, 11, green, true);
    right(money(grandTotal), RIGHT - 14, y + 84, 17, green);
    y = Math.max(ny, y + 130);
    text("Thank you for choosing " + brand, LEFT, y + 12, 10, green, true);
    finish();
  }

  private void nextPage() {
    if (page != null) finish();
    page = pdf.startPage(new PdfDocument.PageInfo.Builder(W, H, ++number).create());
    canvas = page.getCanvas();
    rect(0, 0, W, 8, green);
    rect(LEFT, 28, 76, 72, green);
    text("HC", 40, 56, 18, Color.WHITE, true);
    List<String> brandLines = wrap(brand, RIGHT - 94, 18);
    float by = 45;
    for (String s : brandLines) {
      text(s, 90, by, 18, green, true);
      by += 21;
    }
    List<String> contacts = wrap(contact, RIGHT - 94, 8.5f);
    for (String s : contacts) {
      text(s, 90, by, 8.5f, muted, false);
      by += 12;
    }
    y = Math.max(94, by + 12);
    text("PRODUCT QUOTATION", LEFT, y, 10, muted, true);
    right(reference, RIGHT, y, 8, muted);
    y += 18;
    line(LEFT, y, RIGHT, y, Color.rgb(207, 224, 214));
    y += 20;
    text("PREPARED FOR", LEFT, y, 8, muted, true);
    right("Issued: " + date + "   |   Valid until: " + validUntil, RIGHT, y, 8, muted);
    y += 17;
    for (String s : wrap(person, RIGHT - LEFT, 12)) {
      text(s, LEFT, y, 12, ink, true);
      y += 16;
    }
    for (String s : wrap(address, RIGHT - LEFT, 9)) {
      text(s, LEFT, y, 9, muted, false);
      y += 13;
    }
    y += 10;
    rect(LEFT, y, RIGHT, y + 25, green);
    String[] heads = {"#", "PRODUCT / PACK", "QTY", "VP", "UNIT PRICE", "AMOUNT"};
    for (int i = 0; i < heads.length; i++) text(heads[i], X[i] + 6, y + 16, 8, Color.WHITE, true);
    y += 25;
  }

  private void finish() {
    line(LEFT, 791, RIGHT, 791, Color.rgb(207, 224, 214));
    text("Prepared with care  •  " + brand, LEFT, 807, 8, muted, false);
    right("Page " + number, RIGHT, 807, 8, muted);
    pdf.finishPage(page);
    page = null;
  }

  private void text(String s, float x, float baseline, float size, int color, boolean bold) {
    p.setTextSize(size);
    p.setColor(color);
    p.setStyle(Paint.Style.FILL);
    p.setTypeface(bold ? Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) : Typeface.SANS_SERIF);
    canvas.drawText(s == null ? "" : s, x, baseline, p);
  }

  private void right(String s, float x, float baseline, float size, int color) {
    p.setTextSize(size);
    p.setTypeface(Typeface.SANS_SERIF);
    text(s, x - p.measureText(s), baseline, size, color, false);
  }

  private void rect(float l, float t, float r, float b, int color) {
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    canvas.drawRect(l, t, r, b, p);
  }

  private void line(float x, float y, float xx, float yy, int color) {
    p.setColor(color);
    p.setStrokeWidth(.7f);
    canvas.drawLine(x, y, xx, yy, p);
  }

  private List<String> wrap(String value, float width, float size) {
    p.setTextSize(size);
    p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String word : (value == null ? "" : value).trim().split("\\s+")) {
      if (word.isEmpty()) continue;
      if (current.length() > 0 && p.measureText(current + " " + word) > width) {
        result.add(current.toString());
        current.setLength(0);
      }
      while (p.measureText(word) > width) {
        int cut = Math.max(1, p.breakText(word, true, width, null));
        result.add(word.substring(0, cut));
        word = word.substring(cut);
      }
      if (current.length() > 0) current.append(' ');
      current.append(word);
    }
    if (current.length() > 0) result.add(current.toString());
    if (result.isEmpty()) result.add("");
    return result;
  }

  private static String money(int value) {
    return "Rs. " + String.format(Locale.US, "%,d", value);
  }

  private static String decimal(double value) {
    return String.format(Locale.US, "%.2f", value);
  }

  private static String join(String a, String b) {
    a = a == null ? "" : a.trim();
    b = b == null ? "" : b.trim();
    return a.isEmpty() ? b : b.isEmpty() ? a : a + "  |  " + b;
  }

  private static String tier(String t) {
    if ("Price@15".equals(t)) return "Bronze Preferred";
    if ("Price@25".equals(t)) return "25% level / Silver";
    if ("Price@35".equals(t)) return "35% level / Gold";
    if ("Price@42".equals(t)) return "42% level";
    if ("Price@50".equals(t)) return "Supervisor";
    return "MRP";
  }
}
