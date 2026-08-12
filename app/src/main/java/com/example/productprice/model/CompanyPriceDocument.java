package com.example.productprice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompanyPriceDocument {

    public enum DocumentType {
        ASSOCIATE,
        PREFERRED_CUSTOMER,
        UNKNOWN
    }

    private DocumentType documentType;
    private String effectiveDate;
    private String sourceName;
    private String rawText;

    private final List<CompanyPriceRow> rows;
    private final List<String> warnings;

    public CompanyPriceDocument() {
        documentType = DocumentType.UNKNOWN;
        effectiveDate = "";
        sourceName = "";
        rawText = "";
        rows = new ArrayList<>();
        warnings = new ArrayList<>();
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType == null
                ? DocumentType.UNKNOWN
                : documentType;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate == null ? "" : effectiveDate.trim();
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName == null ? "" : sourceName.trim();
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText == null ? "" : rawText;
    }

    public void addRow(CompanyPriceRow row) {
        if (row != null) {
            rows.add(row);
        }
    }

    public List<CompanyPriceRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.trim().isEmpty()) {
            warnings.add(warning.trim());
        }
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public boolean isRecognizedOfficialPriceList() {
        return documentType != DocumentType.UNKNOWN
                && !effectiveDate.isEmpty()
                && !rows.isEmpty();
    }
}
