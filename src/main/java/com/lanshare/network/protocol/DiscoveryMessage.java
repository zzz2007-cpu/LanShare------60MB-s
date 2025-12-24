package com.lanshare.network.protocol;
import com.google.gson.Gson;
import com.lanshare.network.model.DeviceInfo;
/**
 * 设备发现消息
 * * 这是一个数据传输对象 (DTO)，用于在局域网中通过 UDP 广播。
 * 它封装了设备进行自我介绍（上线）、保持在线（心跳）或通知下线所需的所有信息。
 * * 它将被 Gson 序列化为 JSON 字符串进行传输。
 */
public class DiscoveryMessage {
    public enum MessageType{
        PRESENCE,//设备刚刚上线

        HEARTBEAT,//设备在线时发送

        GOODBYE//设备下线

    }
    private MessageType type;
    private String deviceId;
    private String deviceName;
    private String UserName;
    private String ipAddress;
    private int port;
    private long timestamp;//时间戳

    private DiscoveryMessage(){

    }
    public DiscoveryMessage(MessageType type,DeviceInfo deviceInfo){
        this.type = type;
        this.deviceId = deviceInfo.getDeviceId();
        this.deviceName = deviceInfo.getDeviceName();
        this.UserName = deviceInfo.getUserName();
        this.ipAddress = deviceInfo.getIpAddress();
        this.port = deviceInfo.getPort();
        this.timestamp = System.currentTimeMillis();
    }
    private static final Gson gson = new Gson();
    //将当前消息对象序列化为json字符串
    public String toJson(){
        return gson.toJson(this);
    }
    //从json字符串反序列化为DiscoveryMessage
    public static DiscoveryMessage fromJson(String json){
        return gson.fromJson(json, DiscoveryMessage.class);
    }

    //将接收到的消息转换为 DeviceInfo 对象
    public DeviceInfo toDeviceInfo(){
        return new DeviceInfo(
                this.deviceId,
                this.deviceName,
                this.UserName,
                this.ipAddress,
                this.port
        );
    }//用来更新设备列表
    public MessageType getType() {
        return type;
    }
    public String getDeviceId() {
        return deviceId;
    }
    public String getDeviceName() {
        return deviceName;
    }
    public String getUserName() {
        return UserName;
    }
    public String getIpAddress() {
        return ipAddress;
    }
    public int getPort() {
        return port;
    }




}
