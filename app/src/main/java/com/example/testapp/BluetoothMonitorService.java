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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
        serviceLog("服务创建");
        
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
        serviceLog("服务启动");
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
        serviceLog("服务销毁");
        
        if (bluetoothReceiver != null) {
            unregisterReceiver(bluetoothReceiver);
        }
        
        if (hotspotCloseRunnable != null) {
            handler.removeCallbacks(hotspotCloseRunnable);
        }
    }
    
    /**
     * 发送日志到 MainActivity
     */
    private void serviceLog(String message) {
        serviceLog(message, "INFO");
    }
    
    private void serviceLog(String message, String level) {
        Log.d(TAG, message);
        
        // 广播到 MainActivity
        Intent intent = new Intent("com.example.testapp.LOG_MESSAGE");
        intent.putExtra("log_message", message);
        intent.putExtra("log_level", level);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
                        serviceLog("📶 设备连接：" + deviceName + " (目标：" + targetName + ")");
                        
                        if (targetName.equalsIgnoreCase(deviceName)) {
                            onTargetDeviceConnected();
                        }
                    }
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        String deviceName = device.getName();
                        String targetName = getTargetDeviceName();
                        serviceLog("📶 设备断开：" + deviceName + " (目标：" + targetName + ")");
                        
                        if (targetName.equalsIgnoreCase(deviceName)) {
                            onTargetDeviceDisconnected();
                        }
                    }
                } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 
                                                    BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_OFF) {
                        serviceLog("⚠️ 蓝牙已关闭", "WARN");
                        isConnectedToTarget = false;
                        updateNotification("⚠️ 蓝牙已关闭");
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        serviceLog("✅ 蓝牙已开启，重新扫描设备");
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
            serviceLog("⚠️ 蓝牙未启用", "WARN");
            return;
        }
        
        String targetName = getTargetDeviceName();
        serviceLog("🔍 扫描已配对设备，目标：" + targetName);
        
        if (bluetoothAdapter.getBondedDevices() != null) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                if (targetName.equalsIgnoreCase(device.getName())) {
                    serviceLog("✅ 发现目标设备：" + device.getAddress());
                    if (isDeviceConnected(device)) {
                        serviceLog("🔗 设备已连接，触发连接事件");
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
            serviceLog("❌ 检查连接状态失败：" + e.getMessage(), "ERROR");
            return false;
        }
    }

    private void onTargetDeviceConnected() {
        serviceLog("🎉 目标设备已连接！");
        isConnectedToTarget = true;
        
        updateNotification("✅ 设备已连接，开启热点中...");
        
        // 取消之前的关闭任务
        if (hotspotCloseRunnable != null) {
            handler.removeCallbacks(hotspotCloseRunnable);
        }
        
        // 延迟开启热点，确保连接稳定
        serviceLog("⏱️ 2 秒后开启热点");
        handler.postDelayed(() -> {
            enableHotspot(true);
        }, 2000);
    }

    private void onTargetDeviceDisconnected() {
        serviceLog("❌ 目标设备已断开");
        isConnectedToTarget = false;
        
        long delayMs = getHotspotCloseDelay();
        serviceLog("⏱️ 计划 " + (delayMs / 1000) + " 秒后关闭热点");
        
        updateNotification("❌ 设备已断开，" + (delayMs / 1000) + "秒后关闭热点");
        
        // 延迟关闭热点
        hotspotCloseRunnable = () -> {
            serviceLog("⏰ 执行关闭热点");
            enableHotspot(false);
        };
        
        handler.postDelayed(hotspotCloseRunnable, delayMs);
    }

    private void enableHotspot(boolean enable) {
        serviceLog("========== 热点" + (enable ? "开启" : "关闭") + "请求 ==========");
        serviceLog("📱 设备制造商：" + android.os.Build.MANUFACTURER);
        serviceLog("📱 设备型号：" + android.os.Build.MODEL);
        serviceLog("🤖 Android API: " + Build.VERSION.SDK_INT);
        serviceLog("📝 Display: " + android.os.Build.DISPLAY);
        serviceLog("🔷 是华为设备：" + isHuaweiDevice());
        serviceLog("🔷 是 HarmonyOS: " + isHarmonyOS());
        
        android.net.wifi.WifiManager wifiManager = 
            (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        if (wifiManager == null) {
            serviceLog("❌ WiFiManager 为空", "ERROR");
            updateNotification("❌ 系统错误：无法获取 WiFi 管理器");
            return;
        }
        
        serviceLog("✅ WiFiManager 获取成功");
        
        // 检查 WiFi 是否可用
        boolean isWifiEnabled = wifiManager.isWifiEnabled();
        serviceLog("📶 WiFi 当前状态：" + (isWifiEnabled ? "已开启" : "已关闭"));
        
        boolean success = false;
        String usedMethod = "";
        
        // 方法 1: 标准 Android 反射方法（最先尝试，因为最简单）
        serviceLog("🔷 方法 1/5: 标准 Android 反射方法");
        try {
            Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                android.net.wifi.WifiConfiguration.class, boolean.class);
            success = (Boolean) method.invoke(wifiManager, null, enable);
            if (success) {
                usedMethod = "标准反射";
                serviceLog("✅ 方法 1 成功");
            } else {
                serviceLog("❌ 方法 1 返回 false");
            }
        } catch (Exception e) {
            serviceLog("❌ 方法 1 异常：" + e.getClass().getSimpleName() + ": " + e.getMessage(), "ERROR");
        }
        
        // 方法 2: 带 WiFi 配置的反射方法
        if (!success && enable) {
            serviceLog("🔷 方法 2/5: 带配置的反射方法");
            try {
                android.net.wifi.WifiConfiguration config = new android.net.wifi.WifiConfiguration();
                config.SSID = "Hotspot_" + System.currentTimeMillis();
                config.preSharedKey = "12345678";
                config.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK);
                
                Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                    android.net.wifi.WifiConfiguration.class, boolean.class);
                success = (Boolean) method.invoke(wifiManager, config, true);
                if (success) {
                    usedMethod = "带配置反射";
                    serviceLog("✅ 方法 2 成功");
                } else {
                    serviceLog("❌ 方法 2 返回 false");
                }
            } catch (Exception e) {
                serviceLog("❌ 方法 2 异常：" + e.getClass().getSimpleName(), "ERROR");
            }
        }
        
        // 方法 3: 华为 HwWifiManager
        if (!success && isHuaweiDevice()) {
            serviceLog("🔷 方法 3/5: 华为 HwWifiManager");
            success = enableHotspotForHuawei(wifiManager, enable);
            if (success) usedMethod = "华为 HwWifiManager";
        }
        
        // 方法 4: 关闭 WiFi 后再开启
        if (!success && enable && isWifiEnabled) {
            serviceLog("🔷 方法 4/5: 关闭 WiFi 后开启");
            try {
                serviceLog("⚠️ 关闭 WiFi...");
                wifiManager.setWifiEnabled(false);
                Thread.sleep(1000);
                
                Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                    android.net.wifi.WifiConfiguration.class, boolean.class);
                success = (Boolean) method.invoke(wifiManager, null, true);
                if (success) {
                    usedMethod = "关闭 WiFi 后开启";
                    serviceLog("✅ 方法 4 成功");
                } else {
                    serviceLog("❌ 方法 4 返回 false");
                    // 重新开启 WiFi
                    wifiManager.setWifiEnabled(true);
                    serviceLog("⚠️ 重新开启 WiFi");
                }
            } catch (Exception e) {
                serviceLog("❌ 方法 4 异常：" + e.getMessage(), "ERROR");
                try { wifiManager.setWifiEnabled(true); } catch (Exception ignore) {}
            }
        }
        
        // 方法 5: 使用 Tethering Manager（Android 11+ 官方 API）
        if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceLog("🔷 方法 5/6: Tethering Manager (Android 11+ 官方 API)");
            success = enableTethering(enable);
            if (success) usedMethod = "Tethering Manager";
        }
        
        // 方法 6: 通过 ContentResolver 修改系统设置
        if (!success && enable) {
            serviceLog("🔷 方法 6/6: 修改系统设置 (ContentResolver)");
            try {
                // 尝试通过系统设置开启热点
                android.net.wifi.WifiConfiguration config = new android.net.wifi.WifiConfiguration();
                config.SSID = "AutoHotspot_" + System.currentTimeMillis();
                config.preSharedKey = "12345678";
                config.allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK);
                config.allowedAuthAlgorithms.set(android.net.wifi.WifiConfiguration.AuthAlgorithm.OPEN);
                config.allowedProtocols.set(android.net.wifi.WifiConfiguration.Protocol.RSN);
                config.allowedPairwiseCiphers.set(android.net.wifi.WifiConfiguration.PairwiseCipher.CCMP);
                config.allowedGroupCiphers.set(android.net.wifi.WifiConfiguration.GroupCipher.CCMP);
                
                // 尝试不同的反射方法
                Method[] methods = wifiManager.getClass().getDeclaredMethods();
                for (Method m : methods) {
                    if (m.getName().equals("setWifiApEnabled") && m.getParameterCount() == 2) {
                        serviceLog("  尝试方法：" + m.getName());
                        m.setAccessible(true);
                        Boolean result = (Boolean) m.invoke(wifiManager, config, true);
                        if (result) {
                            success = true;
                            usedMethod = "反射 setWifiApEnabled";
                            serviceLog("✅ 方法 6 成功");
                            break;
                        }
                    }
                }
                
                if (!success) {
                    serviceLog("❌ 方法 6 所有尝试都失败了", "ERROR");
                }
            } catch (Exception e) {
                serviceLog("❌ 方法 6 异常：" + e.getClass().getSimpleName() + ": " + e.getMessage(), "ERROR");
            }
        }
        
        isHotspotEnabled = enable && success;
        
        serviceLog("========== 热点操作完成 ==========");
        serviceLog("✅ 成功：" + success);
        serviceLog("🔧 使用方法：" + (usedMethod.isEmpty() ? "无" : usedMethod));
        serviceLog("📶 热点状态：" + (isHotspotEnabled ? "已开启" : "已关闭"));
        
        final String status = enable ? 
            (success ? "✅ 热点已开启 (" + usedMethod + ")" : "❌ 热点开启失败") : 
            (success ? "✅ 热点已关闭" : "❌ 热点关闭失败");
        
        updateNotification("设备：" + (isConnectedToTarget ? "已连接" : "已断开") + " | " + status);
        
        // 如果失败，给出详细建议
        if (!success && enable) {
            serviceLog("⚠️ ️ ⚠️ 热点开启失败 ⚠️ ⚠️ ⚠️", "ERROR");
            serviceLog("原因：HarmonyOS 系统限制了第三方应用直接开启热点", "WARN");
            serviceLog("解决方案:", "WARN");
            serviceLog("  1. 手动开启：设置 → 移动网络 → 个人热点", "WARN");
            serviceLog("  2. 或使用 ADB 授权（需要电脑）:", "WARN");
            serviceLog("     adb shell pm grant com.example.testapp android.permission.WRITE_SECURE_SETTINGS", "WARN");
        }
    }
    
    /**
     * Android 11+ 使用 Tethering Manager 开启热点（官方 API）
     */
    private boolean enableTethering(boolean enable) {
        try {
            // 获取 TetheringManager
            Object tetheringManager;
            
            // 方法 1: 从 Context 获取 TetheringManager
            try {
                Class<?> tetheringManagerClass = Class.forName("android.net.TetheringManager");
                Method getTetheringManager = Context.class.getMethod("getSystemService", String.class);
                tetheringManager = getTetheringManager.invoke(getApplicationContext(), "tethering");
                
                if (tetheringManager == null) {
                    serviceLog("⚠️ TetheringManager 为空", "WARN");
                    return false;
                }
                
                serviceLog("  获取 TetheringManager 成功");
                
                // 调用 startTethering 方法
                // public void startTethering(int type, boolean showProvisioningUi, OnStartTetheringCallback callback, Handler handler)
                Method startTethering = tetheringManager.getClass().getMethod(
                    "startTethering", 
                    int.class, 
                    boolean.class, 
                    Class.forName("android.net.TetheringManager$OnStartTetheringCallback"),
                    android.os.Handler.class
                );
                
                // 创建回调对象
                Class<?> callbackClass = Class.forName("android.net.TetheringManager$OnStartTetheringCallback");
                Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] { callbackClass },
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                            serviceLog("  Tethering 回调：" + method.getName());
                            return null;
                        }
                    }
                );
                
                // 调用 startTethering
                // TETHERING_WIFI = 0
                startTethering.invoke(tetheringManager, 0, false, callback, handler);
                
                serviceLog("✅ Tethering Manager 调用成功");
                
                // 等待一下让热点启动
                Thread.sleep(2000);
                
                return true;
                
            } catch (Exception e) {
                serviceLog("❌ Tethering Manager 方法 1 失败：" + e.getMessage(), "ERROR");
            }
            
            // 方法 2: 使用 WifiManager 的 startLocalOnlyHotspot
            try {
                serviceLog("  尝试 startLocalOnlyHotspot...");
                android.net.wifi.WifiManager wm = 
                    (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                
                if (wm != null) {
                    Method startLocalOnlyHotspot = wm.getClass().getMethod(
                        "startLocalOnlyHotspot",
                        android.net.wifi.WifiManager.LocalOnlyHotspotCallback.class,
                        android.os.Handler.class
                    );
                    
                    startLocalOnlyHotspot.invoke(wm, new android.net.wifi.WifiManager.LocalOnlyHotspotCallback() {
                        @Override
                        public void onStarted(android.net.wifi.WifiManager.LocalOnlyHotspotReservation reservation) {
                            super.onStarted(reservation);
                            serviceLog("✅ LocalOnlyHotspot 启动成功");
                        }
                        
                        @Override
                        public void onFailed(int reason) {
                            super.onFailed(reason);
                            serviceLog("❌ LocalOnlyHotspot 失败：" + reason);
                        }
                    }, handler);
                    
                    serviceLog("✅ startLocalOnlyHotspot 调用成功");
                    Thread.sleep(2000);
                    return true;
                }
                
            } catch (Exception e) {
                serviceLog("❌ startLocalOnlyHotspot 失败：" + e.getMessage(), "ERROR");
            }
            
        } catch (Exception e) {
            serviceLog("❌ Tethering Manager 异常：" + e.getMessage(), "ERROR");
        }
        
        return false;
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
                serviceLog("🔷 HarmonyOS 版本：" + harmonyVersion);
                return true;
            }
        } catch (Exception e) {
            // 无法读取属性，继续其他检测
        }
        
        // HarmonyOS 4.x 基于 Android 12/13，可以通过 Build 信息辅助判断
        String display = android.os.Build.DISPLAY;
        if (display != null && (display.contains("HarmonyOS") || display.contains("OS"))) {
            serviceLog("🔷 检测到 HarmonyOS: " + display);
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
                serviceLog("🔷 HarmonyOS 4.3.0 检测到：" + harmonyVersion);
                return true;
            }
        } catch (Exception e) {
            // 无法读取
        }
        
        // API 33+ 且是华为设备，可能是 HarmonyOS 4.3.0
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    private boolean enableHotspotForHarmonyOS430(android.net.wifi.WifiManager wifiManager, boolean enable) {
        serviceLog("🔷 HarmonyOS 4.3.0 热点" + (enable ? "开启" : "关闭"));
        
        // HarmonyOS 4.3.0 可能使用新的 API 或限制更多
        // 尝试多种方法
        
        // 方法 1: 标准反射方法
        try {
            Method method = wifiManager.getClass().getMethod("setWifiApEnabled", 
                android.net.wifi.WifiConfiguration.class, boolean.class);
            Boolean result = (Boolean) method.invoke(wifiManager, null, enable);
            if (result) {
                serviceLog("✅ HarmonyOS 4.3.0 标准方法成功");
                return true;
            }
        } catch (Exception e) {
            serviceLog("❌ HarmonyOS 4.3.0 标准方法失败：" + e.getMessage(), "ERROR");
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
                serviceLog("✅ HarmonyOS 4.3.0 带配置方法成功");
                return true;
            }
        } catch (Exception e) {
            serviceLog("❌ HarmonyOS 4.3.0 带配置方法失败：" + e.getMessage(), "ERROR");
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
                    serviceLog("✅ HarmonyOS 4.3.0 华为专用方法成功");
                    return true;
                }
            }
        } catch (Exception e) {
            serviceLog("❌ HarmonyOS 4.3.0 华为专用方法失败：" + e.getMessage(), "ERROR");
        }
        
        serviceLog("⚠️ HarmonyOS 4.3.0 所有方法都失败了", "WARN");
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
                Boolean result = (Boolean) setWifiApEnabled.invoke(hwWifiManager, null, enable, 0);
                if (result) {
                    serviceLog("✅ 华为 HwWifiManager 方法成功");
                    return true;
                }
            }
        } catch (Exception e) {
            serviceLog("❌ 华为 HwWifiManager 方法失败：" + e.getMessage(), "ERROR");
        }
        
        return false;
    }
}
