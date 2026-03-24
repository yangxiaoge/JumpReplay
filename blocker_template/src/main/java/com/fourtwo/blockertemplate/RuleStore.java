package com.fourtwo.blockertemplate;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RuleStore {

    private static final String PREF_NAME = "blocker_rules";
    private static final String KEY_RULES = "rules";

    private RuleStore() {
    }

    public static List<BlockRule> load(Context context) {
        List<BlockRule> result = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_RULES, null);
        if (json == null || json.trim().isEmpty()) {
            return result;
        }

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                result.add(BlockRule.fromJson(object));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static void save(Context context, List<BlockRule> rules) {
        try {
            JSONArray array = new JSONArray();
            for (BlockRule rule : rules) {
                array.put(rule.toJson());
            }

            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_RULES, array.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static boolean matches(Context context, String uri) {
        List<BlockRule> rules = load(context);
        for (BlockRule rule : rules) {
            if (rule.matches(uri)) {
                return true;
            }
        }
        return false;
    }
}