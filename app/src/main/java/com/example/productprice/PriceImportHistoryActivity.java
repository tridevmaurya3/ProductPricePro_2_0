package com.example.productprice;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.productprice.data.PriceImportHistoryRepository;
import com.example.productprice.data.ProductDbHelper;
import com.example.productprice.model.PriceImportHistoryEntry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PriceImportHistoryActivity extends AppCompatActivity {

    private PriceImportHistoryRepository historyRepository;
    private TextView summaryText;
    private LinearLayout emptyLayout;
    private LinearLayout historyContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_import_history);

        ProductDbHelper db = ProductDbHelper.getInstance(this);
        db.initialize();
        historyRepository = new PriceImportHistoryRepository(db);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_price_history);
        toolbar.setNavigationOnClickListener(view -> finish());

        summaryText = findViewById(R.id.text_price_history_summary);
        emptyLayout = findViewById(R.id.layout_price_history_empty);
        historyContainer = findViewById(R.id.container_price_history);

        findViewById(R.id.button_open_price_update_from_history)
                .setOnClickListener(
                        view -> startActivity(
                                new Intent(this, PriceUpdateActivity.class)
                        )
                );
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderHistory();
    }

    private void renderHistory() {
        List<PriceImportHistoryEntry> history =
                historyRepository.getOfficialPdfHistory();

        historyContainer.removeAllViews();

        if (history.isEmpty()) {
            summaryText.setText("No official PDF updates recorded yet");
            emptyLayout.setVisibility(View.VISIBLE);
            historyContainer.setVisibility(View.GONE);
            return;
        }

        emptyLayout.setVisibility(View.GONE);
        historyContainer.setVisibility(View.VISIBLE);

        int undoneCount = 0;
        for (PriceImportHistoryEntry entry : history) {
            if (entry != null && entry.isUndone()) {
                undoneCount++;
            }
        }

        PriceImportHistoryEntry latest = history.get(0);
        String summary = history.size()
                + " official import"
                + (history.size() == 1 ? "" : "s")
                + " • latest "
                + latest.getEffectiveDateLabel()
                + " • "
                + latest.getProductCount()
                + " products";

        if (undoneCount > 0) {
            summary += " • " + undoneCount + " undone";
        }

        summaryText.setText(summary);

        for (PriceImportHistoryEntry entry : history) {
            historyContainer.addView(createHistoryCard(entry));
        }
    }

    private View createHistoryCard(PriceImportHistoryEntry entry) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(
                Color.parseColor(
                        entry.isUndone() ? "#E7D3CF" : "#D8E5DB"
                )
        );
        card.setCardBackgroundColor(
                Color.parseColor(
                        entry.isUndone() ? "#FFF9F7" : "#FFFFFF"
                )
        );

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(8);
        card.setLayoutParams(cardParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView title = textView(
                entry.getEffectiveDateLabel(),
                15,
                entry.isUndone() ? "#7A3F33" : "#173B24",
                true
        );
        body.addView(title);

        TextView importedAt = textView(
                "Imported " + formatDateTime(entry.getImportedAt()),
                11,
                "#65716A",
                false
        );
        LinearLayout.LayoutParams importedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        importedParams.topMargin = dp(3);
        importedAt.setLayoutParams(importedParams);
        body.addView(importedAt);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        metricsParams.topMargin = dp(9);
        metrics.setLayoutParams(metricsParams);

        TextView products = textView(
                entry.getProductCount() + " products updated",
                11,
                "#315F83",
                true
        );
        products.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );
        metrics.addView(products);

        String statusLabel;
        String statusColor;

        if (entry.isUndone()) {
            statusLabel = "Undone";
            statusColor = "#A04432";
        } else if (entry.isUndoAvailable()) {
            statusLabel = "Undo available";
            statusColor = "#8A5A00";
        } else {
            statusLabel = "Applied";
            statusColor = "#177A3A";
        }

        TextView status = textView(
                statusLabel,
                11,
                statusColor,
                true
        );
        metrics.addView(status);
        body.addView(metrics);

        String noteText;
        if (entry.isUndone()) {
            noteText = "This official PDF import was later undone and the previous product prices were restored.";
        } else if (entry.isUndoAvailable()) {
            noteText = "This is the latest bulk price operation. Restore it from Price Update → Undo Latest Bulk Update.";
        } else {
            noteText = "This official price import remains in the audit timeline. A newer bulk operation exists after it.";
        }

        TextView note = textView(
                noteText,
                10,
                "#748078",
                false
        );
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        noteParams.topMargin = dp(7);
        note.setLayoutParams(noteParams);
        body.addView(note);

        card.addView(body);
        return card;
    }

    private TextView textView(
            String text,
            int textSizeSp,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(textSizeSp);
        view.setTextColor(Color.parseColor(color));
        if (bold) {
            view.setTypeface(
                    view.getTypeface(),
                    android.graphics.Typeface.BOLD
            );
        }
        return view;
    }

    private String formatDateTime(long timestamp) {
        if (timestamp <= 0L) {
            return "time not recorded";
        }

        return new SimpleDateFormat(
                "dd MMM yyyy • hh:mm a",
                Locale.getDefault()
        ).format(new Date(timestamp));
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
