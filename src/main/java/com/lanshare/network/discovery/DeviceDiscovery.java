package com.lanshare.network.discovery;
import com.lanshare.network.config.NetworkConfig;
import com.lanshare.network.model.DeviceInfo;
import com.lanshare.network.model.DeviceStatus;
import com.lanshare.network.protocol.DiscoveryMessage;
import com.lanshare.network.discovery.UdpBrodcaster;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

//设备发现服务-总指挥

/**
 * 1.定期广播自己的存在
 * 2.监听其他设备的广播
 * 3.维护设备列表
 * 4.检测设备超时
 */


public class DeviceDiscovery {
    private static final Logger logger = Logger.getLogger(DeviceDiscovery.class.getName());
    private UdpBrodcaster udpBroadcaster;//广播器
    private DeviceRegistry deviceRegistry;//注册表
    private DeviceInfo localDevice;//本机设备信息
    private ScheduledExecutorService scheduler;//定时任务调度器
    private volatile boolean running=false;
    public DeviceDiscovery(){
        this.udpBroadcaster=new UdpBrodcaster();
        this.deviceRegistry=new DeviceRegistry();

        logger.info("DeviceDiscovery created");
    }

    public void initialize() throws IOException {
        logger.info("DeviceDiscovery initialized.....");
        createLocalDevice();
        udpBroadcaster.setMessageListener(new UdpBrodcaster.UdpMessageListener(){
            @Override
                    public void onMessage(String message,InetAddress senderAddress){
                handleDiscoveryMessage(message,senderAddress);
            }
        });
        logger.info("DeviceDiscovery 初始化完成");
        logger.info("本机设备"+ localDevice.getDeviceName());
    }

    public void start()throws IOException{
        if(running){
            logger.warning("DeviceDiscovery 已经在运行");
            return;
        }
        logger.info("正在启动 DeviceDiscovery");

        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(3);
        }


        udpBroadcaster.start();

        startScheduleTasks();
        running=true;
        logger.info("DeviceDiscovery 已启动");
        broadcastPresence();
    }

    /**
     * 停止服务
     */
    public void stop() {
        if (!running) {
            logger.info("DeviceDiscovery 未运行，无需停止");
            return;
        }

        logger.info("正在停止 DeviceDiscovery...");

        running = false;

        // 1. 发送下线广播
        broadcastGoodbye();

        // 2. 停止定时任务
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            logger.info("定时任务调度器已停止");
        }

        // 3. 停止 UDP 广播器
        udpBroadcaster.stop();

        // 4. 清空设备列表
        deviceRegistry.clear();

        logger.info("DeviceDiscovery 已停止");
    }


    //启动所有定时任务
    private void startScheduleTasks(){
        //1.定期广播自己的存在
        scheduler.scheduleAtFixedRate(
                this::broadcastPresence,
                0,
                NetworkConfig.BROADCAST_INTERVAL,
                TimeUnit.MILLISECONDS
        );
        logger.info("已启动定期广播(间隔："+NetworkConfig.BROADCAST_INTERVAL+"ms");
        //2.发送心跳
        scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                NetworkConfig.HEARTBEAT_INTERVAL,  // 初始延迟5秒
                NetworkConfig.HEARTBEAT_INTERVAL,
                TimeUnit.MILLISECONDS
        );
        logger.info("已启动心跳任务（间隔: " + NetworkConfig.HEARTBEAT_INTERVAL + "ms）");
        // 任务3：检查设备超时（每10秒）
        scheduler.scheduleAtFixedRate(
                this::checkDeviceTimeout,
                NetworkConfig.DEVICE_CHECK_INTERVAL,  // 初始延迟10秒
                NetworkConfig.DEVICE_CHECK_INTERVAL,
                TimeUnit.MILLISECONDS
        );
        logger.info("已启动设备超时检查任务（间隔: " + NetworkConfig.DEVICE_CHECK_INTERVAL + "ms）");
    }
