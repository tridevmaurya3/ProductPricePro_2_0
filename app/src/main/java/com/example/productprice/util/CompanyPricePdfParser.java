package com.example.productprice.util;

import android.content.Context;
import android.net.Uri;
import com.example.productprice.model.CompanyPriceDocument;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import java.io.InputStream;
import java.util.regex.*;

/** Opens the original company PDFs; the layout reader handles merged cells and new headings. */
public final class CompanyPricePdfParser {
  private CompanyPricePdfParser() {}

  public static CompanyPriceDocument parse(Context context, Uri uri, String name) throws Exception {
    if (context == null || uri == null) throw new IllegalArgumentException("Select a PDF");
    PDFBoxResourceLoader.init(context.getApplicationContext());
    CompanyPriceDocument result = new CompanyPriceDocument();
    result.setSourceName(name);
    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
      if (input == null) throw new IllegalArgumentException("Cannot open PDF");
      try (PDDocument pdf = PDDocument.load(input)) {
        LayoutCatalogParser.read(pdf, result);
      }
    }
    return result;
  }

  static String readEffectiveDate(String text) {
    String normalized = text.replaceAll("(?i)(?<=\\d)(st|nd|rd|th)", "");
    Matcher date =
        Pattern.compile(
                "(?i)\\b(\\d{1,2})\\s+(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{4})\\b")
            .matcher(normalized);
    return date.find() ? date.group(1) + " " + date.group(2) + " " + date.group(3) : "";
  }
}
