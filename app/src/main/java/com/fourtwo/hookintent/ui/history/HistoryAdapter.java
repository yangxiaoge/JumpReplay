package com.fourtwo.hookintent.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fourtwo.hookintent.R;
import com.fourtwo.hookintent.data.ReleaseInfo;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<ReleaseInfo> data;

    public HistoryAdapter(List<ReleaseInfo> data) {
        this.data = data == null ? new ArrayList<>() : data;
    }

    public void updateData(List<ReleaseInfo> newData) {
        data.clear();
        if (newData != null) {
            data.addAll(newData);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReleaseInfo item = data.get(position);

        holder.versionText.setText(item.getDisplayVersion());
        holder.dateText.setText(
                holder.itemView.getContext().getString(
                        R.string.history_published_at,
                        formatPublishedAt(item.getPublishedAt())
                )
        );

        String body = item.getBody() == null || item.getBody().trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.update_no_changelog)
                : item.getBody();
        holder.bodyText.setText(body);
    }

    private String formatPublishedAt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        return raw.replace("T", " ").replace("Z", "");
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView versionText;
        TextView dateText;
        TextView bodyText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            versionText = itemView.findViewById(R.id.text_history_version);
            dateText = itemView.findViewById(R.id.text_history_date);
            bodyText = itemView.findViewById(R.id.text_history_body);
        }
    }
}