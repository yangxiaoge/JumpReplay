package com.fourtwo.hookintent.ui.blocker;

import org.json.JSONException;
import org.json.JSONObject;

public class DeepLinkRule {

    private final String packageName;
    private final String appName;
    private final String activityName;
    private final String scheme;
    private final String host;
    private final String port;
    private final String path;
    private final String pathPrefix;
    private final String pathPattern;

    private boolean selected;

    public DeepLinkRule(String packageName,
                        String appName,
                        String activityName,
                        String scheme,
                        String host,
                        String port,
                        String path,
                        String pathPrefix,
                        String pathPattern) {
        this.packageName = safe(packageName);
        this.appName = safe(appName);
        this.activityName = safe(activityName);
        this.scheme = safe(scheme);
        this.host = safe(host);
        this.port = safe(port);
        this.path = safe(path);
        this.pathPrefix = safe(pathPrefix);
        this.pathPattern = safe(pathPattern);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String buildTitle() {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme);

        if (!host.isEmpty()) {
            sb.append("://").append(host);
        }

        if (!port.isEmpty()) {
            sb.append(":").append(port);
        }

        if (!path.isEmpty()) {
            sb.append(path);
        } else if (!pathPrefix.isEmpty()) {
            sb.append(" [prefix=").append(pathPrefix).append("]");
        } else if (!pathPattern.isEmpty()) {
            sb.append(" [pattern=").append(pathPattern).append("]");
        }

        return sb.toString();
    }

    public String buildSubtitle() {
        StringBuilder sb = new StringBuilder();
        if (!activityName.isEmpty()) {
            sb.append(activityName);
        }
        if (!host.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("host=").append(host);
        }
        if (!pathPrefix.isEmpty()) {
            if (sb.length() > 0) sb.append("  ");
            sb.append("pathPrefix=").append(pathPrefix);
        }
        if (!pathPattern.isEmpty()) {
            if (sb.length() > 0) sb.append("  ");
            sb.append("pathPattern=").append(pathPattern);
        }
        return sb.toString();
    }

    public boolean sameAs(DeepLinkRule other) {
        if (other == null) return false;
        return packageName.equals(other.packageName)
                && activityName.equals(other.activityName)
                && scheme.equals(other.scheme)
                && host.equals(other.host)
                && port.equals(other.port)
                && path.equals(other.path)
                && pathPrefix.equals(other.pathPrefix)
                && pathPattern.equals(other.pathPattern);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("packageName", packageName);
        object.put("appName", appName);
        object.put("activityName", activityName);
        object.put("scheme", scheme);
        object.put("host", host);
        object.put("port", port);
        object.put("path", path);
        object.put("pathPrefix", pathPrefix);
        object.put("pathPattern", pathPattern);
        return object;
    }
}