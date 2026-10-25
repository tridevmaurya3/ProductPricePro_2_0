# Product Price Pro 3.0

Herbalife India के official catalogue, ग्राहक और quotations के लिए offline Android app.

## अगली कंपनी PDF से automatic update

1. Tools → **Official PDF Catalog** खोलें (Price Update का PDF button भी यही screen खोलता है).
2. Associate और Preferred Customer की समान effective date वाली दोनों original PDF चुनें.
3. हर Stock No., पूरा नाम, flavour/pack, category, VP और सभी six prices का review देखें.
4. **Apply Complete Catalog** करें. नई categories और नए products अपने आप बनेंगे; existing official products Stock No. से update होंगे. पुराने PDF में रहे पर नए में न रहे official products archive होते हैं. ऐप की पहचानी हुई पुरानी bundled group entries archive होती हैं; दूसरे custom products नहीं मिटते. पुराने बिना Stock No. वाले exact-name records को official identity अपनाई जा सकती है.
5. वापस जाने के लिए इसी screen पर **Restore catalog before last PDF import** है. यह अंतिम import से पहले के products, categories और price history को restore करता है. बाद के catalogue edits भी उसी snapshot पर लौटेंगे. Customers, saved quotations, profile और App Lock अलग रहते हैं.

Parser page coordinates, price columns और table borders पढ़ता है. Shared cells के flavour names अलग रहते हैं. Font ligatures तथा अलग-अलग छपे stock/number fragments सँभाले जाते हैं. Category names का product whitelist नहीं है; उसी table format की नई category और नया Stock No. स्वतः आते हैं. दोनों PDFs की common MRP/VP/Silver/Gold values मिलाकर shared price blocks पूरे होते हैं; ambiguous या conflicting values पर import रुकता है, कोई product चुपचाप skip करके partial catalogue apply नहीं होता. Scanned/image-only, rotated या बदले layout की PDF को review/error के साथ रोकना संभव है; OCR या network service नहीं जोड़ी गई.

## Exact official prices

Full Price = MRP; Bronze = Preferred price; Silver/@25, Gold/@35, @42 और @50 official table values हैं. ये MRP पर सीधे arithmetic discount नहीं हैं. Generic scale/recalculate तथा CSV overwrite official products को skip करते हैं. Manual product editing उपलब्ध है. नया install official PDF import screen से शुरू होता है; पुरानी bundled price list अपने आप लागू नहीं होती. Existing install पर भी current company PDFs import करें. मूल कंपनी PDFs repository में प्रकाशित नहीं की जातीं.

## Delivery और quotation

Order summary में delivery amount पर tap करें: Pickup / Delivery, tax सहित below-100-VP charge, और ink-saving PDF चुन सकते हैं. Default inclusive charge Rs.118 है; Pickup और कम-से-कम 100 VP पर शून्य. Saved quotations अपना delivery mode और charge रखते हैं. Reopen, duplicate और share पर उसी saved data की कीमतें इस्तेमाल होती हैं.

PDF में profile organization/contact, brand mark, reference, customer/address, पूरे product names, selected tier, quantities, VP, subtotal, delivery और total आते हैं. Long names wrap होते हैं; multiple pages पर heading दोहरती है. Sharing menu में **Preview PDF**, **Print / Save as PDF**, **Share quotation** हैं. Native print preview और share एक ही बनी PDF file इस्तेमाल करते हैं. Quotation की validity 7 दिन प्रदर्शित होती है. सामान्य और black/white rendering उपलब्ध है.

## Development / verification

- Java 17, Android SDK 34, Gradle wrapper 8.4.
- `./gradlew assembleDebug testDebugUnitTest lintDebug`
- Regression tests synthetic future category/shared-cell PDFs runtime पर बनाते हैं. असली April 2026 PDFs का स्थानीय integration test चलाने के लिए `./gradlew testDebugUnitTest -PofficialFixtureDir=/path/to/pdfs` चलाएँ; उस folder का path दें जिसमें `Associate.pdf` और `Prefered Customer.pdf` हैं. मूल PDFs repository में शामिल नहीं हैं.
- Tests: 57 unique identities / 15 categories, exact group prices and ligatures; future category import; conflict blocking; stable IDs, custom preservation and catalogue restore; shipping/date boundaries; long-name multi-page quotation rendering.
- Robolectric 4.12 में PdfDocument native serialization उपलब्ध नहीं है, इसलिए PDF visual test native Skia Canvas को page images में render कर real QA PDF बनाता है. Production में Android PdfDocument ही PDF लिखता है. Physical phone installation/printing अलग device validation है.
- पुराने app पर update install के लिए वही signing key रखें. Package/application ID `com.example.productprice` बदला नहीं है. कोई paid service, cloud login या app network connection नहीं जोड़ा गया है.
