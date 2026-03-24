package com.fourtwo.blockertemplate;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SelfManifestUtils {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private SelfManifestUtils() {
    }

    public static List<String> getHandledSchemes(Context context) {
        Set<String> result = new LinkedHashSet<>();
        try {
            String sourceDir = context.getApplicationInfo().sourceDir;

            AssetManager assetManager = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
            int cookie = (int) addAssetPath.invoke(assetManager, sourceDir);

            Method openXml = AssetManager.class.getMethod("openXmlResourceParser", int.class, String.class);
            XmlResourceParser parser = (XmlResourceParser) openXml.invoke(assetManager, cookie, "AndroidManifest.xml");

            boolean inIntentFilter = false;
            boolean hasView = false;
            boolean hasBrowsable = false;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();

                if (eventType == XmlPullParser.START_TAG) {
                    if ("intent-filter".equals(tagName)) {
                        inIntentFilter = true;
                        hasView = false;
                        hasBrowsable = false;
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
                        if (hasView && hasBrowsable) {
                            String scheme = parser.getAttributeValue(ANDROID_NS, "scheme");
                            if (scheme != null && !scheme.trim().isEmpty()) {
                                String normalized = scheme.trim();
                                if (!normalized.startsWith("zzblockunused")
                                        && !normalized.startsWith("blockslot")) {
                                    result.add(normalized);
                                }
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("intent-filter".equals(tagName)) {
                        inIntentFilter = false;
                        hasView = false;
                        hasBrowsable = false;
                    }
                }

                eventType = parser.next();
            }

            parser.close();
        } catch (Throwable ignored) {
        }

        return new ArrayList<>(result);
    }
}