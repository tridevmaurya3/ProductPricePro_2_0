package com.example.productprice;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import com.example.productprice.data.*;
import com.example.productprice.model.*;
import com.example.productprice.util.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class OfficialCatalogRegressionTest {
  private Context context;

  private CompanyPriceDocument read(String name) throws Exception {
    File file = new File(context.getCacheDir(), name + ".pdf");
    if (name.startsWith("future-")) {
      boolean associate = name.endsWith("associate");
      try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
        org.apache.pdfbox.pdmodel.PDPage page =
            new org.apache.pdfbox.pdmodel.PDPage(
                new org.apache.pdfbox.pdmodel.common.PDRectangle(600, 842));
        doc.addPage(page);
        try (org.apache.pdfbox.pdmodel.PDPageContentStream c =
            new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
          drawText(c, 40, 800, associate ? "ASSOCIATE PRICE LIST" : "PREFERRED CUSTOMERS");
          drawText(c, 40, 770, "Effective Date: 1 May 2027");
          drawText(c, 200, 700, "METABOLIC WELLNESS");
          drawText(c, 40, 665, "Z900");
          drawText(c, 80, 665, "Future Nutrition - Citrus - 350 g");
          drawText(c, 40, 640, "Z901");
          drawText(c, 80, 640, "Future Nutrition - Berry - 350 g");
          double[] values =
              associate
                  ? new double[] {1, 17.5, 2000, 1900, 1700, 1600, 1400, 1200, 1000}
                  : new double[] {1, 17.5, 2000, 1800, 1600, 1400};
          for (int i = 0; i < values.length; i++)
            drawText(c, 230 + i * 38, 652, String.valueOf(values[i]));
          c.moveTo(35, 675);
          c.lineTo(585, 675);
          c.moveTo(35, 630);
          c.lineTo(585, 630);
          c.stroke();
        }
        doc.save(file);
      }
    } else {
      String dir = System.getProperty("official.fixture.dir");
      org.junit.Assume.assumeNotNull(dir);
      File source =
          new File(dir, name.equals("associate") ? "Associate.pdf" : "Prefered Customer.pdf");
      org.junit.Assume.assumeTrue("Local official PDF fixture not supplied", source.isFile());
      try (InputStream in = new FileInputStream(source);
          OutputStream out = new FileOutputStream(file)) {
        byte[] bytes = new byte[8192];
        int n;
        while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
      }
    }
    return CompanyPricePdfParser.parse(context, Uri.fromFile(file), name);
  }

  private void drawText(
      org.apache.pdfbox.pdmodel.PDPageContentStream c, float x, float y, String text)
      throws IOException {
    c.beginText();
    c.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 8);
    c.newLineAtOffset(x, y);
    c.showText(text);
    c.endText();
  }

  private OfficialCatalogBuildResult catalog() throws Exception {
    boolean genuine = System.getProperty("official.fixture.dir") != null;
    CompanyPriceDocument a = read(genuine ? "associate" : "future-associate"),
        p = read(genuine ? "preferred" : "future-preferred");
    assertEquals(genuine ? 57 : 2, a.getDetectedIdentityCount());
    assertEquals(a.getDetectedIdentityCount(), p.getDetectedIdentityCount());
    assertEquals(a.getEffectiveDate(), p.getEffectiveDate());
    OfficialCatalogBuildResult r = OfficialFullCatalogBuilder.build(a, p, a.getEffectiveDate());
    assertTrue(r.getErrors().toString(), r.isReadyToReplace());
    assertEquals(genuine ? 57 : 2, r.getProductCount());
    assertEquals(genuine ? 15 : 1, r.getCategoryCount());
    return r;
  }

  @Before
  public void before() throws Exception {
    context = RuntimeEnvironment.getApplication();
    java.lang.reflect.Field field = ProductDbHelper.class.getDeclaredField("instance");
    field.setAccessible(true);
    ProductDbHelper old = (ProductDbHelper) field.get(null);
    if (old != null) old.close();
    field.set(null, null);
    context.deleteDatabase("product_price_pro.db");
  }

  @Test
  public void genuinePdfsHaveEveryStockAndExactSharedPrices() throws Exception {
    org.junit.Assume.assumeNotNull(System.getProperty("official.fixture.dir"));
    CompanyPriceDocument a = read("associate"), p = read("preferred");
    OfficialCatalogBuildResult result =
        OfficialFullCatalogBuilder.build(a, p, a.getEffectiveDate());
    assertTrue(result.getErrors().toString(), result.isReadyToReplace());
    assertEquals(57, result.getProductCount());
    assertEquals(15, result.getCategoryCount());
    Map<String, CompanyPriceRow> ar = new HashMap<>(), pr = new HashMap<>();
    for (CompanyPriceRow row : a.getRows()) ar.put(row.getStockNo(), row);
    for (CompanyPriceRow row : p.getRows()) pr.put(row.getStockNo(), row);
    Set<String> ids = new HashSet<>();
    for (Product product : result.getProducts()) {
      assertTrue(ids.add(product.getStockNo()));
      assertFalse(product.getName().isEmpty());
      assertFalse(product.getCategory().isEmpty());
      CompanyPriceRow source = ar.get(product.getStockNo()), bronze = pr.get(product.getStockNo());
      assertEquals(source.getMrp(), product.getFullPrice());
      assertEquals(source.getVolumePoint(), product.getVp(), .001);
      assertEquals(bronze.getPrice15().intValue(), product.getPrice15());
      assertEquals(source.getPrice25().intValue(), product.getPrice25());
      assertEquals(source.getPrice35().intValue(), product.getPrice35());
      assertEquals(source.getPrice42().intValue(), product.getPrice42());
      assertEquals(source.getPrice50().intValue(), product.getPrice50());
    }
  }

  @Test
  public void syncIsIdempotentPreservesCustomAndRestoresSnapshot() throws Exception {
    ProductDbHelper helper = ProductDbHelper.getInstance(context);
    SQLiteDatabase db = helper.getWritableDatabase();
    db.delete("products", null, null);
    db.delete("categories", null, null);
    db.delete("price_history", null, null);
    Product custom = new Product();
    custom.setName("My custom bundle");
    custom.setCategory("CUSTOM");
    custom.setFullPrice(999);
    helper.saveProduct(custom);
    OfficialCatalogReplaceRepository repo = new OfficialCatalogReplaceRepository(context, helper);
    List<Product> products = catalog().getProducts();
    repo.replaceWithOfficialCatalog(products, "15 April 2026");
    long id = helper.getProductByStock(products.get(0).getStockNo()).getId();
    assertEquals(products.size() + 1, helper.getAllProducts(false).size());
    repo.replaceWithOfficialCatalog(products, "15 April 2026");
    assertEquals(id, helper.getProductByStock(products.get(0).getStockNo()).getId());
    assertEquals(products.size() + 1, helper.getAllProducts(false).size());
    assertNotNull(helper.getProduct(custom.getId()));
    assertTrue(repo.hasBackup());
    assertTrue(repo.restoreLastCatalog());
    assertEquals(id, helper.getProductByStock(products.get(0).getStockNo()).getId());
    assertFalse(repo.hasBackup());
    assertEquals(1, helper.recalculateDiscounts("All Categories", 15, 25, 35, 42, 50, 1));
    assertEquals(
        products.get(0).getPrice50(),
        helper.getProductByStock(products.get(0).getStockNo()).getPrice50());
  }

  @Test
  public void missingAndConflictingRowsCannotApply() throws Exception {
    CompanyPriceDocument a = read("future-associate"), p = read("future-preferred");
    p.getRows().get(0).setMrp(9999);
    assertFalse(OfficialFullCatalogBuilder.build(a, p, a.getEffectiveDate()).isReadyToReplace());
    assertFalse(
        OfficialFullCatalogBuilder.build(a, new CompanyPriceDocument(), a.getEffectiveDate())
            .isReadyToReplace());
  }

  @Test
  public void futureUnknownProductAndCategoryNeedNoMapping() throws Exception {
    CompanyPriceDocument a = read("future-associate"), p = read("future-preferred");
    OfficialCatalogBuildResult result =
        OfficialFullCatalogBuilder.build(a, p, a.getEffectiveDate());
    assertTrue(result.getErrors().toString(), result.isReadyToReplace());
    assertEquals(2, result.getProductCount());
    assertEquals("Z900", result.getProducts().get(0).getStockNo());
    assertEquals("Z901", result.getProducts().get(1).getStockNo());
    assertEquals("METABOLIC WELLNESS", result.getProducts().get(0).getCategory());
    assertEquals(1800, result.getProducts().get(1).getPrice15());
  }

  @Test
  public void databaseUpgradePreservesOldPrices() throws Exception {
    File file = context.getDatabasePath("product_price_pro.db");
    file.getParentFile().mkdirs();
    try (SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null)) {
      old.execSQL(
          "CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL,"
              + " name TEXT UNIQUE NOT NULL, vp REAL DEFAULT 0, full_price INTEGER DEFAULT 0,"
              + " price15 INTEGER DEFAULT 0, price25 INTEGER DEFAULT 0, price35 INTEGER DEFAULT 0,"
              + " price42 INTEGER DEFAULT 0, price50 INTEGER DEFAULT 0, active INTEGER DEFAULT 1,"
              + " updated_at INTEGER DEFAULT 0)");
      old.execSQL(
          "CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE,active"
              + " INTEGER DEFAULT 1,created_at INTEGER DEFAULT 0,updated_at INTEGER DEFAULT 0)");
      old.execSQL(
          "CREATE TABLE price_history (id INTEGER PRIMARY KEY AUTOINCREMENT,operation_id"
              + " TEXT,product_id INTEGER,full_price INTEGER,price15 INTEGER,price25"
              + " INTEGER,price35 INTEGER,price42 INTEGER,price50 INTEGER,action TEXT,changed_at"
              + " INTEGER)");
      old.execSQL(
          "INSERT INTO products(category,name,vp,full_price,price25) VALUES('Legacy','Old"
              + " product',7.8,900,700)");
      old.setVersion(2);
    }
    ProductDbHelper helper = ProductDbHelper.getInstance(context);
    Product old = helper.getAllProducts(false).get(0);
    assertEquals("Old product", old.getName());
    assertEquals(700, old.getPrice25());
    assertEquals("", old.getStockNo());
    assertEquals(3, helper.getReadableDatabase().getVersion());
  }

  @Test
  public void savedPickupRetainsItsDeliverySettings() throws Exception {
    QuotationDbHelper helper = QuotationDbHelper.getInstance(context);
    Product p = new Product();
    p.setName("Saved item");
    p.setCategory("TEST");
    p.setVp(17.5);
    p.setPrice25(1600);
    SavedQuotation quote = new SavedQuotation("Pickup quote", "Self");
    quote.setPickup(true);
    quote.setDeliveryCharge(150);
    List<SavedQuotationItem> items =
        Collections.singletonList(
            SavedQuotationItem.fromCartItem(0, new CartItem(p, 1, "Price@25"), 0));
    long id = helper.saveQuotation(quote, items);
    SavedQuotation read = helper.getQuotation(id);
    assertTrue(read.isPickup());
    assertEquals(0, read.getLogistics());
    assertEquals(1600, read.getGrandTotal());
    assertEquals(150, read.getDeliveryCharge());
    read.setPickup(false);
    helper.saveQuotation(read, items);
    assertEquals(1750, helper.getQuotation(id).getGrandTotal());
  }

  @Test
  public void incompleteSyncRollsBackProductsAndBackup() throws Exception {
    ProductDbHelper helper = ProductDbHelper.getInstance(context);
    OfficialCatalogReplaceRepository repo = new OfficialCatalogReplaceRepository(context, helper);
    List<Product> products = catalog().getProducts();
    repo.replaceWithOfficialCatalog(products, "15 April 2026");
    int count = helper.getAllProducts(false).size();
    long original = helper.getProductByStock(products.get(0).getStockNo()).getId();
    Product bad = new Product();
    bad.setStockNo("FAIL1");
    bad.setName("Collision");
    bad.setCategory("NEW");
    bad.setFullPrice(10);
    Product custom = new Product();
    custom.setName("Collision");
    custom.setCategory("CUSTOM");
    helper.saveProduct(custom);
    Product custom2 = new Product();
    custom2.setName("Collision [FAIL1]");
    custom2.setCategory("CUSTOM");
    helper.saveProduct(custom2);
    // A real conflicting identity cannot partially archive/update existing official items.
    Product officialCollision = new Product();
    officialCollision.setStockNo("OTHER1");
    officialCollision.setName("Collision");
    officialCollision.setCategory("CUSTOM");
    android.content.ContentValues v = new android.content.ContentValues();
    v.put("stock_no", "OTHER1");
    helper
        .getWritableDatabase()
        .update("products", v, "id=?", new String[] {String.valueOf(custom.getId())});
    try {
      repo.replaceWithOfficialCatalog(Collections.singletonList(bad), "1 May 2027");
      fail("Expected a unique-name failure");
    } catch (android.database.sqlite.SQLiteConstraintException expected) {
    }
    assertEquals(count + 2, helper.getAllProducts(false).size());
    assertEquals(original, helper.getProductByStock(products.get(0).getStockNo()).getId());
    assertTrue(helper.getProductByStock(products.get(0).getStockNo()).isActive());
    assertTrue(repo.hasBackup());
  }

  @Test
  public void shippingBoundariesAndDates() {
    assertEquals(0, ShippingPolicy.calculate(0, false, 118));
    assertEquals(118, ShippingPolicy.calculate(99.99, false, 118));
    assertEquals(0, ShippingPolicy.calculate(100, false, 118));
    assertEquals(0, ShippingPolicy.calculate(21.75, true, 118));
    assertEquals(150, ShippingPolicy.calculate(21.75, false, 150));
    assertTrue(CatalogDates.isOlder("15th April 2026", "1 May 2026"));
    assertFalse(CatalogDates.isOlder("1 May 2026", "15 April 2026"));
  }

  @Test
  @Config(shadows = RasterPdfShadow.class)
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  public void quotationPdfPaginatesLongNames() throws Exception {
    List<CartItem> items = new ArrayList<>();
    for (Product p : catalog().getProducts()) items.add(new CartItem(p, 1, "Price@25"));
    Product longName = new Product();
    longName.setName(String.join(" ", Collections.nCopies(90, "Long product description")));
    longName.setCategory("NEW CATEGORY");
    longName.setPrice25(250);
    items.add(new CartItem(longName, 2, "Price@25"));
    Customer customer = new Customer();
    customer.setName("Sample Customer");
    customer.setMobile("0000000000");
    customer.setAddress("Sample address for quotation layout verification");
    int qty = items.stream().mapToInt(CartItem::getQuantity).sum();
    double vp = items.stream().mapToDouble(CartItem::getTotalVp).sum();
    int sum = items.stream().mapToInt(CartItem::getTotalPrice).sum();
    File out = new File(context.getCacheDir(), "quotation-layout.pdf");
    BrandedQuotationPdf.write(
        context.getContentResolver(),
        Uri.fromFile(out),
        items,
        qty,
        vp,
        0,
        sum,
        "Customer",
        customer);
    assertTrue(out.length() > 1000);
    if (System.getProperty("quotation.qa.output") != null) {
      java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
      File sample = new File("/tmp/quotation-sample.pdf");
      List<CartItem> selected = items.subList(0, Math.min(3, items.size()));
      BrandedQuotationPdf.write(
          context.getContentResolver(),
          Uri.fromFile(sample),
          selected,
          selected.size(),
          selected.stream().mapToDouble(CartItem::getTotalVp).sum(),
          118,
          selected.stream().mapToInt(CartItem::getTotalPrice).sum() + 118,
          "Customer",
          customer);
      assertTrue(sample.length() > 1000);
    }
    String target = System.getProperty("quotation.qa.output");
    if (target != null)
      try (InputStream in = new FileInputStream(out);
          OutputStream os = new FileOutputStream(target)) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) os.write(b, 0, n);
      }
  }
}
