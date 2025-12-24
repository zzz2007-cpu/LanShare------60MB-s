package com.lanshare.network.model;

import java.util.Objects;
public class DeviceInfo {
    private final String deviceId;//设备的唯一标识符
    private final String deviceName;//设备名称
    private final String userName;//用户名称
    private final String ipAddress;//设备的ip地址
    private  final int port;//设备的TCP端口号
    private volatile long lastSeen;//设备最后一次在线时间
    private volatile DeviceStatus status;//设备当前状态

    public DeviceInfo(String deviceId, String deviceName, String userName, String ipAddress, int port) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.userName = userName;
        this.ipAddress = ipAddress;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();//设置为当前时间
        this.status = DeviceStatus.ONLINE;
    }
    public void updateLastSeen(){
        this.lastSeen = System.currentTimeMillis();
        this.status=DeviceStatus.ONLINE;
    }

    public boolean isTimeout(long timeout){
        return System.currentTimeMillis() - lastSeen > timeout;
    }
    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getUserName() {
        return userName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public long getLastSeen() {
        return lastSeen;
    }
    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
    public DeviceStatus getStatus() {
        return status;
    }
    public void setStatus(DeviceStatus status) {
        this.status = status;
    }
    @Override
    public  boolean equals(Object o) {
        if(this==o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        DeviceInfo that = (DeviceInfo) o;
        return Objects.equals(deviceId,that.deviceId);
    }
    @Override
    public int hashCode() {
        return Objects.hash(deviceId);
    }
    @Override
    public String toString() {
        return String.format("DeviceInfo{id=%s, name=%s, ip=%s:%d, status=%s}",
                deviceId, deviceName, ipAddress, port, status);
    }
}
