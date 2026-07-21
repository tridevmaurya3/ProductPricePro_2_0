package com.example.productprice.util;

import android.content.ContentResolver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.example.productprice.model.CartItem;
import com.example.productprice.model.Product;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ExportUtils {
    private ExportUtils() {}

    public static void writeProductsXlsx(ContentResolver resolver, Uri uri, List<Product> products) throws Exception {
        List<List<Cell>> rows = new ArrayList<>();
        rows.add(header("Category", "Product Name", "VP", "Full Price", "Price@15", "Price@25", "Price@35", "Price@42", "Price@50"));
        for (Product p : products) {
            List<Cell> row = new ArrayList<>();
            row.add(Cell.text(p.getCategory()));
            row.add(Cell.text(p.getName()));
            row.add(Cell.decimal(p.getVp()));
            row.add(Cell.currency(p.getFullPrice()));
            row.add(Cell.currency(p.getPrice15()));
            row.add(Cell.currency(p.getPrice25()));
            row.add(Cell.currency(p.getPrice35()));
            row.add(Cell.currency(p.getPrice42()));
            row.add(Cell.currency(p.getPrice50()));
            rows.add(row);
        }
        writeXlsx(resolver, uri, "Product Prices", rows, new int[]{22, 42, 10, 14, 14, 14, 14, 14, 14});
    }

    public static void writeQuoteXlsx(ContentResolver resolver, Uri uri, List<CartItem> items,
                                      int totalQuantity, double totalVp, int logistics, int grandTotal) throws Exception {
        List<List<Cell>> rows = new ArrayList<>();
        rows.add(singleTitle("Health Care Wellness Club - Product Quotation", 7));
        rows.add(singleTitle("Generated: " + formattedDate(), 7));
        rows.add(header("Sr", "Product", "Qty", "Price Level", "Unit Price", "VP", "Total"));
        int sr = 1;
        for (CartItem item : items) {
            List<Cell> row = new ArrayList<>();
            row.add(Cell.number(sr++));
            row.add(Cell.text(item.getProduct().getName()));
            row.add(Cell.number(item.getQuantity()));
            row.add(Cell.text(item.getTier()));
            row.add(Cell.currency(item.getUnitPrice()));
            row.add(Cell.decimal(item.getTotalVp()));
            row.add(Cell.currency(item.getTotalPrice()));
            rows.add(row);
        }
        rows.add(totalRow("Total Products", totalQuantity, 7));
        rows.add(totalRow("Total VP", totalVp, 7));
        rows.add(totalRow("Logistics", logistics, 7));
        rows.add(totalRow("Grand Total", grandTotal, 7));
        writeXlsx(resolver, uri, "Quotation", rows, new int[]{8, 42, 9, 16, 15, 12, 16});
    }

    public static void writeProductsPdf(ContentResolver resolver, Uri uri, List<Product> products) throws Exception {
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setColor(Color.rgb(190, 205, 195));
        border.setStrokeWidth(0.8f);

        final int pageWidth = 842;
        final int pageHeight = 595;
        final int margin = 24;
        final int rowHeight = 18;
        final int[] widths = {24, 88, 210, 45, 64, 64, 64, 64, 64, 64};
        final String[] headers = {"#", "Category", "Product", "VP", "Full", "@15", "@25", "@35", "@42", "@50"};
        int rowsPerPage = 25;
        int pageNumber = 1;

        for (int start = 0; start < products.size(); start += rowsPerPage) {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
            Canvas canvas = page.getCanvas();
            paint.setColor(Color.rgb(11, 122, 75));
            paint.setTextSize(18f);
            paint.setFakeBoldText(true);
            canvas.drawText("Product Price Pro - Complete Price List", margin, 28, paint);
            paint.setColor(Color.DKGRAY);
            paint.setTextSize(9f);
            paint.setFakeBoldText(false);
            canvas.drawText("Health Care Wellness Club • " + formattedDate(), margin, 43, paint);

            int y = 56;
            int x = margin;
            paint.setTextSize(8f);
            paint.setFakeBoldText(true);
            paint.setColor(Color.WHITE);
            Paint headerFill = new Paint();
            headerFill.setColor(Color.rgb(11, 122, 75));
            for (int c = 0; c < headers.length; c++) {
                canvas.drawRect(x, y, x + widths[c], y + rowHeight, headerFill);
                canvas.drawRect(x, y, x + widths[c], y + rowHeight, border);
                canvas.drawText(headers[c], x + 3, y + 12, paint);
                x += widths[c];
            }
            y += rowHeight;

            paint.setFakeBoldText(false);
            paint.setColor(Color.rgb(30, 45, 36));
            for (int i = start; i < Math.min(start + rowsPerPage, products.size()); i++) {
                Product p = products.get(i);
                String[] values = {
                        String.valueOf(i + 1), p.getCategory(), p.getName(), formatDecimal(p.getVp()),
                        String.valueOf(p.getFullPrice()), String.valueOf(p.getPrice15()), String.valueOf(p.getPrice25()),
                        String.valueOf(p.getPrice35()), String.valueOf(p.getPrice42()), String.valueOf(p.getPrice50())
                };
                x = margin;
                for (int c = 0; c < values.length; c++) {
                    canvas.drawRect(x, y, x + widths[c], y + rowHeight, border);
                    canvas.drawText(ellipsize(values[c], paint, widths[c] - 6), x + 3, y + 12, paint);
                    x += widths[c];
                }
                y += rowHeight;
            }
            paint.setTextSize(8f);
            paint.setColor(Color.GRAY);
            canvas.drawText("Page " + pageNumber, pageWidth - 60, pageHeight - 12, paint);
            document.finishPage(page);
            pageNumber++;
        }

        if (products.isEmpty()) {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create());
            paint.setColor(Color.DKGRAY);
            paint.setTextSize(18f);
            page.getCanvas().drawText("No products available", margin, 50, paint);
            document.finishPage(page);
        }

        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Unable to open output file");
            document.writeTo(output);
        } finally {
            document.close();
        }
    }

    public static void writeQuotePdf(ContentResolver resolver, Uri uri, List<CartItem> items,
                                     int totalQuantity, double totalVp, int logistics, int grandTotal) throws Exception {
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setColor(Color.rgb(190, 205, 195));
        border.setStrokeWidth(0.8f);

        final int pageWidth = 595;
        final int pageHeight = 842;
        final int margin = 28;
        final int rowHeight = 22;
        final int[] widths = {28, 225, 36, 72, 68, 96};
        final String[] headers = {"#", "Product", "Qty", "VP", "Unit", "Total"};
        int rowsPerPage = 27;
        int pageNumber = 1;

        for (int start = 0; start < items.size(); start += rowsPerPage) {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
            Canvas canvas = page.getCanvas();
            paint.setColor(Color.rgb(11, 122, 75));
            paint.setTextSize(21f);
            paint.setFakeBoldText(true);
            canvas.drawText("Health Care Wellness Club", margin, 34, paint);
            paint.setTextSize(14f);
            canvas.drawText("Product Quotation", margin, 55, paint);
            paint.setTextSize(9f);
            paint.setColor(Color.DKGRAY);
            paint.setFakeBoldText(false);
            canvas.drawText("Generated: " + formattedDate(), margin, 72, paint);

            int y = 88;
            int x = margin;
            Paint headerFill = new Paint();
            headerFill.setColor(Color.rgb(11, 122, 75));
            paint.setColor(Color.WHITE);
            paint.setTextSize(9f);
            paint.setFakeBoldText(true);
            for (int c = 0; c < headers.length; c++) {
                canvas.drawRect(x, y, x + widths[c], y + rowHeight, headerFill);
                canvas.drawRect(x, y, x + widths[c], y + rowHeight, border);
                canvas.drawText(headers[c], x + 4, y + 15, paint);
                x += widths[c];
            }
            y += rowHeight;

            paint.setFakeBoldText(false);
            paint.setColor(Color.rgb(30, 45, 36));
            for (int i = start; i < Math.min(start + rowsPerPage, items.size()); i++) {
                CartItem item = items.get(i);
                String[] values = {
                        String.valueOf(i + 1),
                        item.getProduct().getName() + " (" + item.getTier() + ")",
                        String.valueOf(item.getQuantity()),
                        formatDecimal(item.getTotalVp()),
                        "₹" + item.getUnitPrice(),
                        "₹" + item.getTotalPrice()
                };
                x = margin;
                for (int c = 0; c < values.length; c++) {
                    canvas.drawRect(x, y, x + widths[c], y + rowHeight, border);
                    canvas.drawText(ellipsize(values[c], paint, widths[c] - 8), x + 4, y + 15, paint);
                    x += widths[c];
                }
                y += rowHeight;
            }

            if (start + rowsPerPage >= items.size()) {
                y += 14;
                paint.setColor(Color.rgb(11, 122, 75));
                paint.setTextSize(11f);
                paint.setFakeBoldText(true);
                canvas.drawText("Products: " + totalQuantity, margin, y, paint);
                canvas.drawText("Total VP: " + formatDecimal(totalVp), margin, y + 20, paint);
                canvas.drawText("Logistics: ₹" + logistics, margin, y + 40, paint);
                paint.setTextSize(16f);
                canvas.drawText("Grand Total: ₹" + grandTotal, margin, y + 68, paint);
            }

            paint.setTextSize(8f);
            paint.setFakeBoldText(false);
            paint.setColor(Color.GRAY);
            canvas.drawText("Page " + pageNumber, pageWidth - 60, pageHeight - 14, paint);
            document.finishPage(page);
            pageNumber++;
        }

        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Unable to open output file");
            document.writeTo(output);
        } finally {
            document.close();
        }
    }

    private static void writeXlsx(ContentResolver resolver, Uri uri, String sheetName,
                                  List<List<Cell>> rows, int[] widths) throws Exception {
        try (OutputStream raw = resolver.openOutputStream(uri)) {
            if (raw == null) throw new IllegalStateException("Unable to open output file");
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                put(zip, "[Content_Types].xml", contentTypes());
                put(zip, "_rels/.rels", rootRelationships());
                put(zip, "xl/workbook.xml", workbookXml(sheetName));
                put(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
                put(zip, "xl/styles.xml", stylesXml());
                put(zip, "xl/worksheets/sheet1.xml", sheetXml(rows, widths));
            }
        }
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>";
    }

    private static String rootRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>";
    }

    private static String workbookXml(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"" + xml(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private static String workbookRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>";
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"₹#,##0\"/></numFmts>" +
                "<fonts count=\"3\"><font><sz val=\"10\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"10\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><color rgb=\"FF075E3A\"/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0B7A4B\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
                "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDDF6E9\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
                "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>" +
                "<border><left style=\"thin\"><color rgb=\"FFD1DDD4\"/></left><right style=\"thin\"><color rgb=\"FFD1DDD4\"/></right>" +
                "<top style=\"thin\"><color rgb=\"FFD1DDD4\"/></top><bottom style=\"thin\"><color rgb=\"FFD1DDD4\"/></bottom><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"5\">" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyBorder=\"1\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>" +
                "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyNumberFormat=\"1\" applyBorder=\"1\"/>" +
                "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"1\" applyNumberFormat=\"1\" applyBorder=\"1\"/>" +
                "<xf numFmtId=\"164\" fontId=\"2\" fillId=\"3\" borderId=\"1\" applyFont=\"1\" applyFill=\"1\" applyNumberFormat=\"1\" applyBorder=\"1\"/>" +
                "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
    }

    private static String sheetXml(List<List<Cell>> rows, int[] widths) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        xml.append("<cols>");
        for (int i = 0; i < widths.length; i++) {
            xml.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            xml.append("<row r=\"").append(r + 1).append("\">");
            List<Cell> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                Cell cell = row.get(c);
                String ref = columnName(c + 1) + (r + 1);
                if (cell.number != null) {
                    xml.append("<c r=\"").append(ref).append("\" s=\"").append(cell.style).append("\"><v>")
                            .append(cell.number).append("</v></c>");
                } else {
                    xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(cell.style)
                            .append("\"><is><t xml:space=\"preserve\">").append(xml(cell.text)).append("</t></is></c>");
                }
            }
            xml.append("</row>");
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static List<Cell> header(String... values) {
        List<Cell> row = new ArrayList<>();
        for (String value : values) row.add(Cell.header(value));
        return row;
    }

    private static List<Cell> singleTitle(String value, int columns) {
        List<Cell> row = new ArrayList<>();
        row.add(Cell.header(value));
        for (int i = 1; i < columns; i++) row.add(Cell.header(""));
        return row;
    }

    private static List<Cell> totalRow(String label, double value, int columns) {
        List<Cell> row = new ArrayList<>();
        row.add(Cell.text(label));
        for (int i = 1; i < columns - 1; i++) row.add(Cell.text(""));
        row.add(Cell.total(value));
        return row;
    }

    private static String columnName(int column) {
        StringBuilder result = new StringBuilder();
        while (column > 0) {
            int remainder = (column - 1) % 26;
            result.insert(0, (char) ('A' + remainder));
            column = (column - 1) / 26;
        }
        return result.toString();
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String ellipsize(String value, Paint paint, float maxWidth) {
        if (value == null) return "";
        if (paint.measureText(value) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0 && paint.measureText(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, Math.max(0, end)) + suffix;
    }

    private static String formattedDate() {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private static class Cell {
        final String text;
        final Double number;
        final int style;

        private Cell(String text, Double number, int style) {
            this.text = text;
            this.number = number;
            this.style = style;
        }

        static Cell text(String text) { return new Cell(text, null, 0); }
        static Cell header(String text) { return new Cell(text, null, 1); }
        static Cell currency(double value) { return new Cell(null, value, 2); }
        static Cell decimal(double value) { return new Cell(null, value, 3); }
        static Cell number(double value) { return new Cell(null, value, 0); }
        static Cell total(double value) { return new Cell(null, value, 4); }
    }
}
