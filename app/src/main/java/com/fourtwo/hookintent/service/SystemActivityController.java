package com.fourtwo.hookintent.service;

import android.app.IActivityController;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.fourtwo.hookintent.IntentIntercept;
import com.fourtwo.hookintent.base.DataConverter;
import com.fourtwo.hookintent.base.JsonHandler;
import com.fourtwo.hookintent.data.Constants;
import com.fourtwo.hookintent.manager.HookStatusManager;
import com.fourtwo.hookintent.utils.SharedPreferencesUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 系统级别的 Activity 控制器实现
 * 代替 Xposed 捕获并拦截系统的 Activity 启动
 */
public class SystemActivityController extends IActivityController.Stub {
    private static final String TAG = "SystemActivityController";
    private final Context mContext;
    // 记录最近一个进入 Resumed 状态的包名，作为跳转的发起方 (from)
    private volatile String mLastResumedPackage = "unknown";

    public SystemActivityController(Context context) {
        this.mContext = context.getApplicationContext();
    }

    @Override
    public boolean activityStarting(Intent intent, String pkg) throws RemoteException {
        if (intent == null) {
            return true;
        }

        // 1. 过滤掉本应用自身的跳转，避免无限循环
        if (mContext.getPackageName().equals(pkg)) {
            return true;
        }

        // 2. 检查监听开关状态
        if (!HookStatusManager.isHook()) {
            return true;
        }

        Log.d(TAG, "监听到应用跳转: 目标应用 = " + pkg + ", Intent = " + intent);

        // 3. 将捕获到的 Intent 记录并发送给 UI 刷新
        recordIntentForReplay(intent, pkg);

        // 4. 检查跳转拦截规则 (Blocker)
        if (shouldInterceptIntent(intent)) {
            Log.w(TAG, "拦截规则命中，阻止跳转并拉起中转确认界面！");
            
            // 拉起二次确认中转界面
            try {
                Intent interceptIntent = new Intent(mContext, IntentIntercept.class);
                interceptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // 使用原 Intent 序列化的 Scheme 字符串传递给确认页面
                interceptIntent.putExtra("DetailData", intent.toUri(Intent.URI_INTENT_SCHEME));
                mContext.startActivity(interceptIntent);
            } catch (Exception e) {
                Log.e(TAG, "拉起 IntentIntercept 失败", e);
            }

            // 返回 false 代表阻止该 Activity 启动
            return false;
        }

        return true;
    }

    /**
     * 将捕获到的 Intent 记录在 MessengerService 中以渲染在列表中
     */
    private void recordIntentForReplay(Intent intent, String targetPkg) {
        try {
            // 转换为 URI 串
            String uri = intent.toUri(Intent.URI_INTENT_SCHEME);
            
            // 复用原本 Xposed 传回的 Bundle 数据格式
            Bundle bundle = DataConverter.convertIntentToBundle(intent);
            
            bundle.putString("category", intent.getDataString() != null ? "Scheme" : "Intent");
            bundle.putString("FunctionCall", "IActivityController.activityStarting");
            bundle.putString("uri", uri);
            bundle.putString("packageName", targetPkg);
            bundle.putString("time", new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault()).format(Calendar.getInstance().getTime()));
            bundle.putString("from", mLastResumedPackage);

            // 塞入 MessengerService 的队列中，UI 挂载的 LiveData 观察者会自动读取渲染
            MessengerService messengerService = MessengerService.getInstance();
            if (messengerService != null) {
                messengerService.getDataQueue().add(bundle);
                MessengerService.liveDataTrigger.postValue(true);
                Log.d(TAG, "跳转记录已塞入数据队列，通知 UI 更新");
            } else {
                Log.w(TAG, "MessengerService 未处于运行状态，无法刷新跳转列表");
            }
        } catch (Exception e) {
            Log.e(TAG, "recordIntentForReplay 异常", e);
        }
    }

    /**
     * 判断当前 Intent 是否触发 Blocker 拦截规则
     */
    private boolean shouldInterceptIntent(Intent intent) {
        String dataString = intent.getDataString();
        String scheme = intent.getScheme();

        // 如果没有 Scheme 信息，则无需进行 Scheme 规则校验
        if (dataString == null && scheme == null) {
            return false;
        }

        try {
            // 从 SharedPreferences 读取已配置的禁用 Scheme 列表
            String disabledScheme = SharedPreferencesUtils.getStr(mContext, Constants.DISABLED_SCHEME);
            if (disabledScheme == null || disabledScheme.trim().isEmpty()) {
                return false;
            }

            List<Map<String, Object>> disabledSchemeList = JsonHandler.deserializeHookedRecords(disabledScheme);
            for (Map<String, Object> rule : disabledSchemeList) {
                Boolean open = (Boolean) rule.get("open");
                Boolean re = (Boolean) rule.get("re");
                String text = (String) rule.get("text");

                if (!Boolean.TRUE.equals(open) || text == null || text.trim().isEmpty()) {
                    continue;
                }

                if (Boolean.TRUE.equals(re)) {
                    // 正则模式匹配
                    if (dataString != null) {
                        try {
                            Pattern pattern = Pattern.compile(text);
                            Matcher matcher = pattern.matcher(dataString);
                            if (matcher.find()) {
                                Log.i(TAG, "正则拦截规则命中: pattern = " + text + ", data = " + dataString);
                                return true;
                            }
                        } catch (PatternSyntaxException e) {
                            Log.e(TAG, "正则表达式语法错误: " + text, e);
                        }
                    }
                } else {
                    // 普通模式匹配 Scheme 协议头
                    if (Objects.equals(text, scheme)) {
                        Log.i(TAG, "协议头拦截规则命中: scheme = " + text);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "判定拦截规则异常", e);
        }

        return false;
    }

    @Override
    public boolean activityResuming(String pkg) throws RemoteException {
        // 记录当前处于前台（Resumed）的包名，作为后续可能发生的跳转的发起方
        mLastResumedPackage = pkg;
        return true;
    }

    @Override
    public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg, long timeMillis, String stackTrace) throws RemoteException {
        return false;
    }

    @Override
    public int appEarlyNotResponding(String processName, int pid, String annotation) throws RemoteException {
        return 0;
    }

    @Override
    public int appNotResponding(String processName, int pid, String processStats) throws RemoteException {
        return 0;
    }

    @Override
    public int systemNotResponding(String msg) throws RemoteException {
        return 0;
    }
}
