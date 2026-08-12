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

        PriceImportHistoryEntry latest = history.get(0);
        summaryText.setText(
                history.size()
                        + " official import"
                        + (history.size() == 1 ? "" : "s")
                        + " • latest "
                        + latest.getEffectiveDateLabel()
                        + " • "
                        + latest.getProductCount()
                        + " products"
        );

        for (int index = 0; index < history.size(); index++) {
            historyContainer.addView(
                    createHistoryCard(history.get(index), index + 1)
            );
        }
    }

    private View createHistoryCard(
            PriceImportHistoryEntry entry,
            int position
    ) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.parseColor("#D8E5DB"));
        card.setCardBackgroundColor(Color.parseColor("#FFFFFF"));

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
                "#173B24",
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

        TextView status = textView(
                entry.isUndoAvailable() ? "Undo available" : "Applied",
                11,
                entry.isUndoAvailable() ? "#8A5A00" : "#177A3A",
                true
        );
        metrics.addView(status);
        body.addView(metrics);

        TextView note = textView(
                entry.isUndoAvailable()
                        ? "This is the latest bulk price operation. It can be restored from Price Update → Undo Latest Bulk Update."
                        : "Older official import snapshot. Newer bulk operations exist after this update.",
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
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
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
