package com.example.testapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private TextView statusTextView;
    private EditText deviceNameEditText;
    private EditText closeDelayEditText;
    private Button saveButton;
    private Button startServiceButton;
    private Button stopServiceButton;
    
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        
        statusTextView = findViewById(R.id.textView);
        deviceNameEditText = findViewById(R.id.deviceNameEditText);
        closeDelayEditText = findViewById(R.id.closeDelayEditText);
        saveButton = findViewById(R.id.saveButton);
        startServiceButton = findViewById(R.id.startServiceButton);
        stopServiceButton = findViewById(R.id.stopServiceButton);
        
        // 加载保存的配置
        loadSettings();
        
        // 设置按钮监听
        saveButton.setOnClickListener(v -> saveSettings());
        startServiceButton.setOnClickListener(v -> startMonitorService());
        stopServiceButton.setOnClickListener(v -> stopMonitorService());
        
        // 检查并请求权限
        if (!checkPermissions()) {
            requestPermissions();
        } else {
            statusTextView.setText("✅ 权限已授予\n点击'启动服务'开始监控");
        }
    }
    
    private void loadSettings() {
        String deviceName = prefs.getString("target_device_name", "SYNC");
        long closeDelay = prefs.getLong("hotspot_close_delay", 60000);
        
        deviceNameEditText.setText(deviceName);
        closeDelayEditText.setText(String.valueOf(closeDelay / 1000));
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
        
        Toast.makeText(this, "✅ 设置已保存\n设备：" + deviceName + " | 延迟：" + delaySeconds + "秒", 
            Toast.LENGTH_LONG).show();
        
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
        
        statusTextView.setText("✅ 监控服务已启动\n设备：" + deviceNameEditText.getText().toString().trim());
        Toast.makeText(this, "后台监控服务已启动", Toast.LENGTH_SHORT).show();
    }
    
    private void stopMonitorService() {
        Intent serviceIntent = new Intent(this, BluetoothMonitorService.class);
        stopService(serviceIntent);
        
        statusTextView.setText("⏹️ 监控服务已停止");
        Toast.makeText(this, "后台监控服务已停止", Toast.LENGTH_SHORT).show();
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
            } else {
                statusTextView.setText("❌ 部分权限被拒绝\n无法使用蓝牙和热点功能");
                Toast.makeText(this, "需要所有权限才能正常工作", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注意：不自动停止服务，让用户手动控制
    }
}
