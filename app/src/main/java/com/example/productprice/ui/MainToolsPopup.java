package com.example.productprice.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.example.productprice.AppLockSettingsActivity;
import com.example.productprice.CategoryManagerActivity;
import com.example.productprice.CustomerManagerActivity;
import com.example.productprice.MainActivity;
import com.example.productprice.PriceImportHistoryActivity;
import com.example.productprice.PriceUpdateActivity;
import com.example.productprice.ProductManagementActivity;
import com.example.productprice.ProfileActivity;
import com.example.productprice.R;
import com.example.productprice.SavedQuotationsActivity;
import com.example.productprice.data.PriceImportHistoryRepository;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.PriceImportHistoryEntry;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainToolsPopup {

    private MainToolsPopup() {
    }

    public static void show(MainActivity activity, MaterialToolbar anchor) {
        if (activity == null || anchor == null || activity.isFinishing()) {
            return;
        }

        View content = LayoutInflater.from(activity)
                .inflate(R.layout.popup_main_tools, null, false);

        int width = Math.min(
                dp(activity, 300),
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24)
        );

        PopupWindow popupWindow = new PopupWindow(
                content,
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(dp(activity, 8));
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

        bindPriceStatus(activity, content);

        bind(content, R.id.menu_products, () -> open(
                activity,
                popupWindow,
                ProductManagementActivity.class
        ));

        bind(content, R.id.menu_categories, () -> open(
                activity,
                popupWindow,
                CategoryManagerActivity.class
        ));

        bind(content, R.id.menu_price_update, () -> open(
                activity,
                popupWindow,
                PriceUpdateActivity.class
        ));

        bind(content, R.id.menu_price_history, () -> open(
                activity,
                popupWindow,
                PriceImportHistoryActivity.class
        ));

        bind(content, R.id.menu_customers, () -> open(
                activity,
                popupWindow,
                CustomerManagerActivity.class
        ));

        bind(content, R.id.menu_saved_quotations, () -> open(
                activity,
                popupWindow,
                SavedQuotationsActivity.class
        ));

        bind(content, R.id.menu_security, () -> open(
                activity,
                popupWindow,
                AppLockSettingsActivity.class
        ));

        bind(content, R.id.menu_profile, () -> open(
                activity,
                popupWindow,
                ProfileActivity.class
        ));

        popupWindow.showAsDropDown(anchor, dp(activity, 8), -dp(activity, 2));
    }

    private static void bindPriceStatus(MainActivity activity, View content) {
        TextView title = content.findViewById(R.id.text_menu_price_status_title);
        TextView detail = content.findViewById(R.id.text_menu_price_status_detail);

        if (title == null || detail == null) {
            return;
        }

        try {
            ProductDbHelper db = ProductDbHelper.getInstance(activity);
            db.initialize();

            PriceImportHistoryRepository repository =
                    new PriceImportHistoryRepository(db);

            PriceImportHistoryEntry latest =
                    repository.getLatestOfficialPdfUpdate();

            if (latest == null) {
                title.setText("No official PDF import yet");
                detail.setText("Price Update → select both official company PDFs");
                return;
            }

            title.setText(latest.getEffectiveDateLabel());

            String date = latest.getImportedAt() <= 0L
                    ? "date not recorded"
                    : new SimpleDateFormat(
                            "dd MMM yyyy",
                            Locale.getDefault()
                    ).format(new Date(latest.getImportedAt()));

            detail.setText(
                    latest.getProductCount()
                            + " products • "
                            + date
                            + (latest.isUndoAvailable()
                            ? " • Undo available"
                            : " • Applied")
            );

        } catch (Exception exception) {
            title.setText("Official price status unavailable");
            detail.setText("Open Price Update to refresh price data");
        }
    }

    private static void bind(View root, int id, Runnable action) {
        View view = root.findViewById(id);
        if (view != null) {
            view.setOnClickListener(v -> action.run());
        }
    }

    private static void open(
            MainActivity activity,
            PopupWindow popupWindow,
            Class<?> destination
    ) {
        popupWindow.dismiss();
        activity.startActivity(new Intent(activity, destination));
    }

    private static int dp(MainActivity activity, int dp) {
        return Math.round(
                dp * activity.getResources().getDisplayMetrics().density
        );
    }
}
