package com.fourtwo.blockertemplate;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class BlockRule {

    private String pattern;
    private boolean regex;
    private boolean enabled;

    public BlockRule(String pattern, boolean regex, boolean enabled) {
        this.pattern = safe(pattern);
        this.regex = regex;
        this.enabled = enabled;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isRegex() {
        return regex;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setPattern(String pattern) {
        this.pattern = safe(pattern);
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean matches(String uri) {
        if (!enabled) {
            return false;
        }

        String safeUri = safe(uri);
        if (safeUri.isEmpty()) {
            return false;
        }

        if (regex) {
            try {
                return Pattern.compile(pattern).matcher(safeUri).find();
            } catch (PatternSyntaxException ignored) {
                return false;
            }
        }

        return safeUri.startsWith(pattern);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("pattern", pattern);
        object.put("regex", regex);
        object.put("enabled", enabled);
        return object;
    }

    public static BlockRule fromJson(JSONObject object) {
        return new BlockRule(
                object.optString("pattern", ""),
                object.optBoolean("regex", false),
                object.optBoolean("enabled", true)
        );
    }
}