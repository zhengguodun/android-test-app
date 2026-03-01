package com.example.testapp;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String TARGET_DEVICE_NAME = "SYNC";
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    private TextView statusTextView;
    private BluetoothAdapter bluetoothAdapter;
    private WifiManager wifiManager;
    private boolean isConnectedToSync = false;
    private boolean isHotspotEnabled = false;
    
    // 蓝牙配置文件代理
    private BluetoothProfile.ServiceListener bluetoothServiceListener;
    private android.bluetooth.BluetoothProfile bluetoothProfile;

    // 蓝牙状态广播接收器
    private BroadcastReceiver bluetoothReceiver;
    
    // 华为/荣耀设备检测
    private boolean isHuaweiDevice() {
        return Build.MANUFACTURER.equalsIgnoreCase("HUAWEI") || 
               Build.MANUFACTURER.equalsIgnoreCase("HONOR");
    }
    
    private boolean isHarmonyOS() {
        // HarmonyOS 4.2.0 可能不会在 Build.VERSION 中明确标识
        // 可以通过华为设备 + 特定系统属性判断
        return isHuaweiDevice();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        statusTextView = findViewById(R.id.textView);
        statusTextView.setText("正在初始化...");
        
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        // 检查并请求权限
        if (!checkPermissions()) {
            requestPermissions();
        } else {
            initBluetoothMonitor();
        }
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
        
        return true;
    }
    
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE
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
                initBluetoothMonitor();
            } else {
                statusTextView.setText("❌ 权限被拒绝，无法使用蓝牙和热点功能");
                Toast.makeText(this, "需要所有权限才能正常工作", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void initBluetoothMonitor() {
        if (bluetoothAdapter == null) {
            statusTextView.setText("❌ 设备不支持蓝牙");
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            statusTextView.setText("⚠️ 蓝牙未开启，请手动开启蓝牙");
            // 请求开启蓝牙
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
            return;
        }
        
        // 注册蓝牙状态广播接收器
        registerBluetoothReceiver();
        
        // 开始扫描设备
        scanForSyncDevice();
        
        statusTextView.setText("🔍 正在搜索 SYNC 设备...");
    }
    
    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        String deviceName = device.getName();
                        Log.d(TAG, "设备连接：" + deviceName);
                        if (TARGET_DEVICE_NAME.equalsIgnoreCase(deviceName)) {
                            onSyncDeviceConnected();
                        }
                    }
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        String deviceName = device.getName();
                        Log.d(TAG, "设备断开：" + deviceName);
                        if (TARGET_DEVICE_NAME.equalsIgnoreCase(deviceName)) {
                            onSyncDeviceDisconnected();
                        }
                    }
                } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 
                                                    BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_OFF) {
                        statusTextView.setText("⚠️ 蓝牙已关闭");
                        isConnectedToSync = false;
                    }
                }
            }
        };
        
        registerReceiver(bluetoothReceiver, filter);
    }
    
    private void scanForSyncDevice() {
        // 检查已配对的设备
        if (bluetoothAdapter.getBondedDevices() != null) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                if (TARGET_DEVICE_NAME.equalsIgnoreCase(device.getName())) {
                    Log.d(TAG, "发现已配对的 SYNC 设备：" + device.getAddress());
                    // 检查是否已连接
                    if (isDeviceConnected(device)) {
                        onSyncDeviceConnected();
                    }
                    break;
                }
            }
        }
    }
    
    private boolean isDeviceConnected(BluetoothDevice device) {
        // 简化检查：实际应该通过 BluetoothProfile 检查
        // 这里使用一个简单的方法
        try {
            Method method = device.getClass().getMethod("isConnected");
            return (Boolean) method.invoke(device);
        } catch (Exception e) {
            Log.e(TAG, "检查连接状态失败", e);
            return false;
        }
    }
    
    private void onSyncDeviceConnected() {
        Log.d(TAG, "SYNC 设备已连接！");
        isConnectedToSync = true;
        statusTextView.setText("✅ SYNC 设备已连接\n正在开启热点...");
        
        // 延迟一下确保连接稳定
        android.os.Handler handler = new android.os.Handler();
        handler.postDelayed(() -> {
            enableHotspot(true);
        }, 2000);
    }
    
    private void onSyncDeviceDisconnected() {
        Log.d(TAG, "SYNC 设备已断开");
        isConnectedToSync = false;
        statusTextView.setText("❌ SYNC 设备已断开\n正在关闭热点...");
        
        // 延迟关闭热点
        android.os.Handler handler = new android.os.Handler();
        handler.postDelayed(() -> {
            enableHotspot(false);
        }, 5000);
    }
    
    private void enableHotspot(boolean enable) {
        Log.d(TAG, (enable ? "开启" : "关闭") + "WiFi 热点");
        
        boolean success = false;
        
        // 尝试多种方法开启热点
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 使用 WifiManager 的 setWifiApEnabled 反射方法
            success = setWifiApEnabled(enable);
        } else {
            // 旧版本
            success = setWifiApEnabledLegacy(enable);
        }
        
        // 华为/荣耀设备特殊处理
        if (!success && isHuaweiDevice()) {
            Log.d(TAG, "尝试华为设备专用方法");
            success = enableHotspotForHuawei(enable);
        }
        
        isHotspotEnabled = enable && success;
        
        runOnUiThread(() -> {
            if (success) {
                String status = enable ? 
                    "✅ 热点已开启" : 
                    "✅ 热点已关闭";
                statusTextView.setText("SYNC 设备：" + 
                    (isConnectedToSync ? "已连接" : "已断开") + "\n" + status);
                Toast.makeText(this, 
                    enable ? "热点已自动开启" : "热点已自动关闭", 
                    Toast.LENGTH_SHORT).show();
            } else {
                statusTextView.setText("❌ 热点操作失败\n可能需要系统权限");
                Toast.makeText(this, 
                    "热点操作失败，请手动设置", 
                    Toast.LENGTH_LONG).show();
            }
        });
    }
    
    // Android 8.0+ 方法
    private boolean setWifiApEnabled(boolean enable) {
        try {
            Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                android.net.wifi.WifiConfiguration.class, boolean.class);
            return (Boolean) method.invoke(wifiManager, null, enable);
        } catch (Exception e) {
            Log.e(TAG, "setWifiApEnabled 失败", e);
            return false;
        }
    }
    
    // 旧版本方法
    private boolean setWifiApEnabledLegacy(boolean enable) {
        try {
            Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                android.net.wifi.WifiConfiguration.class, boolean.class);
            return (Boolean) method.invoke(wifiManager, null, enable);
        } catch (Exception e) {
            Log.e(TAG, "setWifiApEnabledLegacy 失败", e);
            return false;
        }
    }
    
    // 华为设备专用方法（可能需要特殊权限）
    private boolean enableHotspotForHuawei(boolean enable) {
        try {
            // 华为使用 HwWifiManager
            Method getHwWifiManager = wifiManager.getClass().getMethod("getHwWifiManager");
            Object hwWifiManager = getHwWifiManager.invoke(wifiManager);
            
            if (hwWifiManager != null) {
                Method setWifiApEnabled = hwWifiManager.getClass()
                    .getMethod("setWifiApEnabled", 
                        android.net.wifi.WifiConfiguration.class, 
                        boolean.class, 
                        int.class);
                return (Boolean) setWifiApEnabled.invoke(hwWifiManager, null, enable, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "华为专用方法失败", e);
        }
        
        return false;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothReceiver != null) {
            unregisterReceiver(bluetoothReceiver);
        }
    }
}
