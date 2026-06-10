package com.fourtwo.hookintent.manager;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * 权限管理器（系统签名专用版）
 * 彻底剥离了 Shizuku 与 Root (libsu) 的依赖，只保留系统级 API 调用
 */
public class PermissionManager {

    private static final String TAG = "PermissionManager";

    public static boolean isShizukuPermissionGranted = false;
    public static boolean isRootPermissionGranted = false;
    public static boolean isBinderAvailable = false;

    public static void ShizukuInit() {
        // 系统签名版无需 Shizuku 初始化
    }

    public static void ShizukuCleanUp() {
        // 系统签名版无需 Shizuku 清理
    }

    public static void bindRootService(Context context) {
        // 系统签名版无需 Root 服务
    }

    public static void unbindRootService(Context context) {
        // 系统签名版无需 Root 服务
    }

    public static void init(Context context){
        // 系统签名版无需初始化
    }

    public static void unload(Context context){
        // 系统签名版无需卸载
    }

    public static void startActivity(Context context, Intent intent, Boolean isRoot, String SelectedItem) {
        try {
            // 系统签名版已拥有充足特权，直接使用系统 API 启动
            context.startActivity(intent);
            Toast.makeText(context, "调用成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "无法启动新的 Intent: " + e.getMessage(), e);
            Toast.makeText(context, "调用失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void executeCommand(String command, Boolean Root, Context context) {
        Toast.makeText(context, "当前为系统签名版，不支持执行 am 命令行，请使用 schemeUri 或 intentUri 进行回放测试", Toast.LENGTH_LONG).show();
    }
}
