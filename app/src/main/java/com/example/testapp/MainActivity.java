package com.example.testapp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    // UI 组件
    private TextView statusTextView;
    private TextView deviceInfoTextView;
    private TextView logTextView;
    private EditText deviceNameEditText;
    private EditText closeDelayEditText;
    private Button saveButton;
    private Button startServiceButton;
    private Button stopServiceButton;
    private Button clearLogButton;
    
    private SharedPreferences prefs;
    private Handler mainHandler;
    private StringBuilder logBuffer;
    private static final int MAX_LOG_LINES = 200;
    
    // 日志广播接收器
    private BroadcastReceiver logReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mainHandler = new Handler(Looper.getMainLooper());
        logBuffer = new StringBuilder();
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        
        // 初始化 UI 组件
        initViews();
        
        // 加载保存的配置
        loadSettings();
        
        // 显示设备信息
        showDeviceInfo();
        
        // 设置按钮监听
        setupListeners();
        
        // 注册日志广播接收器
        registerLogReceiver();
        
        // 检查并请求权限
        if (!checkPermissions()) {
            requestPermissions();
        } else {
            statusTextView.setText("✅ 权限已授予\n点击'启动服务'开始监控");
            appendLog("权限检查通过");
        }
    }
    
    private void initViews() {
        statusTextView = findViewById(R.id.statusTextView);
        deviceInfoTextView = findViewById(R.id.deviceInfoTextView);
        logTextView = findViewById(R.id.logTextView);
        deviceNameEditText = findViewById(R.id.deviceNameEditText);
        closeDelayEditText = findViewById(R.id.closeDelayEditText);
        saveButton = findViewById(R.id.saveButton);
        startServiceButton = findViewById(R.id.startServiceButton);
        stopServiceButton = findViewById(R.id.stopServiceButton);
        clearLogButton = findViewById(R.id.clearLogButton);
    }
    
    private void loadSettings() {
        String deviceName = prefs.getString("target_device_name", "SYNC");
        long closeDelay = prefs.getLong("hotspot_close_delay", 60000);
        
        deviceNameEditText.setText(deviceName);
        closeDelayEditText.setText(String.valueOf(closeDelay / 1000));
    }
    
    private void showDeviceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("制造商：").append(Build.MANUFACTURER).append("\n");
        info.append("型号：").append(Build.MODEL).append("\n");
        info.append("Android: ").append(Build.VERSION.RELEASE);
        info.append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        info.append("Display: ").append(Build.DISPLAY);
        
        deviceInfoTextView.setText(info.toString());
        appendLog("设备信息：");
        appendLog("  制造商：" + Build.MANUFACTURER);
        appendLog("  型号：" + Build.MODEL);
        appendLog("  Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
    }
    
    private void setupListeners() {
        saveButton.setOnClickListener(v -> saveSettings());
        startServiceButton.setOnClickListener(v -> startMonitorService());
        stopServiceButton.setOnClickListener(v -> stopMonitorService());
        clearLogButton.setOnClickListener(v -> clearLog());
    }
    
    private void registerLogReceiver() {
        logReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String logMessage = intent.getStringExtra("log_message");
                String logLevel = intent.getStringExtra("log_level");
                if (logMessage != null) {
                    mainHandler.post(() -> appendLog(logMessage, logLevel));
                }
            }
        };
        
        IntentFilter filter = new IntentFilter("com.example.testapp.LOG_MESSAGE");
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, filter);
    }
    
    public void appendLog(String message) {
        appendLog(message, "INFO");
    }
    
    public void appendLog(String message, String level) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logLine = String.format("[%s] %-6s %s", timestamp, level, message);
        
        logBuffer.append(logLine).append("\n");
        
        // 限制日志行数
        String[] lines = logBuffer.toString().split("\n");
        if (lines.length > MAX_LOG_LINES) {
            int start = lines.length - MAX_LOG_LINES;
            logBuffer = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                logBuffer.append(lines[i]).append("\n");
            }
        }
        
        logTextView.setText(logBuffer.toString());
        
        // 自动滚动到底部
        mainHandler.postDelayed(() -> {
            int scrollAmount = logTextView.getLayout().getLineTop(logTextView.getLineCount()) 
                - logTextView.getHeight();
            if (scrollAmount > 0) {
                logTextView.scrollTo(0, scrollAmount);
            }
        }, 100);
    }
    
    private void clearLog() {
        logBuffer.setLength(0);
        logTextView.setText("日志已清空");
        appendLog("日志已手动清空");
    }
    
    private void saveSettings() {
        String deviceName = deviceNameEditText.getText().toString().trim();
        String delayStr = closeDelayEditText.getText().toString().trim();
        
        if (deviceName.isEmpty()) {
            Toast.makeText(this, "设备名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int delaySeconds;
        try {
            delaySeconds = Integer.parseInt(delayStr);
            if (delaySeconds < 5) {
                Toast.makeText(this, "延迟时间至少 5 秒", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            return;
        }
        
        prefs.edit()
            .putString("target_device_name", deviceName)
            .putLong("hotspot_close_delay", delaySeconds * 1000L)
            .apply();
        
        String msg = "✅ 设置已保存\n设备：" + deviceName + " | 延迟：" + delaySeconds + "秒";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        appendLog("设置已保存 - 设备：" + deviceName + ", 延迟：" + delaySeconds + "秒");
        
        Log.d(TAG, "设置已保存 - 设备：" + deviceName + ", 延迟：" + delaySeconds + "秒");
    }
    
    private void startMonitorService() {
        // 先保存设置
        saveSettings();
        
        Intent serviceIntent = new Intent(this, BluetoothMonitorService.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        String deviceName = deviceNameEditText.getText().toString().trim();
        statusTextView.setText("✅ 监控服务已启动\n设备：" + deviceName);
        Toast.makeText(this, "后台监控服务已启动", Toast.LENGTH_SHORT).show();
        appendLog("监控服务已启动");
        appendLog("目标设备：" + deviceName);
    }
    
    private void stopMonitorService() {
        Intent serviceIntent = new Intent(this, BluetoothMonitorService.class);
        stopService(serviceIntent);
        
        statusTextView.setText("⏹️ 监控服务已停止");
        Toast.makeText(this, "后台监控服务已停止", Toast.LENGTH_SHORT).show();
        appendLog("监控服务已停止");
    }
    
    private boolean checkPermissions() {
        // Android 12+ (API 31+) 需要新的蓝牙权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        
        // 旧版本需要蓝牙和位置权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) 
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) 
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        
        // WiFi 热点需要 CHANGE_WIFI_STATE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) 
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        
        // 位置权限（蓝牙扫描需要）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        
        // 前台服务需要通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        
        return true;
    }
    
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            }, PERMISSION_REQUEST_CODE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                statusTextView.setText("✅ 权限已授予\n点击'启动服务'开始监控");
                appendLog("所有权限已授予");
            } else {
                statusTextView.setText("❌ 部分权限被拒绝\n无法使用蓝牙和热点功能");
                Toast.makeText(this, "需要所有权限才能正常工作", Toast.LENGTH_LONG).show();
                appendLog("❌ 部分权限被拒绝", "ERROR");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver);
        }
        // 注意：不自动停止服务，让用户手动控制
    }
}
