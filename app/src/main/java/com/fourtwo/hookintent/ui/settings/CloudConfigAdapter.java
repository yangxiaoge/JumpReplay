package com.fourtwo.hookintent.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fourtwo.hookintent.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CloudConfigAdapter extends RecyclerView.Adapter<CloudConfigAdapter.ViewHolder> {

    public interface OnCloudConfigClickListener {
        void onConfigClick(Map<String, Object> item);
    }

    private final List<Map<String, Object>> dataList;
    private final OnCloudConfigClickListener listener;

    public CloudConfigAdapter(List<Map<String, Object>> dataList, OnCloudConfigClickListener listener) {
        this.dataList = dataList != null ? dataList : new ArrayList<>();
        this.listener = listener;
    }

    public void updateData(List<Map<String, Object>> newData) {
        dataList.clear();
        if (newData != null) {
            dataList.addAll(newData);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cloud_config, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> item = dataList.get(position);

        String name = valueOf(item.get("name"));
        String remark = valueOf(item.get("remark"));
        String packageName = valueOf(item.get("packageName"));
        String className = valueOf(item.get("className"));
        String methodName = valueOf(item.get("methodName"));
        String category = valueOf(item.get("category"));
        String title = valueOf(item.get("title"));
        String data = valueOf(item.get("data"));

        if (name.isEmpty()) {
            if (!methodName.isEmpty()) {
                name = methodName;
            } else if (!category.isEmpty()) {
                name = category;
            } else {
                name = "未命名配置";
            }
        }

        if (remark.isEmpty()) {
            remark = "无备注";
        }

        StringBuilder summaryBuilder = new StringBuilder();
        if (!category.isEmpty()) {
            summaryBuilder.append("分类: ").append(category).append("\n");
        }
        if (!packageName.isEmpty()) {
            summaryBuilder.append("包名: ").append(packageName).append("\n");
        }
        if (!className.isEmpty()) {
            summaryBuilder.append("类名: ").append(className).append("\n");
        }
        if (!methodName.isEmpty()) {
            summaryBuilder.append("方法: ").append(methodName).append("\n");
        }
        if (!title.isEmpty()) {
            summaryBuilder.append("Title: ").append(title).append("\n");
        }
        if (!data.isEmpty()) {
            summaryBuilder.append("Data: ").append(data).append("\n");
        }

        String summary = summaryBuilder.toString().trim();
        if (summary.isEmpty()) {
            summary = "无可展示字段";
        }

        holder.name.setText(name);
        holder.remark.setText(remark);
        holder.summary.setText(summary);

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION && listener != null) {
                listener.onConfigClick(dataList.get(adapterPosition));
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    private String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView remark;
        TextView summary;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.cloud_name);
            remark = itemView.findViewById(R.id.cloud_remark);
            summary = itemView.findViewById(R.id.cloud_summary);
        }
    }
}