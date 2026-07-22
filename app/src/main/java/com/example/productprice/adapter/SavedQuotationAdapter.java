package com.example.productprice.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.productprice.R;
import com.example.productprice.model.SavedQuotation;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SavedQuotationAdapter extends
        RecyclerView.Adapter<SavedQuotationAdapter.SavedQuotationViewHolder> {

    public interface OnQuotationActionListener {

        void onOpenQuotation(
                SavedQuotation quotation
        );

        void onShareQuotation(
                SavedQuotation quotation
        );

        void onRenameQuotation(
                SavedQuotation quotation
        );

        void onDuplicateQuotation(
                SavedQuotation quotation
        );

        void onDeleteQuotation(
                SavedQuotation quotation
        );

        void onChangeQuotationStatus(
                SavedQuotation quotation,
                String newStatus
        );
    }

    private final List<SavedQuotation> quotationList;
    private final OnQuotationActionListener actionListener;

    private final NumberFormat indianNumberFormat;
    private final SimpleDateFormat dateFormat;

    public SavedQuotationAdapter(
            List<SavedQuotation> quotationList,
            OnQuotationActionListener actionListener
    ) {
        this.quotationList =
                quotationList == null
                        ? new ArrayList<>()
                        : quotationList;

        this.actionListener =
                actionListener;

        indianNumberFormat =
                NumberFormat.getNumberInstance(
                        new Locale("en", "IN")
                );

        indianNumberFormat.setMinimumFractionDigits(0);
        indianNumberFormat.setMaximumFractionDigits(0);

        dateFormat =
                new SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a",
                        Locale.getDefault()
                );
    }

    @NonNull
    @Override
    public SavedQuotationViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View itemView =
                LayoutInflater.from(
                        parent.getContext()
                ).inflate(
                        R.layout.item_saved_quotation,
                        parent,
                        false
                );

        return new SavedQuotationViewHolder(
                itemView
        );
    }

    @Override
    public void onBindViewHolder(
            @NonNull SavedQuotationViewHolder holder,
            int position
    ) {
        SavedQuotation quotation =
                quotationList.get(position);

        holder.bind(
                quotation,
                actionListener,
                this
        );
    }

    @Override
    public int getItemCount() {
        return quotationList.size();
    }

    public void replaceItems(
            List<SavedQuotation> updatedList
    ) {
        quotationList.clear();

        if (updatedList != null) {
            quotationList.addAll(
                    updatedList
            );
        }

        notifyDataSetChanged();
    }

    public SavedQuotation getItem(
            int position
    ) {
        if (position < 0
                || position >= quotationList.size()) {

            return null;
        }

        return quotationList.get(position);
    }

    private String formatRupees(
            int amount
    ) {
        return "₹"
                + indianNumberFormat.format(
                Math.max(amount, 0)
        );
    }

    private String formatVp(
            double totalVp
    ) {
        return String.format(
                Locale.getDefault(),
                "%.2f",
                Math.max(totalVp, 0d)
        );
    }

    private String formatSavedDate(
            long updatedAt,
            long createdAt
    ) {
        long dateValue =
                updatedAt > 0
                        ? updatedAt
                        : createdAt;

        if (dateValue <= 0) {
            return "Saved date unavailable";
        }

        return "Saved on "
                + dateFormat.format(
                new Date(dateValue)
        );
    }

    private String getOrderForText(
            SavedQuotation quotation
    ) {
        if (quotation == null) {
            return "Order For: Self";
        }

        String orderType =
                quotation.getOrderType();

        boolean customerOrder =
                orderType != null
                        && orderType.equalsIgnoreCase(
                        SavedQuotation.ORDER_TYPE_CUSTOMER
                );

        if (!customerOrder) {
            return "Order For: Self";
        }

        String customerName =
                quotation.getCustomerName();

        if (TextUtils.isEmpty(
                customerName
        )) {
            return "Order For: Customer";
        }

        return "Order For: "
                + customerName.trim();
    }

    private String normalizeStatus(
            String status
    ) {
        if (status == null) {
            return SavedQuotation.STATUS_DRAFT;
        }

        String cleanStatus =
                status.trim();

        if (cleanStatus.equalsIgnoreCase(
                SavedQuotation.STATUS_FINAL
        )) {
            return SavedQuotation.STATUS_FINAL;
        }

        if (cleanStatus.equalsIgnoreCase(
                SavedQuotation.STATUS_SENT
        )) {
            return SavedQuotation.STATUS_SENT;
        }

        return SavedQuotation.STATUS_DRAFT;
    }

    static class SavedQuotationViewHolder
            extends RecyclerView.ViewHolder {

        private final MaterialCardView quotationCard;

        private final TextView titleText;
        private final TextView dateText;

        private final MaterialCardView statusCard;
        private final TextView statusText;

        private final TextView orderForText;

        private final LinearLayout mobileLayout;
        private final TextView mobileText;

        private final TextView itemCountText;
        private final TextView totalVpText;
        private final TextView grandTotalText;

        private final MaterialCardView notesCard;
        private final TextView notesText;

        private final MaterialButton changeStatusButton;
        private final MaterialButton renameButton;
        private final MaterialButton duplicateButton;
        private final MaterialButton openButton;
        private final MaterialButton shareButton;
        private final MaterialButton deleteButton;

        private SavedQuotationViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            quotationCard =
                    itemView.findViewById(
                            R.id.card_saved_quotation
                    );

            titleText =
                    itemView.findViewById(
                            R.id.text_saved_title
                    );

            dateText =
                    itemView.findViewById(
                            R.id.text_saved_date
                    );

            statusCard =
                    itemView.findViewById(
                            R.id.card_saved_status
                    );

            statusText =
                    itemView.findViewById(
                            R.id.text_saved_status
                    );

            orderForText =
                    itemView.findViewById(
                            R.id.text_saved_order_for
                    );

            mobileLayout =
                    itemView.findViewById(
                            R.id.layout_saved_mobile
                    );

            mobileText =
                    itemView.findViewById(
                            R.id.text_saved_mobile
                    );

            itemCountText =
                    itemView.findViewById(
                            R.id.text_saved_item_count
                    );

            totalVpText =
                    itemView.findViewById(
                            R.id.text_saved_total_vp
                    );

            grandTotalText =
                    itemView.findViewById(
                            R.id.text_saved_grand_total
                    );

            notesCard =
                    itemView.findViewById(
                            R.id.card_saved_notes
                    );

            notesText =
                    itemView.findViewById(
                            R.id.text_saved_notes
                    );

            changeStatusButton =
                    itemView.findViewById(
                            R.id.button_change_saved_status
                    );

            renameButton =
                    itemView.findViewById(
                            R.id.button_rename_saved_quotation
                    );

            duplicateButton =
                    itemView.findViewById(
                            R.id.button_duplicate_saved_quotation
                    );

            openButton =
                    itemView.findViewById(
                            R.id.button_open_saved_quotation
                    );

            shareButton =
                    itemView.findViewById(
                            R.id.button_share_saved_quotation
                    );

            deleteButton =
                    itemView.findViewById(
                            R.id.button_delete_saved_quotation
                    );
        }

        private void bind(
                SavedQuotation quotation,
                OnQuotationActionListener listener,
                SavedQuotationAdapter adapter
        ) {
            if (quotation == null) {
                return;
            }

            String quotationTitle =
                    quotation.getTitle();

            if (TextUtils.isEmpty(
                    quotationTitle
            )) {
                quotationTitle =
                        "Saved Quotation";
            }

            titleText.setText(
                    quotationTitle
            );

            dateText.setText(
                    adapter.formatSavedDate(
                            quotation.getUpdatedAt(),
                            quotation.getCreatedAt()
                    )
            );

            orderForText.setText(
                    adapter.getOrderForText(
                            quotation
                    )
            );

            bindStatus(
                    quotation,
                    listener,
                    adapter
            );

            bindMobileNumber(
                    quotation
            );

            itemCountText.setText(
                    String.valueOf(
                            Math.max(
                                    quotation.getTotalQuantity(),
                                    0
                            )
                    )
            );

            totalVpText.setText(
                    adapter.formatVp(
                            quotation.getTotalVp()
                    )
            );

            grandTotalText.setText(
                    adapter.formatRupees(
                            quotation.getGrandTotal()
                    )
            );

            bindNotes(
                    quotation.getNotes()
            );

            quotationCard.setOnClickListener(
                    view -> {
                        if (listener != null) {
                            listener.onOpenQuotation(
                                    quotation
                            );
                        }
                    }
            );

            openButton.setOnClickListener(
                    view -> {
                        if (listener != null) {
                            listener.onOpenQuotation(
                                    quotation
                            );
                        }
                    }
            );

            shareButton.setOnClickListener(
                    view -> {
                        if (listener != null) {
                            listener.onShareQuotation(
                                    quotation
                            );
                        }
                    }
            );

            renameButton.setOnClickListener(
                    view -> {
                        if (listener != null) {
                            listener.onRenameQuotation(
                                    quotation
                            );
                        }
                    }
            );

            duplicateButton.setOnClickListener(
                    view -> {
                        if (listener != null) {
                            listener.onDuplicateQuotation(
                                    quotation
                            );
                        }
                    }
            );

            deleteButton.setOnClickListener(
                    view -> {
                        if (listener != null) {
                            listener.onDeleteQuotation(
                                    quotation
                            );
                        }
                    }
            );
        }

        private void bindStatus(
                SavedQuotation quotation,
                OnQuotationActionListener listener,
                SavedQuotationAdapter adapter
        ) {
            String currentStatus =
                    adapter.normalizeStatus(
                            quotation.getStatus()
                    );

            statusText.setText(
                    currentStatus.toUpperCase(
                            Locale.getDefault()
                    )
            );

            if (SavedQuotation.STATUS_FINAL.equals(
                    currentStatus
            )) {
                applyFinalStatusStyle();

                changeStatusButton.setVisibility(
                        View.VISIBLE
                );

                changeStatusButton.setText(
                        "Mark Sent"
                );

                changeStatusButton.setOnClickListener(
                        view -> {
                            if (listener != null) {
                                listener.onChangeQuotationStatus(
                                        quotation,
                                        SavedQuotation.STATUS_SENT
                                );
                            }
                        }
                );

                return;
            }

            if (SavedQuotation.STATUS_SENT.equals(
                    currentStatus
            )) {
                applySentStatusStyle();

                changeStatusButton.setVisibility(
                        View.GONE
                );

                changeStatusButton.setOnClickListener(
                        null
                );

                return;
            }

            applyDraftStatusStyle();

            changeStatusButton.setVisibility(
                    View.VISIBLE
            );

            changeStatusButton.setText(
                    "Mark Final"
            );

            changeStatusButton.setOnClickListener(
                    view -> {
                        if (listener != null) {
                            listener.onChangeQuotationStatus(
                                    quotation,
                                    SavedQuotation.STATUS_FINAL
                            );
                        }
                    }
            );
        }

        private void applyDraftStatusStyle() {
            int backgroundColor =
                    Color.parseColor(
                            "#FFF8E8"
                    );

            int strokeColor =
                    Color.parseColor(
                            "#E8D29A"
                    );

            int contentColor =
                    Color.parseColor(
                            "#80631D"
                    );

            statusCard.setCardBackgroundColor(
                    backgroundColor
            );

            statusCard.setStrokeColor(
                    strokeColor
            );

            statusText.setTextColor(
                    contentColor
            );

            changeStatusButton.setBackgroundTintList(
                    ColorStateList.valueOf(
                            backgroundColor
                    )
            );

            changeStatusButton.setStrokeColor(
                    ColorStateList.valueOf(
                            strokeColor
                    )
            );

            changeStatusButton.setTextColor(
                    contentColor
            );

            changeStatusButton.setIconTint(
                    ColorStateList.valueOf(
                            contentColor
                    )
            );
        }

        private void applyFinalStatusStyle() {
            int backgroundColor =
                    Color.parseColor(
                            "#EEF6FD"
                    );

            int strokeColor =
                    Color.parseColor(
                            "#B8D8F2"
                    );

            int contentColor =
                    Color.parseColor(
                            "#0F6CBD"
                    );

            statusCard.setCardBackgroundColor(
                    backgroundColor
            );

            statusCard.setStrokeColor(
                    strokeColor
            );

            statusText.setTextColor(
                    contentColor
            );

            changeStatusButton.setBackgroundTintList(
                    ColorStateList.valueOf(
                            backgroundColor
                    )
            );

            changeStatusButton.setStrokeColor(
                    ColorStateList.valueOf(
                            strokeColor
                    )
            );

            changeStatusButton.setTextColor(
                    contentColor
            );

            changeStatusButton.setIconTint(
                    ColorStateList.valueOf(
                            contentColor
                    )
            );
        }

        private void applySentStatusStyle() {
            int backgroundColor =
                    Color.parseColor(
                            "#EAF7EE"
                    );

            int strokeColor =
                    Color.parseColor(
                            "#A9DDBB"
                    );

            int contentColor =
                    Color.parseColor(
                            "#107C10"
                    );

            statusCard.setCardBackgroundColor(
                    backgroundColor
            );

            statusCard.setStrokeColor(
                    strokeColor
            );

            statusText.setTextColor(
                    contentColor
            );
        }

        private void bindMobileNumber(
                SavedQuotation quotation
        ) {
            String orderType =
                    quotation.getOrderType();

            boolean customerOrder =
                    orderType != null
                            && orderType.equalsIgnoreCase(
                            SavedQuotation.ORDER_TYPE_CUSTOMER
                    );

            String mobileNumber =
                    quotation.getCustomerMobile();

            boolean showMobile =
                    customerOrder
                            && !TextUtils.isEmpty(
                            mobileNumber
                    );

            mobileLayout.setVisibility(
                    showMobile
                            ? View.VISIBLE
                            : View.GONE
            );

            if (showMobile) {
                mobileText.setText(
                        mobileNumber.trim()
                );

            } else {
                mobileText.setText("");
            }
        }

        private void bindNotes(
                String notes
        ) {
            boolean hasNotes =
                    !TextUtils.isEmpty(
                            notes
                    );

            notesCard.setVisibility(
                    hasNotes
                            ? View.VISIBLE
                            : View.GONE
            );

            if (hasNotes) {
                notesText.setText(
                        notes.trim()
                );

            } else {
                notesText.setText("");
            }
        }
    }
}