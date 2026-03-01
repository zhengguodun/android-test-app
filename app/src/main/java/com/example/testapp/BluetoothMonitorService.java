package com.example.testapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.lang.reflect.Method;

public class BluetoothMonitorService extends Service {
    private static final String TAG = "BluetoothMonitorService";
    private static final String CHANNEL_ID = "BluetoothMonitorChannel";
    private static final int NOTIFICATION_ID = 1001;
    
    // 默认配置
    private static final String DEFAULT_DEVICE_NAME = "SYNC";
    private static final long HOTSPOT_CLOSE_DELAY_MS = 60000; // 1 分钟
    
    private BluetoothAdapter bluetoothAdapter;
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable hotspotCloseRunnable;
    
    private boolean isConnectedToTarget = false;
    private boolean isHotspotEnabled = false;
    
    // 蓝牙状态广播接收器
    private BroadcastReceiver bluetoothReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务创建");
        
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("正在监控蓝牙设备..."));
        
        registerBluetoothReceiver();
        
        // 延迟检查已连接设备
        new Handler().postDelayed(this::checkConnectedDevices, 2000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "服务启动");
        return START_STICKY; // 服务被杀后自动重启
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "服务销毁");
        
        if (bluetoothReceiver != null) {
            unregisterReceiver(bluetoothReceiver);
        }
        
        if (hotspotCloseRunnable != null) {
            handler.removeCallbacks(hotspotCloseRunnable);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "蓝牙监控服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("持续监控蓝牙设备连接状态");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, 
            notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("蓝牙热点助手")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void updateNotification(String content) {
        Notification notification = createNotification(content);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
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
                        String targetName = getTargetDeviceName();
                        Log.d(TAG, "设备连接：" + deviceName + " (目标：" + targetName + ")");
                        
                        if (targetName.equalsIgnoreCase(deviceName)) {
                            onTargetDeviceConnected();
                        }
                    }
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        String deviceName = device.getName();
                        String targetName = getTargetDeviceName();
                        Log.d(TAG, "设备断开：" + deviceName + " (目标：" + targetName + ")");
                        
                        if (targetName.equalsIgnoreCase(deviceName)) {
                            onTargetDeviceDisconnected();
                        }
                    }
                } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 
                                                    BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_OFF) {
                        Log.d(TAG, "蓝牙已关闭");
                        isConnectedToTarget = false;
                        updateNotification("⚠️ 蓝牙已关闭");
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        Log.d(TAG, "蓝牙已开启，重新扫描设备");
                        checkConnectedDevices();
                    }
                }
            }
        };
        
        registerReceiver(bluetoothReceiver, filter);
    }

    private String getTargetDeviceName() {
        return prefs.getString("target_device_name", DEFAULT_DEVICE_NAME);
    }

    private long getHotspotCloseDelay() {
        return prefs.getLong("hotspot_close_delay", HOTSPOT_CLOSE_DELAY_MS);
    }

    private void checkConnectedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "蓝牙未启用");
            return;
        }
        
        String targetName = getTargetDeviceName();
        Log.d(TAG, "扫描已配对设备，目标：" + targetName);
        
        if (bluetoothAdapter.getBondedDevices() != null) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                if (targetName.equalsIgnoreCase(device.getName())) {
                    Log.d(TAG, "发现目标设备：" + device.getAddress());
                    if (isDeviceConnected(device)) {
                        Log.d(TAG, "设备已连接，触发连接事件");
                        onTargetDeviceConnected();
                    }
                    break;
                }
            }
        }
    }

    private boolean isDeviceConnected(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("isConnected");
            return (Boolean) method.invoke(device);
        } catch (Exception e) {
            Log.e(TAG, "检查连接状态失败", e);
            return false;
        }
    }

    private void onTargetDeviceConnected() {
        Log.d(TAG, "目标设备已连接！");
        isConnectedToTarget = true;
        
        updateNotification("✅ 设备已连接，开启热点中...");
        
        // 取消之前的关闭任务
        if (hotspotCloseRunnable != null) {
            handler.removeCallbacks(hotspotCloseRunnable);
        }
        
        // 延迟开启热点，确保连接稳定
        handler.postDelayed(() -> {
            enableHotspot(true);
        }, 2000);
    }

    private void onTargetDeviceDisconnected() {
        Log.d(TAG, "目标设备已断开");
        isConnectedToTarget = false;
        
        long delayMs = getHotspotCloseDelay();
        Log.d(TAG, "计划 " + (delayMs / 1000) + " 秒后关闭热点");
        
        updateNotification("❌ 设备已断开，" + (delayMs / 1000) + "秒后关闭热点");
        
        // 延迟关闭热点
        hotspotCloseRunnable = () -> {
            Log.d(TAG, "执行关闭热点");
            enableHotspot(false);
        };
        
        handler.postDelayed(hotspotCloseRunnable, delayMs);
    }

    private void enableHotspot(boolean enable) {
        Log.d(TAG, "========== 热点" + (enable ? "开启" : "关闭") + "请求 ==========");
        Log.d(TAG, "设备制造商：" + android.os.Build.MANUFACTURER);
        Log.d(TAG, "设备型号：" + android.os.Build.MODEL);
        Log.d(TAG, "Android API: " + Build.VERSION.SDK_INT);
        Log.d(TAG, "Display: " + android.os.Build.DISPLAY);
        Log.d(TAG, "是华为设备：" + isHuaweiDevice());
        Log.d(TAG, "是 HarmonyOS: " + isHarmonyOS());
        Log.d(TAG, "是 HarmonyOS 4.3.0: " + isHarmonyOS430());
        
        android.net.wifi.WifiManager wifiManager = 
            (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        if (wifiManager == null) {
            Log.e(TAG, "❌ WiFiManager 为空");
            updateNotification("❌ 系统错误：无法获取 WiFi 管理器");
            return;
        }
        
        Log.d(TAG, "✅ WiFiManager 获取成功");
        
        // 检查 WiFi 是否可用
        boolean isWifiEnabled = wifiManager.isWifiEnabled();
        Log.d(TAG, "WiFi 当前状态：" + (isWifiEnabled ? "已开启" : "已关闭"));
        
        boolean success = false;
        String usedMethod = "";
        
        // HarmonyOS 4.3.0 特殊处理
        if (isHarmonyOS430()) {
            Log.d(TAG, "🔷 使用 HarmonyOS 4.3.0 专用方法");
            success = enableHotspotForHarmonyOS430(wifiManager, enable);
            if (success) usedMethod = "HarmonyOS 4.3.0";
        }
        
        // 标准 Android 方法
        if (!success) {
            Log.d(TAG, "🔷 尝试标准 Android 反射方法");
            try {
                Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                    android.net.wifi.WifiConfiguration.class, boolean.class);
                success = (Boolean) method.invoke(wifiManager, null, enable);
                if (success) usedMethod = "标准反射";
            } catch (Exception e) {
                Log.e(TAG, "❌ 标准反射方法失败：" + e.getMessage());
            }
        }
        
        // 华为旧设备专用方法
        if (!success && isHuaweiDevice()) {
            Log.d(TAG, "🔷 尝试华为 HwWifiManager 方法");
            success = enableHotspotForHuawei(wifiManager, enable);
            if (success) usedMethod = "华为 HwWifiManager";
        }
        
        // 尝试关闭 WiFi 再开启热点（某些设备需要）
        if (!success && enable && isWifiEnabled) {
            Log.d(TAG, "🔷 尝试先关闭 WiFi 再开启热点");
            try {
                wifiManager.setWifiEnabled(false);
                Thread.sleep(500);
                
                Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                    android.net.wifi.WifiConfiguration.class, boolean.class);
                success = (Boolean) method.invoke(wifiManager, null, true);
                if (success) {
                    usedMethod = "关闭 WiFi 后开启";
                    Log.d(TAG, "✅ 成功：需要先关闭 WiFi");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ 关闭 WiFi 方法失败：" + e.getMessage());
                // 尝试重新开启 WiFi
                try {
                    wifiManager.setWifiEnabled(true);
                } catch (Exception ignore) {}
            }
        }
        
        isHotspotEnabled = enable && success;
        
        Log.d(TAG, "========== 热点操作完成 ==========");
        Log.d(TAG, "成功：" + success);
        Log.d(TAG, "使用方法：" + (usedMethod.isEmpty() ? "无" : usedMethod));
        Log.d(TAG, "热点状态：" + (isHotspotEnabled ? "已开启" : "已关闭"));
        
        final String status = enable ? 
            (success ? "✅ 热点已开启 (" + usedMethod + ")" : "❌ 热点开启失败") : 
            (success ? "✅ 热点已关闭" : "❌ 热点关闭失败");
        
        updateNotification("设备：" + (isConnectedToTarget ? "已连接" : "已断开") + " | " + status);
        
        // 如果失败，显示更详细的提示
        if (!success && enable) {
            Log.e(TAG, "⚠️ 热点开启失败，可能需要手动开启");
            Log.e(TAG, "⚠️ 请检查：设置 → 移动网络 → 个人热点");
        }
    }

    private boolean isHuaweiDevice() {
        return android.os.Build.MANUFACTURER.equalsIgnoreCase("HUAWEI") || 
               android.os.Build.MANUFACTURER.equalsIgnoreCase("HONOR");
    }
    
    private boolean isHarmonyOS() {
        // HarmonyOS 4.2.0 / 4.3.0 检测
        // 通过系统属性判断（华为设备专用）
        if (!isHuaweiDevice()) {
            return false;
        }
        
        // 尝试读取 HarmonyOS 版本属性
        try {
            @SuppressWarnings("DiscouragedPrivateApi")
            java.lang.reflect.Method get = System.class.getDeclaredMethod("getProperty", String.class);
            String harmonyVersion = (String) get.invoke(null, "ro.build.harmony.version");
            if (harmonyVersion != null && !harmonyVersion.isEmpty()) {
                Log.d(TAG, "HarmonyOS 版本：" + harmonyVersion);
                return true;
            }
        } catch (Exception e) {
            // 无法读取属性，继续其他检测
        }
        
        // HarmonyOS 4.x 基于 Android 12/13，可以通过 Build 信息辅助判断
        String display = android.os.Build.DISPLAY;
        if (display != null && (display.contains("HarmonyOS") || display.contains("OS"))) {
            Log.d(TAG, "检测到 HarmonyOS: " + display);
            return true;
        }
        
        // 默认华为设备认为是 HarmonyOS
        return true;
    }
    
    private boolean isHarmonyOS430() {
        // HarmonyOS 4.3.0 基于 Android 13 (API 33)
        // 主要通过系统属性判断
        if (!isHarmonyOS()) {
            return false;
        }
        
        try {
            @SuppressWarnings("DiscouragedPrivateApi")
            java.lang.reflect.Method get = System.class.getDeclaredMethod("getProperty", String.class);
            String harmonyVersion = (String) get.invoke(null, "ro.build.harmony.version");
            if (harmonyVersion != null && harmonyVersion.startsWith("4.3")) {
                Log.d(TAG, "HarmonyOS 4.3.0 检测到：" + harmonyVersion);
                return true;
            }
        } catch (Exception e) {
            // 无法读取
        }
        
        // API 33+ 且是华为设备，可能是 HarmonyOS 4.3.0
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    private boolean enableHotspotForHarmonyOS430(android.net.wifi.WifiManager wifiManager, boolean enable) {
        Log.d(TAG, "HarmonyOS 4.3.0 热点" + (enable ? "开启" : "关闭"));
        
        // HarmonyOS 4.3.0 可能使用新的 API 或限制更多
        // 尝试多种方法
        
        // 方法 1: 标准反射方法
        try {
            Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                android.net.wifi.WifiConfiguration.class, boolean.class);
            Boolean result = (Boolean) method.invoke(wifiManager, null, enable);
            if (result) {
                Log.d(TAG, "HarmonyOS 4.3.0 标准方法成功");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "HarmonyOS 4.3.0 标准方法失败", e);
        }
        
        // 方法 2: 尝试使用 WifiConfiguration 设置热点
        try {
            android.net.wifi.WifiConfiguration config = new android.net.wifi.WifiConfiguration();
            config.SSID = "Hotspot";
            config.preSharedKey = "12345678";
            
            Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                android.net.wifi.WifiConfiguration.class, boolean.class);
            Boolean result = (Boolean) method.invoke(wifiManager, config, enable);
            if (result) {
                Log.d(TAG, "HarmonyOS 4.3.0 带配置方法成功");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "HarmonyOS 4.3.0 带配置方法失败", e);
        }
        
        // 方法 3: 尝试华为 HwWifiManager
        try {
            Method getHwWifiManager = wifiManager.getClass().getMethod("getHwWifiManager");
            Object hwWifiManager = getHwWifiManager.invoke(wifiManager);
            
            if (hwWifiManager != null) {
                Method setWifiApEnabled = hwWifiManager.getClass()
                    .getMethod("setWifiApEnabled", 
                        android.net.wifi.WifiConfiguration.class, 
                        boolean.class, 
                        int.class);
                Boolean result = (Boolean) setWifiApEnabled.invoke(hwWifiManager, null, enable, 0);
                if (result) {
                    Log.d(TAG, "HarmonyOS 4.3.0 华为专用方法成功");
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "HarmonyOS 4.3.0 华为专用方法失败", e);
        }
        
        Log.w(TAG, "HarmonyOS 4.3.0 所有方法都失败了");
        return false;
    }

    private boolean enableHotspotForHuawei(android.net.wifi.WifiManager wifiManager, boolean enable) {
        try {
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
}
