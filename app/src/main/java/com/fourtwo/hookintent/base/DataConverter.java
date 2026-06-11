package com.fourtwo.hookintent.base;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataConverter {

    @SuppressLint("SimpleDateFormat")
    public static Bundle convertIntentToBundle(Intent intent) {
        return convertIntentToBundle(intent, null, null);
    }

    @SuppressLint("SimpleDateFormat")
    public static Bundle convertIntentToBundle(Intent intent, Context context, String targetPkg) {
        Bundle bundle = new Bundle();

        bundle.putString("action", intent.getAction());
        bundle.putParcelable("clipData", intent.getClipData());
        bundle.putInt("flags", intent.getFlags());
        bundle.putString("dataString", intent.getDataString());
        bundle.putString("type", intent.getType());
        bundle.putString("componentClassName", Extract.extractComponent(String.valueOf(intent.getComponent())));
        bundle.putString("component", intent.getComponent() != null ? intent.getComponent().getClassName() : null);
        bundle.putString("scheme", intent.getScheme());
        bundle.putString("package", intent.getPackage());

        // 添加 categories
        Set<String> categories = intent.getCategories();
        if (categories != null) {
            bundle.putStringArrayList("categories", new ArrayList<>(categories));
        }

        // 添加 intent extras
        Bundle extras = null;
        try {
            extras = intent.getExtras();
        } catch (Exception e) {
            android.util.Log.e("DataConverter", "获取 getExtras() 发生异常: " + e.getMessage(), e);
        }

        if (extras != null) {
            // 尝试加载目标包名的 ClassLoader，避免跨进程读取 Parcelable 发生 BadParcelableException 导致数据丢失
            try {
                if (context != null && targetPkg != null) {
                    Context otherAppContext = context.createPackageContext(targetPkg, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                    extras.setClassLoader(otherAppContext.getClassLoader());
                }
            } catch (Exception e) {
                android.util.Log.e("DataConverter", "设置目标应用 ClassLoader 失败: " + targetPkg, e);
            }

            // 安全获取 keySet
            Set<String> keys = null;
            String debugStatus = "正常";
            try {
                keys = extras.keySet();
            } catch (Exception e) {
                android.util.Log.w("DataConverter", "首次获取 keySet 发生异常 (可能有自定义Parcelable): " + e.getMessage());
                debugStatus = "首次获取 keySet 异常: " + e.getMessage();
                // 根据 Android 源码，unparcel 异常后 mParcelledData 被置空，但部分解析出的 mMap 依然保留。
                // 再次调用 keySet() 可以安全返回已成功反序列化的部分 key 列表。
                try {
                    keys = extras.keySet();
                    debugStatus += " -> 二次获取成功";
                } catch (Exception ex) {
                    android.util.Log.e("DataConverter", "二次获取 keySet 依然失败", ex);
                    debugStatus += " -> 二次获取失败: " + ex.getMessage();
                }
            }

            ArrayList<Map<String, Object>> extrasList = new ArrayList<>();
            if (keys != null) {
                for (String key : keys) {
                    try {
                        Object value = extras.get(key);
                        Map<String, Object> extrasDetailMap = new HashMap<>();
                        extrasDetailMap.put("key", key);
                        extrasDetailMap.put("value", value != null ? value.toString() : "null");
                        extrasDetailMap.put("class", value != null ? value.getClass().getName() : "null");
                        extrasList.add(extrasDetailMap);
                    } catch (Exception e) {
                        android.util.Log.e("DataConverter", "反序列化单个 Key 失败: " + key, e);
                        // 单个 key 反序列化失败（如依然找不到类），仅记录错误信息，不影响其它 key 的读取
                        Map<String, Object> extrasDetailMap = new HashMap<>();
                        extrasDetailMap.put("key", key);
                        extrasDetailMap.put("value", "[无法反序列化的对象: " + e.getMessage() + "]");
                        extrasDetailMap.put("class", "unknown");
                        extrasList.add(extrasDetailMap);
                    }
                }
            } else {
                // 如果实在拿不到 keys，但我们有想拿的系统标准 Key，可以做防爆兜底扫描
                String[] commonKeys = {
                    "android.intent.extra.PACKAGE_NAME",
                    "android.intent.extra.TEXT",
                    "android.intent.extra.TITLE",
                    "android.intent.extra.SUBJECT",
                    "android.intent.extra.STREAM",
                    "android.intent.extra.EMAIL",
                    "android.intent.extra.CC",
                    "android.intent.extra.BCC",
                    "android.intent.extra.PHONE_NUMBER",
                    "android.intent.extra.MIME_TYPES",
                    "android.intent.extra.INTENT",
                    "android.intent.extra.KEY_EVENT",
                    "android.intent.extra.USER",
                    "android.intent.extra.UID",
                    "android.intent.extra.DATA_CHUNK",
                    "org.chromium.chrome.browser.referrer",
                    "referrer"
                };
                for (String k : commonKeys) {
                    try {
                        if (extras.containsKey(k)) {
                            Object value = extras.get(k);
                            if (value != null) {
                                Map<String, Object> extrasDetailMap = new HashMap<>();
                                extrasDetailMap.put("key", k);
                                extrasDetailMap.put("value", value.toString());
                                extrasDetailMap.put("class", value.getClass().getName());
                                extrasList.add(extrasDetailMap);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            bundle.putSerializable("intentExtras", extrasList); // 使用 Serializable 存储 ArrayList<Map>
            bundle.putString("debug_extras_status", debugStatus);
        } else {
            bundle.putString("debug_extras_status", "extras为null");
        }

        return bundle;
    }

    public static Bundle convertUriToBundle(Uri uri) {
        Bundle bundle = new Bundle();

        if (uri != null) {
            bundle.putString("scheme", uri.getScheme());
            bundle.putString("schemeSpecificPart", uri.getSchemeSpecificPart());
            bundle.putString("authority", uri.getAuthority());
            bundle.putString("userInfo", uri.getUserInfo());
            bundle.putString("host", uri.getHost());
            bundle.putInt("port", uri.getPort());
            bundle.putString("path", uri.getPath());
            bundle.putString("query", uri.getQuery());
            bundle.putString("fragment", uri.getFragment());

            // 添加 path segments
            List<String> pathSegments = uri.getPathSegments();
            if (!pathSegments.isEmpty()) {
                bundle.putStringArrayList("pathSegments", new ArrayList<>(pathSegments));
            }

            // 添加 last path segment
            String lastPathSegment = uri.getLastPathSegment();
            if (lastPathSegment != null) {
                bundle.putString("lastPathSegment", lastPathSegment);
            }
        }

        return bundle;
    }

    public static String getCurrentProcessName(Context context) {
        int pid = android.os.Process.myPid();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo processInfo : am.getRunningAppProcesses()) {
            if (processInfo.pid == pid) {
                return processInfo.processName;
            }
        }
        return null;
    }
}
