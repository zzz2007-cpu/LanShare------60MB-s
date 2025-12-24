package com.lanshare.network.discovery;

import com.lanshare.network.model.DeviceInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 设备注册表
 * 负责管理所有发现的设备信息
 *
 * 功能：
 * 1. 注册新设备
 * 2. 更新设备信息
 * 3. 删除离线设备
 * 4. 查询设备
 *
 * 线程安全：使用 ConcurrentHashMap 保证多线程安全
 *
 * @author 主人
 * @version 1.0
 */
public class DeviceRegistry {
    private static final Logger logger = Logger.getLogger(DeviceRegistry.class.getName());

    // ==================== 数据存储 ====================

    /**
     * 设备映射表
     * Key: deviceId (设备唯一标识)
     * Value: DeviceInfo (设备信息)
     *
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private final ConcurrentHashMap<String, DeviceInfo> deviceMap;

    /**
     * 可观察的设备列表
     * 用于 JavaFX UI 绑定，列表变化时自动更新界面
     */
    private final ObservableList<DeviceInfo> deviceList;

    // ==================== 构造函数 ====================

    /**
     * 构造函数 - 初始化数据结构
     */
    public DeviceRegistry() {
        this.deviceMap = new ConcurrentHashMap<>();
        this.deviceList = FXCollections.observableArrayList();

        logger.info("DeviceRegistry 已创建");
    }

    // ==================== 核心操作 ====================

    /**
     * 注册或更新设备
     *
     * 如果设备已存在，更新其信息（主要是最后在线时间）
     * 如果设备不存在，添加到列表
     *
     * @param device 设备信息
     */
    public void registerOrUpdate(DeviceInfo device) {
        if (device == null) {
            logger.warning("尝试注册空设备，已忽略");
            return;
        }

        String deviceId = device.getDeviceId();

        // 检查设备是否已存在
        DeviceInfo existing = deviceMap.get(deviceId);

        if (existing != null) {
            // 设备已存在，更新信息
            updateExistingDevice(existing);
            logger.fine("更新设备: " + deviceId);
        } else {
            // 新设备，检查是否存在同IP的旧设备
            DeviceInfo sameIp = null;
            for (DeviceInfo d : deviceMap.values()) {
                if (d.getIpAddress().equals(device.getIpAddress())) { sameIp = d; break; }
            }
            if (sameIp != null) {
                removeDevice(sameIp.getDeviceId());
            }
            addNewDevice(device);
            logger.info("注册新设备: " + device);
        }
    }

    /**
     * 更新已存在的设备
     * 主要更新最后在线时间和状态
     */
    private void updateExistingDevice(DeviceInfo device) {
        device.updateLastSeen();
        if (Platform.isFxApplicationThread()) {
            int idx = deviceList.indexOf(device);
            if (idx >= 0) { deviceList.set(idx, device); }
        } else {
            Platform.runLater(() -> {
                int idx = deviceList.indexOf(device);
                if (idx >= 0) { deviceList.set(idx, device); }
            });
        }
    }

    /**
     * 添加新设备
     */
    private void addNewDevice(DeviceInfo device) {
        // 1. 添加到 Map
        deviceMap.put(device.getDeviceId(), device);

        // 2. 添加到 ObservableList（需要在 JavaFX 线程中执行）
        if (Platform.isFxApplicationThread()) {
            deviceList.add(device);
        } else {
            Platform.runLater(() -> deviceList.add(device));
        }
    }

    /**
     * 移除设备
     *
     * @param deviceId 设备ID
     * @return 如果设备存在并被移除，返回 true；否则返回 false
     */
    public boolean removeDevice(String deviceId) {
        if (deviceId == null) {
            logger.warning("尝试移除空设备ID，已忽略");
            return false;
        }

        // 1. 从 Map 中移除
        DeviceInfo removed = deviceMap.remove(deviceId);

        if (removed != null) {
            // 2. 从 ObservableList 中移除（需要在 JavaFX 线程中执行）
            if (Platform.isFxApplicationThread()) {
                deviceList.remove(removed);
            } else {
                Platform.runLater(() -> deviceList.remove(removed));
            }

            logger.info("移除设备: " + deviceId);
            return true;
        } else {
            logger.fine("设备不存在，无法移除: " + deviceId);
            return false;
        }
    }

    /**
     * 查找设备
     *
     * @param deviceId 设备ID
     * @return 设备信息，如果不存在返回 null
     */
    public DeviceInfo findDevice(String deviceId) {
        return deviceMap.get(deviceId);
    }

    /**
     * 检查设备是否存在
     *
     * @param deviceId 设备ID
     * @return 如果设备存在返回 true，否则返回 false
     */
    public boolean contains(String deviceId) {
        return deviceMap.containsKey(deviceId);
    }

    /**
     * 获取所有设备
     *
     * @return 可观察的设备列表（用于 UI 绑定）
     */
    public ObservableList<DeviceInfo> getAllDevices() {
        return deviceList;
    }

    /**
     * 获取设备数量
     *
     * @return 当前注册的设备数量
     */
    public int getDeviceCount() {
        return deviceMap.size();
    }

    /**
     * 清空所有设备
     */
    public void clear() {
        logger.info("清空设备注册表");

        // 1. 清空 Map
        deviceMap.clear();

        // 2. 清空 ObservableList（需要在 JavaFX 线程中执行）
        if (Platform.isFxApplicationThread()) {
            deviceList.clear();
        } else {
            Platform.runLater(() -> deviceList.clear());
        }
    }

    // ==================== 批量操作 ====================

    /**
     * 移除所有离线设备
     *
     * @return 被移除的设备数量
     */
    public int removeOfflineDevices() {
        int count = 0;

        // 遍历所有设备，移除离线的
        for (DeviceInfo device : deviceList) {
            if (device.getStatus() == com.lanshare.network.model.DeviceStatus.OFFLINE) {
                if (removeDevice(device.getDeviceId())) {
                    count++;
                }
            }
        }

        if (count > 0) {
            logger.info("移除了 " + count + " 个离线设备");
        }

        return count;
    }

    // ==================== 调试方法 ====================

    /**
     * 打印所有设备（用于调试）
     */
    public void printAllDevices() {
        System.out.println("========== DeviceRegistry ==========");
        System.out.println("设备数量: " + getDeviceCount());

        for (DeviceInfo device : deviceList) {
            System.out.println("  " + device);
        }

        System.out.println("====================================");
    }
}