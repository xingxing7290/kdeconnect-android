package org.kde.kdeconnect.plugins.runcommand;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityRunCommandFullscreenBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.Lazy;
import kotlin.LazyKt;

public class FullScreenRunCommandActivity extends BaseActivity<ActivityRunCommandFullscreenBinding> {

    private static final String PREF_KEY_ORDER_PREFIX = "runcommand_order_";

    private final Lazy<ActivityRunCommandFullscreenBinding> lazyBinding = LazyKt.lazy(() -> ActivityRunCommandFullscreenBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    protected ActivityRunCommandFullscreenBinding getBinding() {
        return lazyBinding.getValue();
    }

    private String deviceId;
    private List<CommandEntry> commandItems;

    private SharedPreferences sharedPreferences;
    private CommandEntryAdapter commandAdapter;

    private final RunCommandPlugin.CommandsChangedCallback commandsChangedCallback = () -> runOnUiThread(this::updateView);

    private int calculateSpanCount() {
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return 3;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float widthDp = metrics.widthPixels / metrics.density;

        int desiredItemDp = 120;
        int span = (int) (widthDp / desiredItemDp);
        return Math.max(3, span);
    }

    private List<String> loadSavedOrder() {
        String raw = sharedPreferences.getString(PREF_KEY_ORDER_PREFIX + deviceId, null);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            JSONArray arr = new JSONArray(raw);
            List<String> keys = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                keys.add(arr.getString(i));
            }
            return keys;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    private void saveOrder(List<String> keys) {
        JSONArray arr = new JSONArray();
        for (String k : keys) {
            arr.put(k);
        }
        sharedPreferences.edit().putString(PREF_KEY_ORDER_PREFIX + deviceId, arr.toString()).apply();
    }

    private List<CommandEntry> applySavedOrder(List<CommandEntry> current) {
        List<String> savedOrder = loadSavedOrder();
        Map<String, CommandEntry> map = new HashMap<>();
        for (CommandEntry e : current) {
            map.put(e.getKey(), e);
        }

        List<CommandEntry> result = new ArrayList<>(current.size());
        Set<String> used = new HashSet<>();

        for (String key : savedOrder) {
            CommandEntry e = map.get(key);
            if (e != null) {
                result.add(e);
                used.add(key);
            }
        }

        for (CommandEntry e : current) {
            if (!used.contains(e.getKey())) {
                result.add(e);
            }
        }

        return result;
    }

    private void updateView() {
        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            Log.e("RunCommand", "Plugin is null");
            finish();
            return;
        }

        try {
            commandItems = new ArrayList<>();
            List<CommandInfo> commandList = plugin.getCommandList();

            for (CommandInfo info : commandList) {
                commandItems.add(new CommandEntry(info));
            }
            commandItems.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));

            if (commandItems.isEmpty()) {
                if (commandAdapter != null) {
                    commandAdapter.setCommands(commandItems);
                }
                getBinding().addCommandExplanation.setVisibility(View.VISIBLE);
                return;
            }

            commandItems = applySavedOrder(commandItems);

            runOnUiThread(() -> {
                int spanCount = calculateSpanCount();
                getBinding().runCommandsList.setLayoutManager(new GridLayoutManager(this, spanCount));

                if (commandAdapter == null) {
                    commandAdapter = new CommandEntryAdapter(
                            new ArrayList<>(commandItems),
                            (CommandEntry command) -> {
                                plugin.runCommand(command.getKey());
                                return kotlin.Unit.INSTANCE;
                            },
                            (CommandEntry command) -> {
                                copyCommandToClipboard(command);
                                return kotlin.Unit.INSTANCE;
                            }
                    );
                    getBinding().runCommandsList.setAdapter(commandAdapter);

                    ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                            ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                            0
                    ) {
                        @Override
                        public boolean onMove(@NonNull RecyclerView recyclerView,
                                              @NonNull RecyclerView.ViewHolder viewHolder,
                                              @NonNull RecyclerView.ViewHolder target) {
                            int from = viewHolder.getBindingAdapterPosition();
                            int to = target.getBindingAdapterPosition();
                            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                                return false;
                            }
                            commandAdapter.moveItem(from, to);
                            saveOrder(commandAdapter.getCommandKeys());
                            return true;
                        }

                        @Override
                        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        }
                    });
                    helper.attachToRecyclerView(getBinding().runCommandsList);
                } else {
                    commandAdapter.setCommands(commandItems);
                }

                saveOrder(commandAdapter.getCommandKeys());
                getBinding().addCommandExplanation.setVisibility(View.GONE);
            });

        } catch (Exception e) {
            Log.e("RunCommand", "Error in updateView", e);
            getBinding().addCommandExplanation.setVisibility(View.VISIBLE);
        }
    }

    private void copyCommandToClipboard(CommandEntry command) {
        String url = "kdeconnect://runcommand/" + deviceId + "/" + command.getKey();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Command", url));
        Toast.makeText(this, R.string.clipboard_toast, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        deviceId = getIntent().getStringExtra(RunCommandActivity.EXTRA_DEVICE_ID);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        updateView();
    }

    @Override
    protected void onDestroy() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            finish();
            return;
        }
        plugin.addCommandsUpdatedCallback(commandsChangedCallback);
    }

    @Override
    protected void onPause() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            super.onPause();
            return;
        }
        plugin.removeCommandsUpdatedCallback(commandsChangedCallback);

        super.onPause();
    }
}
