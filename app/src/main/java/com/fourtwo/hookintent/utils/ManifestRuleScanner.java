package com.fourtwo.hookintent.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;

import com.fourtwo.hookintent.ui.blocker.BlockerGroup;
import com.fourtwo.hookintent.ui.blocker.DeepLinkRule;

import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ManifestRuleScanner {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public static List<BlockerGroup> scan(Context context) {
        List<BlockerGroup> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        List<ApplicationInfo> applications = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo appInfo : applications) {
            List<DeepLinkRule> rules = parseRulesFromApk(context, appInfo);
            if (!rules.isEmpty()) {
                String appName = String.valueOf(pm.getApplicationLabel(appInfo));
                result.add(new BlockerGroup(appInfo.packageName, appName, false, rules));
            }
        }

        result.sort(Comparator.comparing(BlockerGroup::getAppName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static List<DeepLinkRule> parseRulesFromApk(Context context, ApplicationInfo appInfo) {
        List<DeepLinkRule> rules = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();

        try {
            AssetManager assetManager = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            int cookie = (int) addAssetPath.invoke(assetManager, appInfo.sourceDir);

            Method openXml = AssetManager.class.getMethod("openXmlResourceParser", int.class, String.class);
            XmlResourceParser parser = (XmlResourceParser) openXml.invoke(assetManager, cookie, "AndroidManifest.xml");

            String currentActivity = null;
            boolean inIntentFilter = false;
            boolean hasView = false;
            boolean hasBrowsable = false;
            List<DataTag> dataTags = new ArrayList<>();

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();

                if (eventType == XmlPullParser.START_TAG) {
                    if ("activity".equals(tagName) || "activity-alias".equals(tagName)) {
                        currentActivity = normalizeClassName(appInfo.packageName,
                                parser.getAttributeValue(ANDROID_NS, "name"));
                    } else if ("intent-filter".equals(tagName)) {
                        inIntentFilter = true;
                        hasView = false;
                        hasBrowsable = false;
                        dataTags.clear();
                    } else if (inIntentFilter && "action".equals(tagName)) {
                        String action = parser.getAttributeValue(ANDROID_NS, "name");
                        if ("android.intent.action.VIEW".equals(action)) {
                            hasView = true;
                        }
                    } else if (inIntentFilter && "category".equals(tagName)) {
                        String category = parser.getAttributeValue(ANDROID_NS, "name");
                        if ("android.intent.category.BROWSABLE".equals(category)) {
                            hasBrowsable = true;
                        }
                    } else if (inIntentFilter && "data".equals(tagName)) {
                        DataTag dataTag = new DataTag();
                        dataTag.scheme = value(parser, "scheme");
                        dataTag.host = value(parser, "host");
                        dataTag.port = value(parser, "port");
                        dataTag.path = value(parser, "path");
                        dataTag.pathPrefix = value(parser, "pathPrefix");
                        dataTag.pathPattern = value(parser, "pathPattern");
                        if (!dataTag.scheme.isEmpty()) {
                            dataTags.add(dataTag);
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("intent-filter".equals(tagName)) {
                        if (currentActivity != null && hasView && hasBrowsable) {
                            String appName = String.valueOf(context.getPackageManager().getApplicationLabel(appInfo));
                            for (DataTag dataTag : dataTags) {
                                DeepLinkRule rule = new DeepLinkRule(
                                        appInfo.packageName,
                                        appName,
                                        currentActivity,
                                        dataTag.scheme,
                                        dataTag.host,
                                        dataTag.port,
                                        dataTag.path,
                                        dataTag.pathPrefix,
                                        dataTag.pathPattern
                                );

                                String key = rule.getPackageName() + "|" + rule.getActivityName() + "|" +
                                        rule.getScheme() + "|" + rule.getHost() + "|" + rule.getPort() + "|" +
                                        rule.getPath() + "|" + rule.getPathPrefix() + "|" + rule.getPathPattern();

                                if (!dedupe.contains(key)) {
                                    dedupe.add(key);
                                    rules.add(rule);
                                }
                            }
                        }
                        inIntentFilter = false;
                        hasView = false;
                        hasBrowsable = false;
                        dataTags.clear();
                    } else if ("activity".equals(tagName) || "activity-alias".equals(tagName)) {
                        currentActivity = null;
                    }
                }

                eventType = parser.next();
            }

            parser.close();
        } catch (Throwable ignored) {
        }

        return rules;
    }

    private static String normalizeClassName(String packageName, String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "";
        }
        if (rawName.startsWith(".")) {
            return packageName + rawName;
        }
        if (!rawName.contains(".")) {
            return packageName + "." + rawName;
        }
        return rawName;
    }

    private static String value(XmlResourceParser parser, String attr) {
        String value = parser.getAttributeValue(ANDROID_NS, attr);
        return value == null ? "" : value;
    }

    private static class DataTag {
        String scheme = "";
        String host = "";
        String port = "";
        String path = "";
        String pathPrefix = "";
        String pathPattern = "";
    }
}