// ==================== 广播相关 ====================

    /**
     * 广播设备上线消息
     */
    private void broadcastPresence() {
        try {
            DiscoveryMessage message = new DiscoveryMessage(
                    DiscoveryMessage.MessageType.PRESENCE,
                    localDevice
            );
            String json = message.toJson();
            udpBroadcaster.sendBroadcast(json);
            logger.fine("已发送上线广播");
        } catch (Exception e) {
            logger.warning("发送上线广播失败: " + e.getMessage());
        }
    }

    /**
     * 发送心跳消息
     */
    private void sendHeartbeat() {
        try {
            DiscoveryMessage message = new DiscoveryMessage(
                    DiscoveryMessage.MessageType.HEARTBEAT,
                    localDevice
            );
            String json = message.toJson();
            udpBroadcaster.sendBroadcast(json);
            logger.fine("已发送心跳");
        } catch (Exception e) {
            logger.warning("发送心跳失败: " + e.getMessage());
        }
    }

    /**
     * 广播设备下线消息
     */
    private void broadcastGoodbye() {
        try {
            DiscoveryMessage message = new DiscoveryMessage(
                    DiscoveryMessage.MessageType.GOODBYE,
                    localDevice
            );
            String json = message.toJson();
            udpBroadcaster.sendBroadcast(json);
            logger.info("已发送下线广播");

            // 等待消息发送完成
            Thread.sleep(500);
        } catch (Exception e) {
            logger.warning("发送下线广播失败: " + e.getMessage());
        }
    }





//---------------------------消息处理
    private void handleDiscoveryMessage(String json,InetAddress senderAddress){
        try{
            DiscoveryMessage message=DiscoveryMessage.fromJson(json);//解析消息
            if(message.getDeviceId().equals(localDevice.getDeviceId())){

                logger.finest("忽略自己的消息");
                return;
            }
            // 使用实际报文来源地址覆盖消息中的IP，避免对方携带错误IP导致发送到自己
            String realIp = senderAddress != null ? senderAddress.getHostAddress() : message.getIpAddress();
            DeviceInfo device = new DeviceInfo(
                    message.getDeviceId(),
                    message.getDeviceName(),
                    message.getUserName(),
                    realIp,
                    message.getPort()
            );
            switch(message.getType()){
                case PRESENCE:
                    handlePresence(device);
                    break;
                case HEARTBEAT:
                    handleHeartbeat(device);
                    break;
                case GOODBYE:
                    handleGoodbye(device);
                    break;
                default:
                    logger.warning("未知的消息类型"+message.getType());

            }
        }catch(Exception e){
            logger.warning("处理发现消息失败"+e.getMessage());
            logger.fine("无效的消息内容:"+json);
        }
    }
    /**
     * 处理设备上线消息
     */
    private void handlePresence(DeviceInfo device) {
        logger.info("发现新设备: " + device);
        deviceRegistry.registerOrUpdate(device);
    }

    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(DeviceInfo device) {
        logger.fine("收到心跳: " + device.getDeviceId());
        deviceRegistry.registerOrUpdate(device);
    }

    /**
     * 处理设备下线消息
     */
    private void handleGoodbye(DeviceInfo device) {
        logger.info("设备下线: " + device);
        deviceRegistry.removeDevice(device.getDeviceId());
    }
    /**
    * 检查设备是否超时
     * 超时的设备标记为离线，长时间离线的设备会被删除
     */
    private void checkDeviceTimeout() {
        try {
            logger.fine("正在检查设备超时...");

            ObservableList<DeviceInfo> devices = deviceRegistry.getAllDevices();
            int timeoutCount = 0;
            int removedCount = 0;

            for (DeviceInfo device : devices) {
                // 检查是否超时（30秒无响应）
                if (device.isTimeout(NetworkConfig.DEVICE_TIMEOUT)) {
                    if (device.getStatus() == DeviceStatus.ONLINE) {
                        // 标记为离线
                        device.setStatus(DeviceStatus.OFFLINE);
                        timeoutCount++;
                        logger.warning("设备超时: " + device.getDeviceId());
                    }

                    // 检查是否需要删除（2分钟无响应）
                    if (device.isTimeout(NetworkConfig.DEVICE_CLEANUP_TIME)) {
                        deviceRegistry.removeDevice(device.getDeviceId());
                        removedCount++;
                        logger.info("删除长时间离线的设备: " + device.getDeviceId());
                    }
                }
            }

            if (timeoutCount > 0 || removedCount > 0) {
                logger.info(String.format("超时检查完成: %d 个设备离线, %d 个设备被删除",
                        timeoutCount, removedCount));
            }

        } catch (Exception e) {
            logger.warning("检查设备超时时出错: " + e.getMessage());
        }
    }
