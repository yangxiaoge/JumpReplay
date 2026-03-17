package com.fourtwo.hookintent.ui.home;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.fourtwo.hookintent.R;
import com.fourtwo.hookintent.base.JsonHandler;
import com.fourtwo.hookintent.data.Constants;
import com.fourtwo.hookintent.data.ItemData;
import com.fourtwo.hookintent.utils.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeAdapter extends RecyclerView.Adapter<HomeAdapter.ViewHolder> implements Filterable {

    private List<ItemData> mData;
    private List<ItemData> filteredData;
    private final int normalColor = Color.WHITE;
    private final int pressedColor = Color.LTGRAY;

    private String currentQuery = "";

    public HomeAdapter(List<ItemData> data) {
        this.mData = data != null ? new ArrayList<>(data) : new ArrayList<>();
        this.filteredData = new ArrayList<>(this.mData);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.fragment_item_list, parent, false);
        return new ViewHolder(view);
    }

    private final Filter homeFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<ItemData> source = mData != null ? new ArrayList<>(mData) : new ArrayList<>();
            List<ItemData> filteredList = new ArrayList<>();

            String filterPattern = constraint == null
                    ? ""
                    : constraint.toString().toLowerCase(Locale.ROOT).trim();

            if (filterPattern.isEmpty()) {
                filteredList.addAll(source);
            } else {
                for (ItemData item : source) {
                    if (item == null) {
                        continue;
                    }

                    String appName = safeLower(item.getAppName());
                    String itemFrom = safeLower(item.getItem_from());
                    String itemData = safeLower(item.getItem_data());

                    if (appName.contains(filterPattern)
                            || itemFrom.contains(filterPattern)
                            || itemData.contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @SuppressWarnings("unchecked")
        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            currentQuery = constraint == null ? "" : constraint.toString();

            filteredData.clear();
            if (results != null && results.values instanceof List) {
                filteredData.addAll((List<ItemData>) results.values);
            }
            notifyDataSetChanged();
        }
    };

    @Override
    public Filter getFilter() {
        return homeFilter;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ItemData item = filteredData.get(position);

        holder.icon.setImageDrawable(item.getIcon());
        holder.appName.setText(item.getAppName());
        holder.item_from.setText(item.getItem_from());
        holder.item_data.setText(item.getItem_data());
        holder.timestamp.setText(item.getTimestamp());
        holder.dataSize.setText(item.getDataSize());

        String category = item.getCategory();
        holder.category.setText(category);

        Context context = holder.itemView.getContext();

        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.badge_background);
        if (!(drawable instanceof GradientDrawable)) {
            return;
        }

        GradientDrawable background = (GradientDrawable) drawable.mutate();
        String COLORS_CONFIG = SharedPreferencesUtils.getStr(context, Constants.COLORS_CONFIG);
        Map<String, String> COLORS = JsonHandler.jsonToMap(COLORS_CONFIG);

        if (category != null && COLORS.containsKey(category)) {
            background.setColor(Color.parseColor(COLORS.get(category)));
        } else {
            background.setColor(Color.GRAY);
        }
        holder.category.setBackground(background);

        holder.itemView.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(
                    (Activity) v.getContext(),
                    R.id.nav_host_fragment_content_main
            );

            Bundle bundle = new Bundle();
            bundle.putParcelable("itemData", item);

            navController.navigate(R.id.action_nav_home_to_nav_detail, bundle);
        });

        holder.itemView.setOnLongClickListener(v -> {
            showPopupWindow(v, item);
            return true;
        });

        holder.itemView.setOnTouchListener(new View.OnTouchListener() {
            private ValueAnimator animator;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startColorAnimation(holder.itemView, normalColor, pressedColor);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        startColorAnimation(holder.itemView, pressedColor, normalColor);
                        break;
                }
                return false;
            }

            private void startColorAnimation(final View view, int startColor, int endColor) {
                if (animator != null && animator.isRunning()) {
                    animator.cancel();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    animator = ValueAnimator.ofArgb(startColor, endColor);
                }
                if (animator == null) {
                    return;
                }
                animator.setDuration(300);
                animator.addUpdateListener(animation ->
                        view.setBackgroundColor((int) animation.getAnimatedValue()));
                animator.start();
            }
        });
    }

    private void showPopupWindow(View view, ItemData item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
        builder.setTitle(item.getCategory())
                .setMessage(String.valueOf(item.getItem_from()) + "\n\n" + String.valueOf(item.getItem_data()))
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < filteredData.size()) {
            ItemData item = filteredData.get(position);
            filteredData.remove(position);
            mData.remove(item);
            notifyItemRemoved(position);
        }
    }

    @Override
    public int getItemCount() {
        return filteredData.size();
    }

    public List<ItemData> getFilteredData() {
        return filteredData;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<ItemData> newData) {
        this.mData = newData != null ? new ArrayList<>(newData) : new ArrayList<>();

        if (currentQuery == null || currentQuery.trim().isEmpty()) {
            this.filteredData = new ArrayList<>(this.mData);
            notifyDataSetChanged();
        } else {
            getFilter().filter(currentQuery);
        }
    }

    public List<ItemData> getData() {
        return mData;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearData() {
        currentQuery = "";
        mData.clear();
        filteredData.clear();
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView appName, item_from, item_data, timestamp, dataSize, category;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            appName = itemView.findViewById(R.id.app_name);
            category = itemView.findViewById(R.id.category);
            item_from = itemView.findViewById(R.id.item_from);
            item_data = itemView.findViewById(R.id.package_name);
            timestamp = itemView.findViewById(R.id.timestamp);
            dataSize = itemView.findViewById(R.id.data_size);
        }
    }
}