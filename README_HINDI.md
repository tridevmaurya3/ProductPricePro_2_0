# Product Price Pro 2.0

यह पुराने Product Price ऐप का नया database-based version है। Package name `com.example.productprice` रखा गया है और versionCode `2` है। उसी keystore से sign करने पर यह पुराने app के ऊपर update के रूप में install किया जा सकता है।

## मुख्य बदलाव

- Modern Material 3 green wellness design
- नया attractive adaptive ₹ price-tag icon
- SQLite database में Product, Category, VP और सभी prices
- App के अंदर Add, Edit, Delete और Active/Inactive
- Searchable Product Manager
- Category-wise और All Products bulk price update
- Percentage increase/decrease
- Full Price से discount prices recalculate करना
- Nearest ₹1, ₹5 या ₹10 rounding
- Latest bulk update का Undo
- CSV import से existing products update और new products add
- असली `.xlsx` Excel export, किसी भारी third-party library के बिना
- Product catalog PDF export
- Current order/quotation Excel और PDF export
- 100 VP से कम होने पर ₹118 logistics calculation
- CSV header को product मानने वाली पुरानी समस्या ठीक
- पुराने duplicate total calculation logic को हटाया गया

## पहली बार ऐप कैसे काम करेगा

पहली बार launch होने पर `app/src/main/res/raw/p_price.csv` के products database में import होते हैं। इसके बाद prices बदलने के लिए CSV या app code edit करने की जरूरत नहीं है। Product Manager और Bulk Price Update screen से सभी बदलाव किए जा सकते हैं।

## CSV import format

CSV के columns इसी क्रम में रखें:

1. Product Category
2. Product Name
3. Volume Point (VP)
4. Full Price
5. Price@15
6. Price@25
7. Price@35
8. Price@42
9. Price@50

Project root में `CSV_IMPORT_TEMPLATE.csv` दिया गया है। Product Name existing name से match होने पर record update होगा, अन्यथा नया product add होगा।

## Android Studio में चलाने के steps

1. Android Studio खोलें।
2. `Open` चुनकर इस `ProductPrice` folder को open करें।
3. Gradle Sync पूरा होने दें।
4. जरूरत होने पर `local.properties` Android Studio स्वयं बनाएगा।
5. Run button से फोन/emulator पर test करें।
6. APK बनाने के लिए `Build > Build Bundle(s) / APK(s) > Build APK(s)` चुनें।
7. पुराने app के ऊपर update के लिए वही पुरानी `.jks`/`.keystore` signing key इस्तेमाल करें।

## Price update के दो तरीके

### Manual Company Price
Product Manager में किसी product को edit करके Full Price और Price@15 से Price@50 तक exact company prices भरें। यह तब सही है जब company price exact mathematical discount से अलग हो।

### Formula Based Discount
Bulk Price Update में discount percentages डालकर Full Price से सभी tier prices calculate करें। Apply से पहले ध्यान रखें कि manual prices replace हो जाएँगी। Latest bulk update को Undo किया जा सकता है।

## Export

- Product Manager toolbar से पूरी price list `.xlsx` या `.pdf` में export करें।
- Main screen पर order बनाकर quotation `.xlsx` या `.pdf` में export करें।
- Files Android Storage Access Framework से user की चुनी हुई location में save होती हैं; storage permission की जरूरत नहीं है।

## आगे जोड़े जा सकने वाले features

- Google Sheet / Firebase / Supabase cloud price sync
- Admin PIN या biometric lock for price editing
- Customer and Associate discount profile
- Stock management और low-stock alert
- Order history और monthly VP report
- Backup/Restore to Google Drive
- WhatsApp share button और branded quotation
- Barcode/QR scanner
- Hindi/English language switch
- Team login और multi-device synchronization
