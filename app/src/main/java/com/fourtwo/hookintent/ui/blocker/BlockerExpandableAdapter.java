package com.fourtwo.hookintent.ui.blocker;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fourtwo.hookintent.R;
import com.fourtwo.hookintent.utils.AppInfoHelper;

import java.util.ArrayList;
import java.util.List;

public class BlockerExpandableAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onDataChanged();
    }

    private static final int TYPE_GROUP = 1;
    private static final int TYPE_RULE = 2;

    private final List<BlockerGroup> groups = new ArrayList<>();
    private final List<RowItem> rows = new ArrayList<>();
    private final Listener listener;

    public BlockerExpandableAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<BlockerGroup> newGroups) {
        groups.clear();
        if (newGroups != null) {
            groups.addAll(newGroups);
        }
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        for (BlockerGroup group : groups) {
            rows.add(RowItem.forGroup(group));
            if (group.isExpanded()) {
                for (DeepLinkRule rule : group.getRules()) {
                    rows.add(RowItem.forRule(group, rule));
                }
            }
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onDataChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_GROUP) {
            return new GroupHolder(inflater.inflate(R.layout.item_blocker_group, parent, false));
        }
        return new RuleHolder(inflater.inflate(R.layout.item_blocker_rule, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowItem item = rows.get(position);
        if (holder instanceof GroupHolder) {
            bindGroup((GroupHolder) holder, item.group);
        } else if (holder instanceof RuleHolder) {
            bindRule((RuleHolder) holder, item.rule);
        }
    }

    private void bindGroup(@NonNull GroupHolder holder, @NonNull BlockerGroup group) {
        holder.title.setText(group.getAppName());

        if (group.isCustom()) {
            holder.subtitle.setText(
                    holder.itemView.getContext().getString(
                            R.string.blocker_group_subtitle,
                            group.getSelectedCount(),
                            group.getRules().size()
                    )
            );
            Drawable drawable = ContextCompat.getDrawable(holder.itemView.getContext(), R.drawable.filter_list_off);
            holder.icon.setImageDrawable(drawable);
        } else {
            holder.subtitle.setText(
                    holder.itemView.getContext().getString(
                            R.string.blocker_group_subtitle_with_pkg,
                            group.getPackageName(),
                            group.getSelectedCount(),
                            group.getRules().size()
                    )
            );
            holder.icon.setImageDrawable(
                    AppInfoHelper.getAppInfo(holder.itemView.getContext(), group.getPackageName()).getAppIcon()
            );
        }

        holder.indicator.setText(group.isExpanded() ? "▼" : "▶");

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(group.isAllSelected());
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            group.setAllSelected(isChecked);
            rebuildRows();
        });

        holder.itemView.setOnClickListener(v -> {
            group.toggleExpanded();
            rebuildRows();
        });
    }

    private void bindRule(@NonNull RuleHolder holder, @NonNull DeepLinkRule rule) {
        holder.title.setText(rule.buildTitle());
        holder.subtitle.setText(rule.buildSubtitle());

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(rule.isSelected());
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rule.setSelected(isChecked);
            rebuildRows();
        });

        holder.itemView.setOnClickListener(v -> {
            boolean next = !rule.isSelected();
            rule.setSelected(next);
            rebuildRows();
        });
    }

    private static class RowItem {
        final int type;
        final BlockerGroup group;
        final DeepLinkRule rule;

        private RowItem(int type, BlockerGroup group, DeepLinkRule rule) {
            this.type = type;
            this.group = group;
            this.rule = rule;
        }

        static RowItem forGroup(BlockerGroup group) {
            return new RowItem(TYPE_GROUP, group, null);
        }

        static RowItem forRule(BlockerGroup group, DeepLinkRule rule) {
            return new RowItem(TYPE_RULE, group, rule);
        }
    }

    static class GroupHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        ImageView icon;
        TextView title;
        TextView subtitle;
        TextView indicator;

        GroupHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.check_group);
            icon = itemView.findViewById(R.id.icon_group);
            title = itemView.findViewById(R.id.text_group_title);
            subtitle = itemView.findViewById(R.id.text_group_subtitle);
            indicator = itemView.findViewById(R.id.text_expand_indicator);
        }
    }

    static class RuleHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView title;
        TextView subtitle;

        RuleHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.check_rule);
            title = itemView.findViewById(R.id.text_rule_title);
            subtitle = itemView.findViewById(R.id.text_rule_subtitle);
        }
    }
}