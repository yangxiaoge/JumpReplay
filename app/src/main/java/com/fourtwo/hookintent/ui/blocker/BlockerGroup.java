package com.fourtwo.hookintent.ui.blocker;

import java.util.ArrayList;
import java.util.List;

public class BlockerGroup {

    private final String packageName;
    private final String appName;
    private final boolean custom;
    private final List<DeepLinkRule> rules;
    private boolean expanded = false;

    public BlockerGroup(String packageName, String appName, boolean custom, List<DeepLinkRule> rules) {
        this.packageName = packageName == null ? "" : packageName;
        this.appName = appName == null ? "" : appName;
        this.custom = custom;
        this.rules = rules == null ? new ArrayList<>() : rules;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public boolean isCustom() {
        return custom;
    }

    public List<DeepLinkRule> getRules() {
        return rules;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    public int getSelectedCount() {
        int count = 0;
        for (DeepLinkRule rule : rules) {
            if (rule.isSelected()) {
                count++;
            }
        }
        return count;
    }

    public boolean isAllSelected() {
        return !rules.isEmpty() && getSelectedCount() == rules.size();
    }

    public void setAllSelected(boolean selected) {
        for (DeepLinkRule rule : rules) {
            rule.setSelected(selected);
        }
    }
}