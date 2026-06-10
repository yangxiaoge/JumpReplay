package com.fourtwo.hookintent.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.fourtwo.hookintent.base.DataConverter;
import com.fourtwo.hookintent.base.Extract;
import com.fourtwo.hookintent.base.JsonHandler;
import com.fourtwo.hookintent.data.ItemData;

import java.util.HashMap;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DataProcessor {
    private static final String TAG = "DataProcessor";

    private final Context context;
    private final IntentDuplicateChecker intentDuplicateChecker;
    private final IntentDuplicateChecker schemeDuplicateChecker;
    private Map<String, Object> JsonData = new HashMap<>();

    public DataProcessor(Context context) {
        this.context = context;
        this.intentDuplicateChecker = new IntentDuplicateChecker();
        this.schemeDuplicateChecker = new IntentDuplicateChecker();
    }

    public interface DataProcessedCallback {
        void onDataProcessed(ItemData itemData);
    }

    // 新增方法：设置 JsonData
    public void setJsonData(Map<String, Object> jsonData) {
        this.JsonData = jsonData != null ? jsonData : new HashMap<>();
    }
    public void processBundle(Bundle bundle, DataProcessedCallback callback) {
        String category = bundle.getString("category");
        String stackTrace = bundle.getString("stack_trace");
        String uri = bundle.getString("uri");
        String time = Extract.extractTime(bundle.getString("time"));
        if (!JsonData.isEmpty()) {
            if (filterData(category, bundle)) {
                return;
            }
        }
        Log.d(TAG, "processBundle1: " + bundle.getString("time"));
        String dataSize = Extract.calculateBundleDataSize(bundle);
        String packageName;
        String component;
        String dataString = "";

        if ("Intent".equals(category)) {
            if (!JsonData.isEmpty()) {
                if (handleIntentBase(bundle)) return;
            }
            component = bundle.getString("component");
            packageName = component;
            ArrayList<?> intentExtras = (ArrayList<?>) bundle.getSerializable("intentExtras");
            if (intentExtras != null) {
                dataString = Extract.extractIntentExtrasString(intentExtras);
            }
            if (Objects.equals(component, "null") && !Objects.equals(bundle.getString("dataString"), "null")) {
                packageName = SchemeResolver.findAppByUri(context, bundle.getString("dataString"));
                component = bundle.getString("dataString");
            }
            if (Objects.equals(component, "null") && !Objects.equals(bundle.getString("action"), "null")) {
                packageName = SchemeResolver.findAppByUri(context, bundle.getString("action"));
                component = bundle.getString("action");
            }
        } else if ("Scheme".equals(category)) {
            if (!JsonData.isEmpty()) {
                if (handleSchemeBase(bundle)) return;
            }
            String schemeRawUrl = bundle.getString("scheme_raw_url");
            packageName = SchemeResolver.findAppByUri(context, schemeRawUrl);
            Bundle bundle1 = DataConverter.convertUriToBundle(Uri.parse(schemeRawUrl));
            bundle.putAll(bundle1);

            if (schemeRawUrl.startsWith("#Intent;") || bundle.getString("authority").equals("null")) {
                component = schemeRawUrl;
                packageName = Extract.getIntentSchemeValue(schemeRawUrl, "component");
                if (packageName == null) {
                    packageName = Extract.getIntentSchemeValue(schemeRawUrl, "action");
                }
            } else {
                component = bundle.getString("scheme") + "://" + bundle.getString("authority") + bundle.getString("path");
            }
            dataString = bundle.getString("query");
            if ("null".equals(dataString)) {
                dataString = "";
            }
        } else {
            packageName = bundle.getString("packageName");
            component = bundle.getString("title");
            dataString = bundle.getString("data");
        }

        AppInfoHelper.AppInfo appInfo = AppInfoHelper.getAppInfo(context, packageName);
        String appName = appInfo.getAppName();
        Drawable appIcon = appInfo.getAppIcon();

        Log.d(TAG, "processBundle end: " + category);
        ItemData itemData = new ItemData(
                appIcon,
                appName,
                component,
                dataString,
                time,
                String.format("%s B", dataSize),
                bundle,
                category,
                stackTrace,
                uri
        );

        if (callback != null) {
            callback.onDataProcessed(itemData);
        }
    }

    public void processReceivedData(Bundle data, DataProcessedCallback callback) {
        try {
            if (data == null) {
                return;
            }

            String batchDataJson = data.getString("batch_data_binder");
            if (batchDataJson != null && !batchDataJson.trim().isEmpty()) {
                List<Bundle> bundles = JsonHandler.fromJson(batchDataJson);
                Log.d(TAG, "processReceivedData (batch): " + (bundles != null ? bundles.size() : 0));
                if (bundles != null) {
                    for (Bundle bundle : bundles) {
                        processBundle(bundle, callback);
                    }
                }
            } else {
                // 兼容本地直接投递的单条数据格式，直接进行解析
                Log.d(TAG, "processReceivedData (single): " + data);
                processBundle(data, callback);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing data", e);
        }
    }

    private boolean handleIntentBase(Bundle bundle) {
//        Log.d(TAG, "Removing duplicate Intent bundle: " + bundle);
        return intentDuplicateChecker.isDuplicate(bundle);
    }

    private boolean handleSchemeBase(Bundle bundle) {
//        Log.d(TAG, "Removing duplicate Scheme bundle: " + bundle);
        return schemeDuplicateChecker.isDuplicate(bundle);
    }


    public boolean isFilterMatched(Map<String, Object> groupData, String key, String valueToMatch) {
        if (valueToMatch == null) {
            return false;
        }

        List<Map<String, Object>> itemList = JsonHandler.getFilterValueJson(groupData.get(key));
        if (itemList == null) {
            return false;
        }

        for (Map<String, Object> item : itemList) {
            Object type = item.get("type");
            Object text = item.get("text");

            if (Boolean.TRUE.equals(type) && text != null
                    && valueToMatch.equalsIgnoreCase(String.valueOf(text))) {
                return true;
            }
        }
        return false;
    }

    private boolean filterData(String base, Bundle bundle) {
        if (JsonData == null || JsonData.isEmpty() || base == null) {
            return false;
        }

        // 保留旧逻辑：无效 Scheme 直接丢弃，避免后续解析异常
        if ("Scheme".equals(base)) {
            String schemeRawUrl = bundle.getString("scheme_raw_url");
            if (schemeRawUrl == null) {
                return true;
            }

            String scheme = Uri.parse(schemeRawUrl).getScheme();
            if (scheme == null) {
                return true;
            }
        }

        Map<String, Object> groupData = resolveFilterGroup(base);
        if (groupData == null || groupData.isEmpty()) {
            return false;
        }

        for (String key : groupData.keySet()) {
            String valueToMatch = resolveBundleValue(base, bundle, key);
            if (valueToMatch == null || valueToMatch.trim().isEmpty()) {
                continue;
            }

            if (isFilterMatched(groupData, key, valueToMatch)) {
                return true;
            }
        }

        return false;
    }

    private Map<String, Object> resolveFilterGroup(String category) {
        if (JsonData == null || JsonData.isEmpty() || category == null) {
            return null;
        }

        Object directGroup = JsonData.get(category);
        if (directGroup != null) {
            return JsonHandler.getFilterKeyJson(directGroup);
        }

        if ("Intent".equalsIgnoreCase(category)) {
            Object legacyIntentGroup = JsonData.get("intent");
            if (legacyIntentGroup != null) {
                return JsonHandler.getFilterKeyJson(legacyIntentGroup);
            }
        }

        if ("Scheme".equalsIgnoreCase(category)) {
            Object legacySchemeGroup = JsonData.get("scheme");
            if (legacySchemeGroup != null) {
                return JsonHandler.getFilterKeyJson(legacySchemeGroup);
            }
        }

        Object lowerCaseGroup = JsonData.get(category.toLowerCase(Locale.ROOT));
        if (lowerCaseGroup != null) {
            return JsonHandler.getFilterKeyJson(lowerCaseGroup);
        }

        return null;
    }

    private String resolveBundleValue(String category, Bundle bundle, String key) {
        Object directValue = bundle.get(key);
        if (directValue != null) {
            return String.valueOf(directValue);
        }

        if ("Scheme".equals(category)) {
            return resolveSchemeValue(bundle, key);
        }

        return null;
    }

    private String resolveSchemeValue(Bundle bundle, String key) {
        String schemeRawUrl = bundle.getString("scheme_raw_url");
        if (schemeRawUrl == null) {
            return null;
        }

        if ("scheme_raw_url".equals(key)) {
            return schemeRawUrl;
        }

        Uri uri;
        try {
            uri = Uri.parse(schemeRawUrl);
        } catch (Exception e) {
            return null;
        }

        switch (key) {
            case "scheme":
                return uri.getScheme();
            case "schemeSpecificPart":
                return uri.getSchemeSpecificPart();
            case "authority":
                return uri.getAuthority();
            case "userInfo":
                return uri.getUserInfo();
            case "host":
                return uri.getHost();
            case "port":
                return String.valueOf(uri.getPort());
            case "path":
                return uri.getPath();
            case "query":
                return uri.getQuery();
            case "fragment":
                return uri.getFragment();
            case "lastPathSegment":
                return uri.getLastPathSegment();
            case "pathSegments":
                return String.valueOf(uri.getPathSegments());
            default:
                return null;
        }
    }
}
