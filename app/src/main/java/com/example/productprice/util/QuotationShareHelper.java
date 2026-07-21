package com.example.productprice.util;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.example.productprice.model.CartItem;
import com.example.productprice.model.Customer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class QuotationShareHelper {

    private QuotationShareHelper() {
    }

    public static void createAndSharePdf(
            Context context,
            List<CartItem> cartItems,
            String orderType,
            Customer selectedCustomer
    ) throws Exception {

        if (context == null) {
            throw new IllegalArgumentException(
                    "Context is required"
            );
        }

        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException(
                    "Add at least one product before sharing"
            );
        }

        int totalQuantity = 0;
        double totalVp = 0d;
        int subtotal = 0;

        for (CartItem cartItem : cartItems) {
            totalQuantity += cartItem.getQuantity();
            totalVp += cartItem.getTotalVp();
            subtotal += cartItem.getTotalPrice();
        }

        int logistics =
                totalVp > 0 && totalVp < 100
                        ? 118
                        : 0;

        int grandTotal =
                subtotal + logistics;

        File quotationDirectory =
                new File(
                        context.getCacheDir(),
                        "quotations"
                );

        if (!quotationDirectory.exists()
                && !quotationDirectory.mkdirs()) {

            throw new IllegalStateException(
                    "Unable to create quotation folder"
            );
        }

        deleteOldSharedPdfFiles(
                quotationDirectory
        );

        String customerName =
                getQuotationName(
                        orderType,
                        selectedCustomer
                );

        String timeStamp =
                new SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.getDefault()
                ).format(new Date());

        String fileName =
                "Quotation_"
                        + customerName
                        + "_"
                        + timeStamp
                        + ".pdf";

        File pdfFile =
                new File(
                        quotationDirectory,
                        fileName
                );

        Uri pdfUri =
                FileProvider.getUriForFile(
                        context,
                        context.getPackageName()
                                + ".fileprovider",
                        pdfFile
                );

        Customer exportCustomer =
                "Customer".equalsIgnoreCase(
                        safeText(orderType)
                )
                        ? selectedCustomer
                        : null;

        ExportUtils.writeQuotePdf(
                context.getContentResolver(),
                pdfUri,
                cartItems,
                totalQuantity,
                totalVp,
                logistics,
                grandTotal,
                orderType,
                exportCustomer
        );

        Intent shareIntent =
                new Intent(
                        Intent.ACTION_SEND
                );

        shareIntent.setType(
                "application/pdf"
        );

        shareIntent.putExtra(
                Intent.EXTRA_STREAM,
                pdfUri
        );

        shareIntent.putExtra(
                Intent.EXTRA_SUBJECT,
                "Product Quotation"
        );

        shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                createShareMessage(
                        orderType,
                        selectedCustomer,
                        grandTotal
                )
        );

        shareIntent.setClipData(
                ClipData.newRawUri(
                        "Product Quotation",
                        pdfUri
                )
        );

        shareIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        Intent chooserIntent =
                Intent.createChooser(
                        shareIntent,
                        "Share PDF Quotation"
                );

        chooserIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        context.startActivity(
                chooserIntent
        );
    }

    private static String getQuotationName(
            String orderType,
            Customer customer
    ) {
        String name;

        if ("Customer".equalsIgnoreCase(
                safeText(orderType)
        ) && customer != null) {

            name = safeText(
                    customer.getName()
            );

        } else {
            name = "Self";
        }

        String safeName =
                name.replaceAll(
                                "[^a-zA-Z0-9\\-_ ]",
                                ""
                        )
                        .replaceAll(
                                "\\s+",
                                "_"
                        );

        if (safeName.isEmpty()) {
            safeName = "Quotation";
        }

        return safeName;
    }

    private static String createShareMessage(
            String orderType,
            Customer customer,
            int grandTotal
    ) {
        String orderFor;

        if ("Customer".equalsIgnoreCase(
                safeText(orderType)
        ) && customer != null) {

            orderFor =
                    safeText(
                            customer.getName()
                    );

        } else {
            orderFor = "Self";
        }

        return "Health Care Wellness Club\n"
                + "Product quotation for: "
                + orderFor
                + "\nGrand Total: ₹"
                + grandTotal;
    }

    private static void deleteOldSharedPdfFiles(
            File quotationDirectory
    ) {
        File[] files =
                quotationDirectory.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file != null
                    && file.isFile()
                    && file.getName()
                    .toLowerCase(Locale.ROOT)
                    .endsWith(".pdf")) {

                file.delete();
            }
        }
    }

    private static String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }
}