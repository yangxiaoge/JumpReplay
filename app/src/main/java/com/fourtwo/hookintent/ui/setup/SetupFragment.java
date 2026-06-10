package com.fourtwo.hookintent.ui.setup;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.fourtwo.hookintent.MainActivity;
import com.fourtwo.hookintent.R;
import com.fourtwo.hookintent.base.JsonHandler;
import com.fourtwo.hookintent.data.Constants;
import com.fourtwo.hookintent.manager.PermissionManager;
import com.fourtwo.hookintent.utils.SharedPreferencesUtils;

import java.util.Map;

public class SetupFragment extends Fragment {

    private static final String TAG = "SetupFragment";

    private static final String LANGUAGE_SYSTEM = "system";
    private static final String[] LANGUAGE_TAGS = {"system", "zh-CN", "en", "ja"};

    private Map<String, Boolean> FloatWindowConfig;

    // 将 View 的引用定义为成员变量
    private CardView systemSignatureCard;
    private TextView systemSignatureText;
    private ImageView systemSignatureIcon;
    private TextView systemSignatureDescription;

    @SuppressLint({"SetTextI18n", "DefaultLocale"})
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setup, container, false);
        FloatWindowConfig = JsonHandler.strToBoolean(JsonHandler.jsonToMap(SharedPreferencesUtils.getStr(requireContext(), Constants.FLOAT_WINDOW_CONFIG)));
        Log.d(TAG, "onCreateView: " + JsonHandler.jsonToMap(SharedPreferencesUtils.getStr(requireContext(), Constants.FLOAT_WINDOW_CONFIG)));

        systemSignatureCard = view.findViewById(R.id.card_system_signature_status);
        systemSignatureText = systemSignatureCard.findViewById(R.id.sys_sig_text);
        systemSignatureIcon = systemSignatureCard.findViewById(R.id.sys_sig_icon);
        systemSignatureDescription = systemSignatureCard.findViewById(R.id.sys_sig_description);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menu.clear();
                menuInflater.inflate(R.menu.setup_drawer, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_language) {
                    showLanguageDialog();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        // 确保 UI 更新逻辑在视图创建后调用
        updateSystemSignatureUI();

        com.fourtwo.hookintent.service.SystemMonitorService.serviceStatus.observe(getViewLifecycleOwner(), running -> {
            updateSystemSignatureUI();
        });

        systemSignatureCard.setOnClickListener(v -> {
            boolean isRunning = com.fourtwo.hookintent.service.SystemMonitorService.isRunning();
            if (!isRunning) {
                Toast.makeText(requireContext(), "请返回首页，点击右下角按钮启动监听服务", Toast.LENGTH_SHORT).show();
            }
        });


        // 获取主开关和子开关
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch mainSwitch = view.findViewById(R.id.float_window_main_switch);
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch subSwitch = view.findViewById(R.id.float_window_sub_switch);

        mainSwitch.setChecked(Boolean.TRUE.equals(FloatWindowConfig.get("float_window")));
        if (mainSwitch.isChecked()) {
            subSwitch.setEnabled(true);
        }
        subSwitch.setChecked(Boolean.TRUE.equals(FloatWindowConfig.get("my_float_window")));

        // 设置主开关监听器
        mainSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 根据主开关状态启用或禁用子开关
            subSwitch.setEnabled(isChecked);
            FloatWindowConfig.put("float_window", isChecked);
            SharedPreferencesUtils.putStr(requireContext(), Constants.FLOAT_WINDOW_CONFIG, JsonHandler.mapToJson(JsonHandler.booleanToStr(FloatWindowConfig)));
        });

        subSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FloatWindowConfig.put("my_float_window", isChecked);
            SharedPreferencesUtils.putStr(requireContext(), Constants.FLOAT_WINDOW_CONFIG, JsonHandler.mapToJson(JsonHandler.booleanToStr(FloatWindowConfig)));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private void showLanguageDialog() {
        String[] labels = new String[]{
                getString(R.string.lang_follow_system),
                getString(R.string.lang_simplified_chinese),
                getString(R.string.lang_english),
                getString(R.string.lang_japan),
        };

        int checkedItem = findLanguageIndex(getCurrentLanguageTag());

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_select_language)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    applyLanguage(LANGUAGE_TAGS[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String getCurrentLanguageTag() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales == null || locales.isEmpty()) {
            return LANGUAGE_SYSTEM;
        }

        String tag = locales.toLanguageTags();
        if (tag == null || tag.isEmpty()) {
            return LANGUAGE_SYSTEM;
        }

        if (tag.startsWith("zh")) {
            return "zh-CN";
        }
        if (tag.startsWith("en")) {
            return "en";
        }
        return LANGUAGE_SYSTEM;
    }

    private int findLanguageIndex(String tag) {
        for (int i = 0; i < LANGUAGE_TAGS.length; i++) {
            if (LANGUAGE_TAGS[i].equals(tag)) {
                return i;
            }
        }
        return 0;
    }

    private void applyLanguage(String tag) {
        if (LANGUAGE_SYSTEM.equals(tag)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateSystemSignatureUI() {
        boolean isRunning = com.fourtwo.hookintent.service.SystemMonitorService.isRunning();
        if (isRunning) {
            systemSignatureText.setText("系统签名监听服务已激活");
            systemSignatureIcon.setImageResource(R.drawable.baseline_check_circle_outline_24);
            systemSignatureCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_green));
            systemSignatureDescription.setText("免 Root/Xposed/Shizuku 运行中");
        } else {
            systemSignatureText.setText("系统签名监听服务未启动");
            systemSignatureIcon.setImageResource(R.drawable.outline_cancel_24);
            systemSignatureCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_red));
            systemSignatureDescription.setText("请返回首页开启监听");
        }
    }
}