//-----------------------本地设备管理---------------------
    private void createLocalDevice() throws IOException {
        String deviceId = generateStableDeviceId();
        String deviceName = getComputerName();
        String userName = System.getProperty("user.name","Unknown");
        String ipAddress = getLocalIpAddress();
        int port = NetworkConfig.getTcpPort();
        localDevice = new DeviceInfo(deviceId, deviceName, userName, ipAddress, port);
        logger.info("本机设备已生成");
    }

    private String generateStableDeviceId() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nics = java.net.NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                java.net.NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) { continue; }
                byte[] mac = nic.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) { sb.append(String.format("%02X", b)); }
                    String host = getComputerName();
                    return java.util.UUID.nameUUIDFromBytes((host + sb.toString()).getBytes()).toString();
                }
            }
        } catch (Exception ignored) {}
        String host = getComputerName();
        String ip;
        try { ip = getLocalIpAddress(); } catch (UnknownHostException e) { ip = "0.0.0.0"; }
        return java.util.UUID.nameUUIDFromBytes((host + ip).getBytes()).toString();
    }
    //获取计算机名
    private String getComputerName(){
        try{
            String hostName=InetAddress.getLocalHost().getHostName();
            return hostName!=null?hostName:"Unknown";
        }catch(UnknownHostException e){
            logger.warning("无法获取计算机名"+e.getMessage());
            return "Unknown";
        }
    }
    //获取本机ip地址
    private String getLocalIpAddress() throws UnknownHostException {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nics = java.net.NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                java.net.NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) { continue; }
                java.util.Enumeration<java.net.InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            // 次优选：非回环的IPv4
            nics = java.net.NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                java.net.NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isVirtual()) { continue; }
                java.util.Enumeration<java.net.InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        // 回退
        InetAddress localHost = InetAddress.getLocalHost();
        return localHost.getHostAddress();
    }
    // ==================== 手动操作 ====================

    /**
     * 手动刷新设备列表
     * 立即发送一次广播，让其他设备响应
     */
    public void refreshDevices() {
        logger.info("手动刷新设备列表");
        broadcastPresence();
    }

    /**
     * 清除所有设备
     */
    public void clearAllDevices() {
        logger.info("清除所有设备");
        deviceRegistry.clear();
    }

    // ==================== Getter 方法 ====================

    /**
     * 获取本机设备信息
     */
    public DeviceInfo getLocalDevice() {
        return localDevice;
    }

    /**
     * 更新本机设备端口
     */
    public void updateLocalPort(int port) {
        if (localDevice != null) {
            localDevice = new DeviceInfo(
                localDevice.getDeviceId(),
                localDevice.getDeviceName(),
                localDevice.getUserName(),
                localDevice.getIpAddress(),
                port
            );
            logger.info("本机设备端口更新为: " + port);
            if (running) {
                broadcastPresence();
            }
        }
    }

    /**
     * 获取设备列表
     * 返回的是 ObservableList，可以直接绑定到 JavaFX UI
     */
    public ObservableList<DeviceInfo> getDeviceList() {
        return deviceRegistry.getAllDevices();
    }

    /**
     * 根据 ID 查找设备
     */
    public DeviceInfo findDevice(String deviceId) {
        return deviceRegistry.findDevice(deviceId);
    }

    /**
     * 获取在线设备数量
     */
    public int getOnlineDeviceCount() {
        int count = 0;
        for (DeviceInfo device : deviceRegistry.getAllDevices()) {
            if (device.getStatus() == DeviceStatus.ONLINE) {
                count++;
            }
        }
        return count;
    }

    public String getDeviceListChecksum() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (DeviceInfo d : deviceRegistry.getAllDevices()) { ids.add(d.getDeviceId()); }
        java.util.Collections.sort(ids);
        String joined = String.join("|", ids);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(joined.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(joined.hashCode());
        }
    }

    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
// ==================== 调试方法 ====================

    /**
     * 打印所有设备信息（用于调试）
     */
    public void printDevices() {
        System.out.println("========== 设备列表 ==========");
        System.out.println("本机设备: " + localDevice);
        System.out.println("发现的设备数量: " + deviceRegistry.getAllDevices().size());

        for (DeviceInfo device : deviceRegistry.getAllDevices()) {
            System.out.println("  - " + device);
        }

        System.out.println("=============================");
    }



}









