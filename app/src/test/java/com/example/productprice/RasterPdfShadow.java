package com.example.productprice;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

/**
 * Robolectric 4.12 lacks PdfDocument natives. Render production Canvas calls with native Skia into
 * page bitmaps and put them in a real PDF for visual QA. Production PDF serialization remains
 * Android's own PdfDocument implementation.
 */
@Implements(PdfDocument.class)
public class RasterPdfShadow {
  private final List<Bitmap> bitmaps = new ArrayList<>();

  @Implementation
  protected void __constructor__() {}

  @Implementation
  protected PdfDocument.Page startPage(PdfDocument.PageInfo info) {
    Bitmap bitmap =
        Bitmap.createBitmap(
            info.getPageWidth() * 2, info.getPageHeight() * 2, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);
    canvas.scale(2, 2);
    bitmaps.add(bitmap);
    return ReflectionHelpers.callConstructor(
        PdfDocument.Page.class,
        ReflectionHelpers.ClassParameter.from(Canvas.class, canvas),
        ReflectionHelpers.ClassParameter.from(PdfDocument.PageInfo.class, info));
  }

  @Implementation
  protected void finishPage(PdfDocument.Page page) {}

  @Implementation
  protected void close() {}

  @Implementation
  protected void writeTo(OutputStream out) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      for (Bitmap bitmap : bitmaps) {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
        PDPage page = new PDPage(new PDRectangle(bitmap.getWidth() / 2f, bitmap.getHeight() / 2f));
        doc.addPage(page);
        try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
          c.drawImage(
              PDImageXObject.createFromByteArray(doc, png.toByteArray(), "native-canvas-page"),
              0,
              0,
              page.getMediaBox().getWidth(),
              page.getMediaBox().getHeight());
        }
      }
      doc.save(out);
    }
  }
}
