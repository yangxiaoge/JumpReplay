package com.fourtwo.hookintent.ui.blocker;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.apksig.ApkVerifier;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fourtwo.hookintent.R;
import com.fourtwo.hookintent.data.Constants;
import com.fourtwo.hookintent.utils.BlockerApkBuilder;
import com.fourtwo.hookintent.utils.ManifestRuleScanner;
import com.fourtwo.hookintent.utils.SharedPreferencesUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class BlockerFragment extends Fragment {

    private static final String DEFAULT_BLOCKER_PACKAGE = "com.fourtwo.hookscheme";
    private static final String DEFAULT_BLOCKER_LABEL = "意图拦截器";
    private static final String CUSTOM_GROUP_PACKAGE = "__custom_scheme__";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    private Button buildButton;

    private final List<BlockerGroup> groups = new ArrayList<>();
    private BlockerExpandableAdapter adapter;

    private String diagnoseBuiltApk(File apkFile) {
        StringBuilder sb = new StringBuilder();

        try {
            PackageManager pm = requireContext().getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(),
                    PackageManager.GET_ACTIVITIES
            );

            if (info == null) {
                sb.append("PackageManager 无法解析该 APK\n");
            } else {
                sb.append("PackageManager 可解析 APK\n");
                sb.append("archive packageName = ").append(info.packageName).append("\n");
                sb.append("versionCode = ").append(info.versionCode).append("\n");
            }
        } catch (Throwable t) {
            sb.append("PackageManager 解析异常: ").append(t.getMessage()).append("\n");
        }

        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(apkFile).build().verify();
            sb.append("签名验证 = ").append(result.isVerified()).append("\n");

            if (!result.isVerified()) {
                if (!result.getErrors().isEmpty()) {
                    sb.append("签名错误:\n");
                    for (ApkVerifier.IssueWithParams error : result.getErrors()) {
                        sb.append("- ").append(error).append("\n");
                    }
                }
            }
        } catch (Throwable t) {
            sb.append("ApkVerifier 异常: ").append(t.getMessage()).append("\n");
        }

        return sb.toString();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_blocker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_blocker);
        progressBar = view.findViewById(R.id.blocker_progress);
        emptyView = view.findViewById(R.id.blocker_empty_view);
        buildButton = view.findViewById(R.id.button_build_blocker);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new BlockerExpandableAdapter(this::updateEmptyState);
        recyclerView.setAdapter(adapter);

        buildButton.setOnClickListener(v -> buildBlocker());

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menu.clear();
                menuInflater.inflate(R.menu.blocker_drawer, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_blocker_add) {
                    showAddSchemeDialog();
                    return true;
                } else if (id == R.id.action_blocker_settings) {
                    showBlockerSettingsDialog();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        scanRules();
    }

    private void scanRules() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(getString(R.string.blocker_loading));

        Executors.newSingleThreadExecutor().execute(() -> {
            List<BlockerGroup> scannedGroups = ManifestRuleScanner.scan(requireContext());
            List<DeepLinkRule> customRules = loadCustomRules();

            BlockerGroup customGroup = new BlockerGroup(
                    CUSTOM_GROUP_PACKAGE,
                    getString(R.string.blocker_custom_group),
                    true,
                    customRules
            );
            customGroup.toggleExpanded();

            List<BlockerGroup> all = new ArrayList<>();
            all.add(customGroup);
            all.addAll(scannedGroups);

            requireActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                groups.clear();
                groups.addAll(all);
                adapter.submit(groups);
                updateEmptyState();
            });
        });
    }

    private void updateEmptyState() {
        boolean hasAnyGroup = !groups.isEmpty();
        if (!hasAnyGroup) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(getString(R.string.blocker_empty));
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void showAddSchemeDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.blocker_input_scheme));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.blocker_add_scheme)
                .setView(input)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String scheme = safe(input.getText().toString());
                    if (scheme.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.blocker_scheme_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    BlockerGroup customGroup = findCustomGroup();
                    if (customGroup == null) {
                        return;
                    }

                    DeepLinkRule newRule = new DeepLinkRule(
                            CUSTOM_GROUP_PACKAGE,
                            getString(R.string.blocker_custom_group),
                            "",
                            scheme,
                            "",
                            "",
                            "",
                            "",
                            ""
                    );
                    newRule.setSelected(true);

                    for (DeepLinkRule rule : customGroup.getRules()) {
                        if (rule.sameAs(newRule)) {
                            Toast.makeText(requireContext(), R.string.blocker_scheme_duplicate, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    customGroup.getRules().add(newRule);
                    saveCustomRules(customGroup.getRules());
                    adapter.submit(groups);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showBlockerSettingsDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_view_blocker, null);

        EditText inputPackage = dialogView.findViewById(R.id.edit_input_1);
        EditText inputLabel = dialogView.findViewById(R.id.edit_input_2);

        inputPackage.setHint(getString(R.string.blocker_package_hint));
        inputLabel.setHint(getString(R.string.blocker_label_hint));

        inputPackage.setText(getBlockerPackage());
        inputLabel.setText(getBlockerLabel());

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.blocker_settings)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String blockerPackage = safe(inputPackage.getText().toString());
                    String blockerLabel = safe(inputLabel.getText().toString());

                    SharedPreferencesUtils.putStr(
                            requireContext(),
                            Constants.BLOCKER_OUTPUT_PACKAGE,
                            blockerPackage.isEmpty() ? DEFAULT_BLOCKER_PACKAGE : blockerPackage
                    );

                    SharedPreferencesUtils.putStr(
                            requireContext(),
                            Constants.BLOCKER_OUTPUT_LABEL,
                            blockerLabel.isEmpty() ? DEFAULT_BLOCKER_LABEL : blockerLabel
                    );

                    Toast.makeText(requireContext(), R.string.blocker_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void buildBlocker() {
        if (!templateExists()) {
            Toast.makeText(requireContext(), R.string.blocker_template_missing, Toast.LENGTH_LONG).show();
            return;
        }

        JSONObject spec;
        try {
            spec = buildSpecJson();
            if (spec == null) {
                Toast.makeText(requireContext(), R.string.blocker_generate_no_rule, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(getString(R.string.blocker_building));
        buildButton.setEnabled(false);

        JSONObject finalSpec = spec;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File blockerDir = new File(requireContext().getFilesDir(), "blocker");
                if (!blockerDir.exists()) {
                    blockerDir.mkdirs();
                }

                File specFile = new File(blockerDir, Constants.BLOCKER_SPEC_FILE_NAME);
                writeString(specFile, finalSpec.toString(2));

                File signedApk = BlockerApkBuilder.build(requireContext(), finalSpec);

                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buildButton.setEnabled(true);
                    emptyView.setVisibility(View.GONE);

                    String diagnose = diagnoseBuiltApk(signedApk);

                    new AlertDialog.Builder(requireContext())
                            .setTitle("构建结果")
                            .setMessage(
                                    getString(R.string.blocker_build_done) + "\n"
                                            + signedApk.getAbsolutePath() + "\n\n"
                                            + diagnose
                            )
                            .setPositiveButton("安装", (dialog, which) -> {
                                try {
                                    BlockerApkBuilder.install(requireContext(), signedApk);
                                } catch (Exception installError) {
                                    Toast.makeText(requireContext(), installError.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });

            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buildButton.setEnabled(true);
                    updateEmptyState();
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Nullable
    private JSONObject buildSpecJson() throws Exception {
        JSONArray rulesArray = new JSONArray();

        for (BlockerGroup group : groups) {
            for (DeepLinkRule rule : group.getRules()) {
                if (rule.isSelected()) {
                    rulesArray.put(rule.toJson());
                }
            }
        }

        if (rulesArray.length() == 0) {
            return null;
        }

        JSONObject root = new JSONObject();
        root.put("blockerPackage", getBlockerPackage());
        root.put("blockerLabel", getBlockerLabel());
        root.put("templateAsset", Constants.BLOCKER_TEMPLATE_ASSET);
        root.put("rules", rulesArray);
        return root;
    }

    private boolean templateExists() {
        try (InputStream ignored = requireContext().getAssets().open(Constants.BLOCKER_TEMPLATE_ASSET)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void copyAssetToFile(String assetPath, File outFile) throws Exception {
        try (InputStream inputStream = requireContext().getAssets().open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        }
    }

    private void writeString(File outFile, String content) throws Exception {
        try (FileOutputStream outputStream = new FileOutputStream(outFile)) {
            outputStream.write(content.getBytes());
            outputStream.flush();
        }
    }

    private BlockerGroup findCustomGroup() {
        for (BlockerGroup group : groups) {
            if (group.isCustom()) {
                return group;
            }
        }
        return null;
    }

    private List<DeepLinkRule> loadCustomRules() {
        List<DeepLinkRule> result = new ArrayList<>();
        String json = SharedPreferencesUtils.getStr(requireContext(), Constants.BLOCKER_CUSTOM_RULES);
        if (TextUtils.isEmpty(json)) {
            return result;
        }

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                DeepLinkRule rule = new DeepLinkRule(
                        CUSTOM_GROUP_PACKAGE,
                        getString(R.string.blocker_custom_group),
                        "",
                        obj.optString("scheme", ""),
                        "",
                        "",
                        "",
                        "",
                        ""
                );
                rule.setSelected(obj.optBoolean("selected", true));
                result.add(rule);
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    private void saveCustomRules(List<DeepLinkRule> rules) {
        try {
            JSONArray array = new JSONArray();
            for (DeepLinkRule rule : rules) {
                JSONObject obj = new JSONObject();
                obj.put("scheme", rule.getScheme());
                obj.put("selected", rule.isSelected());
                array.put(obj);
            }
            SharedPreferencesUtils.putStr(requireContext(), Constants.BLOCKER_CUSTOM_RULES, array.toString());
        } catch (Exception ignored) {
        }
    }

    private String getBlockerPackage() {
        String saved = SharedPreferencesUtils.getStr(requireContext(), Constants.BLOCKER_OUTPUT_PACKAGE);
        return TextUtils.isEmpty(saved) ? DEFAULT_BLOCKER_PACKAGE : saved;
    }

    private String getBlockerLabel() {
        String saved = SharedPreferencesUtils.getStr(requireContext(), Constants.BLOCKER_OUTPUT_LABEL);
        return TextUtils.isEmpty(saved) ? DEFAULT_BLOCKER_LABEL : saved;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}