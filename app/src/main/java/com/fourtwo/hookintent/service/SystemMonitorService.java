package com.fourtwo.hookintent.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.lifecycle.MutableLiveData;

/**
 * 监听服务，使用前台服务以常驻后台
 * 注册系统级别的 Activity 拦截控制器 (IActivityController)
 */
public class SystemMonitorService extends Service {
    private static final String TAG = "SystemMonitorService";
    private static final String CHANNEL_ID = "System_Monitor_Channel";
    private static final int NOTIFICATION_ID = 1024;

    private Object amInstance;
    private static boolean isRunning = false;
    public static final MutableLiveData<Boolean> serviceStatus = new MutableLiveData<>(false);

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务 onCreate -> 正在启动前台系统跳转监听服务");
        
        // 显式启动 MessengerService 以接收跳转数据并渲染列表
        try {
            Intent messengerIntent = new Intent(this, MessengerService.class);
            startService(messengerIntent);
        } catch (Exception e) {
            Log.e(TAG, "启动 MessengerService 失败", e);
        }
        
        // 1. 反射初始化 ActivityManager
        initActivityManager();
        
        // 2. 启动前台服务保活，防止后台冻结
        startForegroundServiceCompat();
        
        // 3. 注册全局 Activity 监听拦截器
        registerActivityController();
        isRunning = true;
        serviceStatus.postValue(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务 onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "服务 onDestroy -> 正在注销前台监听服务");
        // 安全注销全局拦截器
        unregisterActivityController();
        isRunning = false;
        serviceStatus.postValue(false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 构建前台通知（兼容 Android 8.0+ 及 Android 14+ 要求的特权类型）
     */
    private void startForegroundServiceCompat() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "系统跳转监听",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持 JumpReplay 能够常驻后台监听跳转意图");
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("JumpReplay 运行中")
                .setContentText("系统签名版跳转监听已开启")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(Notification.PRIORITY_LOW);

        // 如果应用是系统 UID，可以直接设置 persistent 或使用 systemExempted 前台服务类型
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        } else {
            startForeground(NOTIFICATION_ID, builder.build());
        }
    }

    /**
     * 反射获取 IActivityManager 实例
     */
    private void initActivityManager() {
        try {
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            amInstance = amClass.getMethod("getService").invoke(null);
        } catch (Exception e) {
            Log.e(TAG, "反射获取 IActivityManager 实例失败", e);
        }
    }

    /**
     * 注册全局 Activity 拦截器
     */
    private void registerActivityController() {
        if (amInstance == null) {
            Log.e(TAG, "IActivityManager 为空，注册拦截器终止");
            return;
        }
        try {
            Class<?> iamClass = Class.forName("android.app.IActivityManager");
            java.lang.reflect.Method setMethod = iamClass.getMethod("setActivityController",
                    Class.forName("android.app.IActivityController"), boolean.class);
            
            // 实例化并反射注册我们的拦截器
            setMethod.invoke(amInstance, new SystemActivityController(this), false);
            Log.i(TAG, "IActivityController 注册成功");
        } catch (Exception e) {
            Log.e(TAG, "IActivityController 注册失败 (Requires android.permission.SET_ACTIVITY_WATCHER)", e);
        }
    }

    /**
     * 注销全局 Activity 拦截器
     */
    private void unregisterActivityController() {
        if (amInstance == null) return;
        try {
            Class<?> iamClass = Class.forName("android.app.IActivityManager");
            java.lang.reflect.Method setMethod = iamClass.getMethod("setActivityController",
                    Class.forName("android.app.IActivityController"), boolean.class);
            
            // 传入 null 以取消注册
            setMethod.invoke(amInstance, null, false);
            Log.i(TAG, "IActivityController 注销成功");
        } catch (Exception e) {
            Log.e(TAG, "IActivityController 注销失败", e);
        }
    }
}
