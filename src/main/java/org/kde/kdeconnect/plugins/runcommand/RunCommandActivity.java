/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.runcommand;

import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.KdeConnect;
import org.kde.kdeconnect.base.BaseActivity;
import org.kde.kdeconnect_tp.R;
import org.kde.kdeconnect_tp.databinding.ActivityRunCommandBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kotlin.Lazy;
import kotlin.LazyKt;

public class RunCommandActivity extends BaseActivity<ActivityRunCommandBinding> {

    private static final String PREF_KEY_ORDER_PREFIX = "runcommand_order_";

    private final Lazy<ActivityRunCommandBinding> lazyBinding = LazyKt.lazy(() -> ActivityRunCommandBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    protected ActivityRunCommandBinding getBinding() {
        return lazyBinding.getValue();
    }

    private String deviceId;
    private final RunCommandPlugin.CommandsChangedCallback commandsChangedCallback = () -> runOnUiThread(this::updateView);
    private List<CommandEntry> commandItems;

    private SharedPreferences sharedPreferences;
    private CommandEntryAdapter commandAdapter;

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
            registerForContextMenu(getBinding().runCommandsList);

            commandItems = new ArrayList<>();
            List<JSONObject> commandList = plugin.getCommandList();
            Log.d("RunCommand", "Found " + commandList.size() + " commands");
            
            for (JSONObject obj : commandList) {
                try {
                    CommandEntry entry = new CommandEntry(obj);
                    commandItems.add(entry);
                    Log.d("RunCommand", "Added command: " + entry.getName());
                } catch (JSONException e) {
                    Log.e("RunCommand", "Error parsing command: " + obj.toString(), e);
                }
            }

            if (commandItems.isEmpty()) {
                Log.d("RunCommand", "No commands found, showing explanation");
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
                                Log.d("RunCommand", "Running command: " + command.getName());
                                plugin.runCommand(command.getKey());
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
            getBinding().addCommandExplanation.setText("Error loading commands: " + e.getMessage());
            getBinding().addCommandExplanation.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        deviceId = getIntent().getStringExtra("deviceId");
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Device device = KdeConnect.getInstance().getDevice(deviceId);
        if (device != null) {
            getSupportActionBar().setSubtitle(device.getName());
            RunCommandPlugin plugin = device.getPlugin(RunCommandPlugin.class);
            if (plugin != null) {
                if (plugin.canAddCommand()) {
                    getBinding().addCommandButton.show();
                } else {
                    getBinding().addCommandButton.hide();
                }
                getBinding().addCommandButton.setOnClickListener(v -> {
                    plugin.sendSetupPacket();
                    new AlertDialog.Builder(RunCommandActivity.this)
                            .setTitle(R.string.add_command)
                            .setMessage(R.string.add_command_description)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                });
            }
        }
        updateView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.runcommand_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_fullscreen) {
            Intent intent = new Intent(this, FullScreenRunCommandActivity.class);
            intent.putExtra("deviceId", deviceId);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Context menu handling for RecyclerView
    private int selectedPosition = -1;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.runcommand_context, menu);
        
        // Get the position of the long-pressed item
        View view = getCurrentFocus();
        if (view != null) {
            selectedPosition = getBinding().runCommandsList.getChildAdapterPosition(view);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (selectedPosition == -1) {
            return super.onContextItemSelected(item);
        }
        
        if (item.getItemId() == R.id.copy_url_to_clipboard) {
            CommandEntry entry = commandAdapter != null ? commandAdapter.getCommandAt(selectedPosition) : null;
            if (entry == null) {
                selectedPosition = -1;
                return super.onContextItemSelected(item);
            }
            String url = "kdeconnect://runcommand/" + deviceId + "/" + entry.getKey();
            ClipboardManager cm = ContextCompat.getSystemService(this, ClipboardManager.class);
            cm.setText(url);
            Toast toast = Toast.makeText(this, R.string.clipboard_toast, Toast.LENGTH_SHORT);
            toast.show();
            selectedPosition = -1; // Reset after handling
            return true;
        }
        selectedPosition = -1; // Reset if not handled
        return false;
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
        super.onPause();

        RunCommandPlugin plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin.class);
        if (plugin == null) {
            return;
        }
        plugin.removeCommandsUpdatedCallback(commandsChangedCallback);
    }

    @Override
    public boolean onSupportNavigateUp() {
        super.onBackPressed();
        return true;
    }
}
