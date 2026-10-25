package com.example.productprice.util;

import android.content.Context;
import android.os.*;
import android.print.*;
import java.io.*;

/** Native print preview reads exactly the PDF bytes used for sharing. */
public final class QuotationPrintHelper {
  private QuotationPrintHelper() {}

  public static void print(Context context, File file) {
    PrintManager manager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
    manager.print(
        "Product Quotation",
        new PrintDocumentAdapter() {
          @Override
          public void onLayout(
              PrintAttributes oldAttrs,
              PrintAttributes newAttrs,
              CancellationSignal signal,
              LayoutResultCallback callback,
              Bundle extras) {
            if (signal.isCanceled()) {
              callback.onLayoutCancelled();
              return;
            }
            callback.onLayoutFinished(
                new PrintDocumentInfo.Builder(file.getName())
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build(),
                true);
          }

          @Override
          public void onWrite(
              PageRange[] pages,
              ParcelFileDescriptor destination,
              CancellationSignal signal,
              WriteResultCallback callback) {
            try (InputStream in = new FileInputStream(file);
                OutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
              byte[] buf = new byte[8192];
              int n;
              while ((n = in.read(buf)) != -1) {
                if (signal.isCanceled()) {
                  callback.onWriteCancelled();
                  return;
                }
                out.write(buf, 0, n);
              }
              callback.onWriteFinished(new PageRange[] {PageRange.ALL_PAGES});
            } catch (Exception e) {
              callback.onWriteFailed(e.getMessage());
            }
          }
        },
        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
  }
}
