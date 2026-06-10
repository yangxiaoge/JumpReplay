package com.fourtwo.hookintent;

import android.app.Application;
import android.os.Build;

import com.fourtwo.hookintent.manager.PermissionManager;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 开启系统隐藏API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }

        // 初始化权限状态
        PermissionManager.init(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        PermissionManager.unload(this); // 清理资源
    }

}
