package com.example.productprice.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.productprice.R;

import java.util.List;

public class AttractiveDropdownAdapter
        extends ArrayAdapter<String> {

    private final LayoutInflater layoutInflater;

    public AttractiveDropdownAdapter(
            @NonNull Context context,
            @NonNull List<String> items
    ) {
        super(
                context,
                R.layout.item_dropdown_option,
                R.id.text_dropdown_option,
                items
        );

        layoutInflater =
                LayoutInflater.from(context);
    }

    public AttractiveDropdownAdapter(
            @NonNull Context context,
            @NonNull String[] items
    ) {
        super(
                context,
                R.layout.item_dropdown_option,
                R.id.text_dropdown_option,
                items
        );

        layoutInflater =
                LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(
            int position,
            @Nullable View convertView,
            @NonNull ViewGroup parent
    ) {
        return createItemView(
                position,
                convertView,
                parent,
                false
        );
    }

    @Override
    public View getDropDownView(
            int position,
            @Nullable View convertView,
            @NonNull ViewGroup parent
    ) {
        return createItemView(
                position,
                convertView,
                parent,
                true
        );
    }

    private View createItemView(
            int position,
            @Nullable View convertView,
            @NonNull ViewGroup parent,
            boolean dropdownItem
    ) {
        View view = convertView;

        if (view == null) {
            view = layoutInflater.inflate(
                    R.layout.item_dropdown_option,
                    parent,
                    false
            );
        }

        TextView optionText =
                view.findViewById(
                        R.id.text_dropdown_option
                );

        String item =
                getItem(position);

        optionText.setText(
                item == null
                        ? ""
                        : item
        );

        if (dropdownItem) {
            optionText.setTextColor(
                    Color.parseColor("#242424")
            );

            optionText.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.NORMAL
            );

            optionText.setMinHeight(
                    dpToPx(52)
            );

            optionText.setPadding(
                    dpToPx(18),
                    dpToPx(12),
                    dpToPx(18),
                    dpToPx(12)
            );

        } else {
            optionText.setTextColor(
                    Color.parseColor("#242424")
            );

            optionText.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.NORMAL
            );

            optionText.setMinHeight(
                    dpToPx(48)
            );

            optionText.setPadding(
                    dpToPx(4),
                    0,
                    dpToPx(4),
                    0
            );
        }

        return view;
    }

    private int dpToPx(int dp) {
        float density =
                getContext()
                        .getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }
}