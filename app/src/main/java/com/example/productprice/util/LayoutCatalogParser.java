package com.example.productprice.util;

import android.graphics.PointF;
import com.example.productprice.model.CompanyPriceDocument;
import com.example.productprice.model.CompanyPriceRow;
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import java.io.IOException;
import java.util.*;

/** Reads identities independently of merged price cells. No product/category whitelist. */
public final class LayoutCatalogParser {
  private LayoutCatalogParser() {}

  public static void read(PDDocument pdf, CompanyPriceDocument output) throws IOException {
    Positions positions = new Positions();
    String text = positions.getText(pdf);
    String upper = text.toUpperCase(Locale.US);
    boolean associate = upper.contains("ASSOCIATE PRICE LIST") || upper.contains("SUPERVISOR");
    boolean preferred = upper.contains("PREFERRED CUSTOMERS") || upper.contains("BRONZE PREFERRED");
    if (!associate && !preferred) throw new IOException("Unrecognized company price list");
    output.setDocumentType(
        associate
            ? CompanyPriceDocument.DocumentType.ASSOCIATE
            : CompanyPriceDocument.DocumentType.PREFERRED_CUSTOMER);
    output.setRawText(text);
    output.setEffectiveDate(CompanyPricePdfParser.readEffectiveDate(text));
    Map<String, CompanyPriceRow> rows = new LinkedHashMap<>();
    String category = "";
    int pageIndex = 0;
    for (PDPage page : pdf.getPages()) {
      if (page.getRotation() != 0)
        throw new IOException("Please use the original unrotated company PDF.");
      List<Line> lines = lines(positions.pages.get(pageIndex++));
      int count = associate ? 9 : 6;
      List<Anchor> anchors = new ArrayList<>();
      for (Line line : lines) {
        List<Word> numbers = new ArrayList<>();
        for (Word w : line.words)
          if (w.x > page.getCropBox().getWidth() * .35f
              && w.text.matches("\\(?\\d+(?:\\.\\d+)?\\)?")) numbers.add(w);
        if (numbers.size() < count) continue;
        List<Word> tail = numbers.subList(numbers.size() - count, numbers.size());
        double[] values = new double[count];
        for (int i = 0; i < count; i++)
          values[i] = Double.parseDouble(tail.get(i).text.replace("(", "").replace(")", ""));
        if (values[0] < 1 || values[0] > 100 || values[1] <= 0 || values[2] < 1) continue;
        boolean valid = true;
        for (int i = associate ? 5 : 3; i < count; i++)
          if (values[i] <= 0 || values[i] > values[2]) valid = false;
        if (valid) anchors.add(new Anchor(line.y, tail.get(0).x, tail.get(count - 1).x, values));
      }
      if (!anchors.isEmpty()) {
        float[] centers = new float[count];
        for (int i = 0; i < count; i++) {
          List<Float> xs = new ArrayList<>();
          for (Line line : lines) {
            List<Word> ns = new ArrayList<>();
            for (Word w : line.words)
              if (w.x > page.getCropBox().getWidth() * .35f
                  && w.text.matches("\\(?\\d+(?:\\.\\d+)?\\)?")) ns.add(w);
            if (ns.size() == count) xs.add(ns.get(i).center);
          }
          Collections.sort(xs);
          if (!xs.isEmpty()) centers[i] = xs.get(xs.size() / 2);
        }
        List<Anchor> repaired = new ArrayList<>();
        for (Line line : lines) {
          StringBuilder[] cells = new StringBuilder[count];
          for (int i = 0; i < count; i++) cells[i] = new StringBuilder();
          for (Word word : line.words) {
            if (word.center < centers[0] - 12) continue;
            int col = 0;
            while (col < count - 1 && word.center > (centers[col] + centers[col + 1]) / 2) col++;
            cells[col].append(word.text);
          }
          double[] v = new double[count];
          boolean valid = true;
          for (int i = 0; i < count; i++)
            try {
              v[i] = Double.parseDouble(cells[i].toString().replace("(", "").replace(")", ""));
            } catch (Exception e) {
              valid = false;
            }
          if (!valid || v[0] < 1 || v[0] > 100 || v[1] <= 0 || v[2] <= 0) continue;
          for (int i = associate ? 5 : 3; i < count; i++)
            if (v[i] <= 0 || v[i] > v[2]) valid = false;
          if (valid) repaired.add(new Anchor(line.y, centers[0] - 2, centers[count - 1] - 3, v));
        }
        if (!repaired.isEmpty()) anchors = repaired;
      }
      for (Anchor anchor : anchors) {
        CompanyPriceRow group = new CompanyPriceRow();
        group.setStockNo("@GROUP" + pageIndex + "-" + anchor.y);
        double[] v = anchor.values;
        group.setVolumePoint(v[1]);
        group.setMrp((int) Math.round(v[2]));
        if (associate) {
          group.setPrice25((int) Math.round(v[5]));
          group.setPrice35((int) Math.round(v[6]));
          group.setPrice42((int) Math.round(v[7]));
          group.setPrice50((int) Math.round(v[8]));
        } else {
          group.setPrice15((int) Math.round(v[3]));
          group.setPrice25((int) Math.round(v[4]));
          group.setPrice35((int) Math.round(v[5]));
        }
        output.addPriceGroup(group);
      }
      if (anchors.isEmpty()) {
        for (Line line : lines)
          if (line.text(0, Float.MAX_VALUE).matches("^[A-Z0-9]{3,8}\\s+.*")
              && line.words.get(0).text.matches(".*\\d.*"))
            throw new IOException(
                "Page "
                    + pageIndex
                    + ": product identities found but price columns could not be read. Import"
                    + " cancelled.");
        continue;
      }
      float priceX = Collections.min(anchors, Comparator.comparingDouble(a -> a.x)).x - 5;
      float lastX = anchors.get(0).lastX;
      Borders borders = new Borders(page);
      borders.processPage(page);
      List<Float> boundaries = new ArrayList<>();
      for (float[] edge : borders.edges)
        if (edge[0] < priceX && edge[2] > lastX && Math.abs(edge[1] - edge[3]) < .8f)
          boundaries.add(edge[1]);
      Collections.sort(boundaries);
      float nameX = 0;
      for (Line line : lines)
        if (line.words.size() > 1
            && line.words.get(0).text.matches("[A-Z0-9]{3,8}")
            && line.words.get(0).text.matches(".*\\d.*")
            && line.words.get(0).x < page.getCropBox().getWidth() * .15f) {
          nameX = line.words.get(1).x;
          break;
        }
      boolean inTable = !category.isEmpty();
      for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
        Line line = lines.get(lineIndex);
        String all = line.text(0, Float.MAX_VALUE).trim();
        if (all.startsWith("NOTES:")
            || all.equals("APPLICATIONS")
            || all.equals("ART OF PROMOTION")) {
          inTable = false;
          continue;
        }
        boolean stockLine =
            !line.words.isEmpty()
                && line.words.get(0).x < page.getCropBox().getWidth() * .15f
                && line.words.get(0).text.matches("[A-Z0-9]{2,8}")
                && line.words.get(0).text.matches(".*\\d.*");
        String heading = stockLine ? "" : heading(all);
        if (!heading.isEmpty()) {
          category = heading;
          inTable = true;
          continue;
        }
        if (!inTable) continue;
        String left = line.text(0, priceX).trim();
        if (nameX > 0) {
          String code = line.text(0, nameX - 2).replace(" ", "");
          if (code.matches("[A-Z0-9]{3,8}") && code.matches(".*\\d.*"))
            left = code + " " + line.text(nameX - 2, priceX).trim();
        }
        java.util.regex.Matcher id =
            java.util.regex.Pattern.compile("^([A-Z0-9]{3,8})\\s+(.+)$").matcher(left);
        if (!id.matches()
            || !id.group(1).matches(".*\\d.*")
            || line.words.get(0).x > page.getCropBox().getWidth() * .15f) continue;
        String stock = id.group(1);
        String name = id.group(2).replace("®", "").replace("™", "").trim();
        // Wrapped names continue in the product column, never in a price column.
        for (int j = lineIndex + 1; j < lines.size(); j++) {
          Line next = lines.get(j);
          String continuation = next.text(0, priceX).trim();
          if (next.y - line.y > 12
              || continuation.isEmpty()
              || continuation.matches("^[A-Z0-9]{3,8}\\s+.*")
              || !heading(next.text(0, Float.MAX_VALUE)).isEmpty()) break;
          if (next.words.get(0).x <= line.words.get(0).x + 8) break;
          name += " " + continuation;
        }
        float top = -1, bottom = page.getCropBox().getHeight() + 1;
        for (float y : boundaries) {
          if (y < line.y - 1) top = Math.max(top, y);
          if (y > line.y + 1) bottom = Math.min(bottom, y);
        }
        List<Anchor> matches = new ArrayList<>();
        for (Anchor anchor : anchors) if (anchor.y > top && anchor.y < bottom) matches.add(anchor);
        if (matches.size() != 1) {
          matches.clear();
          for (Anchor anchor : anchors) if (Math.abs(anchor.y - line.y) < 3) matches.add(anchor);
        }
        CompanyPriceRow row = new CompanyPriceRow();
        row.setStockNo(stock);
        row.setProductName(name);
        row.setCategory(category);
        row.setRawLine("Page " + pageIndex + " | " + left);
        output.recordIdentity(stock);
        if (matches.size() == 1) {
          double[] v = matches.get(0).values;
          row.setVolumePoint(v[1]);
          row.setMrp((int) Math.round(v[2]));
          if (associate) {
            row.setRetailPrice((int) Math.round(v[3]));
            row.setEarnBase((int) Math.round(v[4]));
            row.setPrice25((int) Math.round(v[5]));
            row.setPrice35((int) Math.round(v[6]));
            row.setPrice42((int) Math.round(v[7]));
            row.setPrice50((int) Math.round(v[8]));
          } else {
            row.setPrice15((int) Math.round(v[3]));
            row.setPrice25((int) Math.round(v[4]));
            row.setPrice35((int) Math.round(v[5]));
          }
        } else
          output.addWarning(
              "Stock "
                  + stock
                  + " / "
                  + name
                  + ": price cell could not be resolved on page "
                  + pageIndex);
        CompanyPriceRow old = rows.get(stock);
        if (old == null || (!old.hasAnyMappedPrice() && row.hasAnyMappedPrice()))
          rows.put(stock, row);
        else if (old.hasAnyMappedPrice()
            && row.hasAnyMappedPrice()
            && (old.getMrp() != row.getMrp()
                || Math.abs(old.getVolumePoint() - row.getVolumePoint()) > .01))
          output.addWarning("Stock " + stock + ": conflicting duplicate rows");
      }
    }
    for (CompanyPriceRow row : rows.values()) output.addRow(row);
    if (rows.isEmpty())
      throw new IOException("No products could be read. Use the original text-based company PDFs.");
  }

  private static String heading(String text) {
    String value = text.replace("’", "'").replace("®", "").trim().replaceAll("\\s+", " ");
    if (value.length() < 4
        || value.length() > 80
        || !value.equals(value.toUpperCase(Locale.US))
        || !value.matches("[A-Z][A-Z0-9 &'()/+.-]+")
        || value.contains("PRICE LIST")
        || value.contains("PREFERRED CUSTOMER")
        || value.contains("NUTRITION WHOLESALE")
        || value.contains("LITERATURE")
        || value.contains("STOCK")
        || value.equals("PRODUCT NAME")
        || value.equals("APPLICATIONS")
        || value.equals("ART OF PROMOTION")
        || value.equals("INNER & OUTER NUTRITION PRODUCT")
        || value.equals("MRP")
        || value.equals("NOTES")
        || value.contains("INCLUSIVE")
        || value.equals("ASSOCIATES")) return "";
    return value;
  }

  private static List<Line> lines(List<Glyph> glyphs) {
    glyphs.sort(Comparator.comparingDouble((Glyph g) -> g.y).thenComparingDouble(g -> g.x));
    List<Line> result = new ArrayList<>();
    for (Glyph glyph : glyphs) {
      Line line = result.isEmpty() ? null : result.get(result.size() - 1);
      if (line == null || Math.abs(line.y - glyph.y) > 2.2f) {
        line = new Line(glyph.y);
        result.add(line);
      }
      line.glyphs.add(glyph);
    }
    for (Line line : result) line.build();
    return result;
  }

  private static class Glyph {
    float x, y, width;
    String text;

    Glyph(TextPosition p) {
      x = p.getXDirAdj();
      y = p.getYDirAdj();
      width = p.getWidthDirAdj();
      text = p.getUnicode();
      if (p.getFont() instanceof com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont) {
        StringBuilder recovered = new StringBuilder();
        for (int code : p.getCharacterCodes()) {
          try {
            String u = p.getFont().toUnicode(code);
            if (u == null) {
              String n =
                  ((com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont) p.getFont())
                      .getEncoding()
                      .getName(code);
              if (n != null && n.matches("[a-z](?:_[a-z])+")) u = n.replace("_", "");
            }
            recovered.append(u == null ? "" : u);
          } catch (IOException ignored) {
            recovered.append(text);
          }
        }
        if (recovered.length() > 0) text = recovered.toString();
      }
    }
  }

  private static class Word {
    float x, center;
    String text;

    Word(float x, float end, String text) {
      this.x = x;
      this.center = (x + end) / 2;
      this.text = text;
    }
  }

  private static class Line {
    float y;
    List<Glyph> glyphs = new ArrayList<>();
    List<Word> words = new ArrayList<>();

    Line(float y) {
      this.y = y;
    }

    void build() {
      glyphs.sort(Comparator.comparingDouble(g -> g.x));
      StringBuilder s = new StringBuilder();
      float x = 0, end = -100;
      for (Glyph g : glyphs) {
        if (g.text.trim().isEmpty() || g.x - end > 3f) {
          if (s.length() > 0) {
            words.add(new Word(x, end, s.toString()));
            s.setLength(0);
          }
        }
        if (!g.text.trim().isEmpty()) {
          if (s.length() == 0) x = g.x;
          s.append(g.text);
        }
        end = g.x + g.width;
      }
      if (s.length() > 0) words.add(new Word(x, end, s.toString()));
    }

    String text(float from, float to) {
      StringBuilder s = new StringBuilder();
      for (Word w : words)
        if (w.x >= from && w.x < to) {
          if (s.length() > 0) s.append(' ');
          s.append(w.text);
        }
      return s.toString();
    }
  }

  private static class Anchor {
    float y, x, lastX;
    double[] values;

    Anchor(float y, float x, float lastX, double[] values) {
      this.y = y;
      this.x = x;
      this.lastX = lastX;
      this.values = values;
    }
  }

  private static class Positions extends PDFTextStripper {
    List<List<Glyph>> pages = new ArrayList<>();

    Positions() throws IOException {
      setSortByPosition(true);
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
      pages.add(new ArrayList<>());
      super.startPage(page);
    }

    @Override
    protected void processTextPosition(TextPosition p) {
      pages.get(pages.size() - 1).add(new Glyph(p));
      super.processTextPosition(p);
    }
  }

  private static class Borders extends PDFGraphicsStreamEngine {
    List<float[]> edges = new ArrayList<>();
    PointF current = new PointF();
    float height;

    Borders(PDPage page) {
      super(page);
      height = page.getCropBox().getHeight() + page.getCropBox().getLowerLeftY();
    }

    void edge(float x, float y, float xx, float yy) {
      edges.add(new float[] {Math.min(x, xx), height - y, Math.max(x, xx), height - yy});
    }

    @Override
    public void appendRectangle(PointF a, PointF b, PointF c, PointF d) {
      edge(a.x, a.y, b.x, b.y);
      edge(b.x, b.y, c.x, c.y);
      edge(c.x, c.y, d.x, d.y);
      edge(d.x, d.y, a.x, a.y);
    }

    @Override
    public void moveTo(float x, float y) {
      current = new PointF(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
      edge(current.x, current.y, x, y);
      current = new PointF(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
      current = new PointF(x3, y3);
    }

    @Override
    public PointF getCurrentPoint() {
      return current;
    }

    @Override
    public void closePath() {}

    @Override
    public void endPath() {}

    @Override
    public void strokePath() {}

    @Override
    public void fillPath(android.graphics.Path.FillType rule) {}

    @Override
    public void fillAndStrokePath(android.graphics.Path.FillType rule) {}

    @Override
    public void clip(android.graphics.Path.FillType rule) {}

    @Override
    public void drawImage(PDImage image) {}

    @Override
    public void shadingFill(COSName name) {}
  }
}
