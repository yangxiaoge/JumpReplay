package com.fourtwo.hookintent.service;

import android.app.ActivityManager;
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
        // 初始化最近活跃包名，避免启动服务后的首次跳转显示 unknown
        initLastResumedPackage();
    }

    // 初始化前台活跃应用包名
    private void initLastResumedPackage() {
        try {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty() && tasks.get(0).topActivity != null) {
                mLastResumedPackage = tasks.get(0).topActivity.getPackageName();
                Log.d(TAG, "初始化前台活跃应用包名为: " + mLastResumedPackage);
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化前台活跃应用包名失败", e);
        }
    }

    // 从最近运行的任务栈中回溯寻找真实的跳转发起方信息（支持跨应用返回包名，应用内部跳转返回发起 Activity 类名）
    private String getCallingPackageFromTasks(Intent intent, String targetPkg) {
        try {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);
            if (tasks != null) {
                String targetClassName = (intent != null && intent.getComponent() != null) 
                        ? intent.getComponent().getClassName() : "";
                        
                for (ActivityManager.RunningTaskInfo task : tasks) {
                    if (task.topActivity != null) {
                        String pkgName = task.topActivity.getPackageName();
                        String className = task.topActivity.getClassName();
                        
                        // 1. 忽略我们拦截器本尊的包名
                        if (pkgName.equals(mContext.getPackageName())) {
                            continue;
                        }
                        
                        // 2. 如果包名与目标包名不同，说明是跨应用跳转，直接返回该发起包名
                        if (!pkgName.equals(targetPkg)) {
                            return pkgName;
                        }
                        
                        // 3. 如果是应用内跳转（包名相同）
                        // 3.1. 如果当前栈顶已经是目标类名，说明新 Activity 已是栈顶但仍在同一个任务中，此时通过根 Activity (baseActivity) 获取发起方 MainActivity
                        if (className.equals(targetClassName)) {
                            if (task.baseActivity != null) {
                                return task.baseActivity.getClassName();
                            }
                        } else {
                            // 3.2. 如果栈顶类名不等于目标类名，说明栈顶即为同包发起 Activity
                            return className;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "从任务栈中回溯获取发起方信息失败", e);
        }
        return "unknown";
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

        Log.i(TAG, "监听到应用跳转: 目标应用 = " + pkg + ", Intent = " + intent + ", 是否包含 extras: " + (intent.getExtras() != null));
        try {
            Log.d(TAG, "诊断 - Intent 完整 URI (包含 extras): " + intent.toUri(Intent.URI_INTENT_SCHEME));
        } catch (Exception e) {
            Log.e(TAG, "诊断 - 获取 Intent URI 异常", e);
        }
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
    private void recordIntentForReplay(final Intent intent, final String targetPkg) {
        // 开启后台异步线程，避免同步调用 ActivityManager.getRunningTasks 导致 Binder 死锁
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 调试打印：记录此时的任务栈状态，便于后续通过 Logcat 进行物理观察
                    try {
                        ActivityManager debugAm = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                        List<ActivityManager.RunningTaskInfo> debugTasks = debugAm.getRunningTasks(5);
                        if (debugTasks != null) {
                            for (int i = 0; i < debugTasks.size(); i++) {
                                ActivityManager.RunningTaskInfo t = debugTasks.get(i);
                                Log.d(TAG, "调试任务栈 [" + i + "]: top=" + t.topActivity + ", base=" + t.baseActivity + ", numActivities=" + t.numActivities);
                            }
                        }
                    } catch (Exception ignored) {}

                    // 1. 尝试从系统最近运行任务栈回溯发起包名/Activity类名
                    String fromPkg = getCallingPackageFromTasks(intent, targetPkg);
                    
                    // 2. 如果回溯结果无效，则使用 mLastResumedPackage 做为兜底
                    if ("unknown".equals(fromPkg) || fromPkg.isEmpty()) {
                        fromPkg = mLastResumedPackage;
                    }
                    
                    // 2.1. 强力校验：如果前台是目标应用本身，但回溯结果却是外部包（例如 Launcher），说明回溯越界误判，强制修正为 mLastResumedPackage 或者是 targetPkg
                    if (mLastResumedPackage != null && mLastResumedPackage.equals(targetPkg)) {
                        if (fromPkg != null && !fromPkg.startsWith(targetPkg)) {
                            Log.w(TAG, "回溯包名越界误判: " + fromPkg + ", 强制修正为前台同包应用: " + mLastResumedPackage);
                            fromPkg = mLastResumedPackage;
                        }
                    }

                    // 转换为 URI 串
                    String uri = intent.toUri(Intent.URI_INTENT_SCHEME);
                    
                    // 复用原本 Xposed 传回的 Bundle 数据格式
                    Bundle bundle = DataConverter.convertIntentToBundle(intent, mContext, targetPkg);
                    
                    bundle.putString("category", intent.getDataString() != null ? "Scheme" : "Intent");
                    if (intent.getDataString() != null) {
                        // 补充 scheme_raw_url 字段，防止被数据处理器过滤，同时供详情页解析与复制
                        bundle.putString("scheme_raw_url", intent.getDataString());
                    }
                    bundle.putString("FunctionCall", inferFunctionCall(intent));
                    bundle.putString("uri", uri);
                    bundle.putString("packageName", targetPkg);
                    
                    // 补全进程名（如果是应用内部跳转，发起进程名为目标包名；如果是跨包跳转，发起进程名为发起包名）
                    String processName = fromPkg;
                    if (fromPkg.startsWith(targetPkg)) {
                        processName = targetPkg;
                    }
                    bundle.putString("processName", processName);
                    
                    // 补充调用栈占位符，说明免 Xposed 系统级拦截的原理
                    bundle.putString("stack_trace", "系统签名版拦截 (免 Xposed)\n拦截机制: IActivityController.activityStarting\n发起包名: " + fromPkg + "\n目标包名: " + targetPkg);
                    bundle.putString("time", new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault()).format(Calendar.getInstance().getTime()));
                    bundle.putString("from", fromPkg);

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
                    Log.e(TAG, "recordIntentForReplay 异步记录异常", e);
                }
            }
        }).start();
    }

    // 根据 Intent 的特征智能推断最契合的 FunctionCall 经典方法名
    private String inferFunctionCall(Intent intent) {
        String dataString = intent.getDataString();
        if (dataString != null) {
            // 如果是 Scheme 跳转，且以 intent: 形式的 URI 开头
            if (dataString.startsWith("intent:#Intent")) {
                return "Intent.parseUri";
            }
            return "Uri.parse";
        } else {
            // 默认归类为最常见的 Activity.startActivity
            return "Activity.startActivity";
        }
    }

    /**
     * 判断当前 Intent 是否触发 Blocker 拦截规则
     */
    private boolean shouldInterceptIntent(Intent intent) {
        try {
            // 从 SharedPreferences 读取已配置的禁用 Scheme 列表
            String disabledScheme = SharedPreferencesUtils.getStr(mContext, Constants.DISABLED_SCHEME);
            if (disabledScheme == null || disabledScheme.trim().isEmpty()) {
                return false;
            }

            String dataString = intent.getDataString();
            String scheme = intent.getScheme();
            String intentUri = intent.toUri(Intent.URI_INTENT_SCHEME);

            List<Map<String, Object>> disabledSchemeList = JsonHandler.deserializeHookedRecords(disabledScheme);
            for (Map<String, Object> rule : disabledSchemeList) {
                Boolean open = (Boolean) rule.get("open");
                Boolean re = (Boolean) rule.get("re");
                String text = (String) rule.get("text");

                if (!Boolean.TRUE.equals(open) || text == null || text.trim().isEmpty()) {
                    continue;
                }

                if (Boolean.TRUE.equals(re)) {
                    // 正则模式匹配：支持对 dataString 或完整的 intentUri 进行正则匹配
                    try {
                        Pattern pattern = Pattern.compile(text);
                        // 优先对完整的 intentUri 进行匹配（能够覆盖 component, action, package 等）
                        Matcher matcherUri = pattern.matcher(intentUri);
                        if (matcherUri.find()) {
                            Log.i(TAG, "正则拦截规则命中 (URI): pattern = " + text + ", intentUri = " + intentUri);
                            return true;
                        }
                        // 兼容原本对 dataString 的正则匹配
                        if (dataString != null) {
                            Matcher matcherData = pattern.matcher(dataString);
                            if (matcherData.find()) {
                                Log.i(TAG, "正则拦截规则命中 (Data): pattern = " + text + ", data = " + dataString);
                                return true;
                            }
                        }
                    } catch (PatternSyntaxException e) {
                        Log.e(TAG, "正则表达式语法错误: " + text, e);
                    }
                } else {
                    // 普通模式匹配：支持协议头比对及完整 Intent URI 的包含/相等匹配
                    // 1. 兼容原本的 Scheme 协议头拦截（如 text = "tbopen", scheme = "tbopen"）
                    if (scheme != null && Objects.equals(text, scheme)) {
                        Log.i(TAG, "协议头拦截规则命中: scheme = " + text);
                        return true;
                    }
                    // 2. 匹配完整的 Intent URI（包含指定的内容，如完整的 intent:#Intent; 字符串，或模糊包名）
                    if (intentUri.contains(text)) {
                        Log.i(TAG, "Intent URI 拦截规则命中: text = " + text + ", intentUri = " + intentUri);
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
        // 打印系统回调日志以方便追踪，并记录最近的活跃应用包名
        Log.d(TAG, "activityResuming 被回调: pkg = " + pkg);
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
