package com.lanshare.network.config;
import java.io.*;
import java.util.Properties;
import java.util.logging.Logger;
public class NetworkConfig {
    //日志记录器
    private static final Logger logger=Logger.getLogger(NetworkConfig.class.getName());

    //-----UDP配置---------------------------------------
    //网络端口号
    public static final int UDP_PORT=8888;

    //广播间隔时间
    public static final int BROADCAST_INTERVAL=5000;
    //UDP接收缓冲区
    public static final int UDP_BUFFER_SIZE=1024;

    //-----------------------TCP配置------------------------
    //TCP文件传输端口
    public static final int TCP_PORT=9999;
    //Socket连接超时
    public static final int SOCKET_TIMEOUT=30000;
    //Socket读取超时
    public static final int SOCKET_READ_TIMEOUT=60000;
    //TCP接收缓冲区大小(字节）
    public static final int TCP_BUFFER_SIZE=8192;

    //------心跳配置-------------------------------
    //心跳间隔
    public static final int HEARTBEAT_INTERVAL=5000;
    //心跳超时
    public static final int HEARTBEAT_TIMEOUT=30000;
    //心跳失败重试次数
    public static final int HEARTBEAY_RETRY_COUNT=3;

    //--------------------------------设备发现配置值---------
    //设备超时时间，30s没有收到消息就标记为离线
    public static final int DEVICE_TIMEOUT=30000;
    //设备清理时间，2分钟后彻底删除离线设备
    public static final int DEVICE_CLEANUP_TIME=120000;
    //设备状态检查,10s检查一次设备状态
    public static final int DEVICE_CHECK_INTERVAL=10000;

    //----------------连接池配置--------------------
    //最大连接数，最多同时保持10个TCP连接
    public static final int MAX_CONNECTIONS=10;
    //连接空闲超时，5分钟没用就关闭连接
    public static final long CONNECTION_IDLE_TIMEOUT=300000;
    //连接池清理，每过30s清理一次过期连接
    public static final long CONNECTION_CLEANUP_INTERVAL=30000;

    //------------------传输配置------------------------
    //默认传输线程数
    public static final int DEFAULT_TRANSFER_THREADS=4;
    //最大传输线程数
    public static final int MAX_TRANSFER_THREADS=8;
    //文档分片（字节）每个1MB
    public static final int CHUNK_SIZE=1024*1024;
    //文档分片最大10GB
    public static final long MAX_FILE_SIZE=10L*1024*1024*1024;


    //--------线程池配置---------------------
    //核心线程池大小
    public static final int CORE_POOL_SIZE=5;
    //最大线程池大小
    public static final int MAX_POOL_SIZE=20;
    //线程空闲时间
    public static final int THREAD_KEEP_ALIVE_TIME=60;

//-----------------------广播地址配置
    //IPV4多播地址
    public static final String BROADCAST_ADDRESS_IPV4="255.255.255.255";
    //IPV6多播地址
    public static final String MULTICAST_ADDRESS_IPV6="0.0.0.0";

    //-----------------------------协议版本--------------------
    //协议版本号
    public static final String  PROTOCOL_VERSION="1.0";
    //协议魔数
    public static final int PROTOCOL_MAGIC=0x4C414E53;//lans，每个数据包都哦带上，代表是发送的数据
    //-------------------------------------可配置参数------------------------
    //可以从配置文件中读取参数，而不是写死
    private static Properties properties=new Properties();
    private static final String CONFIG_FILE="lansshare.properties";
    //用户配置的UDP端口
    private static Integer customUdpPort=null;
    //TCP端口
    private static Integer customTcpPort=null;
    //是否启用IPV6
    private static Boolean enableIPv6=false;

    static{
        loadConfig();
    }

   public static void loadConfig(){
        File configFile=new File(CONFIG_FILE);
        if(!configFile.exists()){
            logger.info("配置文件不存在，使用默认配置");
            return;
        }

        try(FileInputStream fis=new FileInputStream(configFile)){
            properties.load(fis);

            String udpPort=properties.getProperty("udp.Port");
            if(udpPort!=null){
                customUdpPort=Integer.valueOf(udpPort);
                logger.info("使用自定义端口"+customUdpPort);
            }
            String tcpPort=properties.getProperty("tcp.Port");
            if(tcpPort!=null){
                customTcpPort=Integer.valueOf(tcpPort);
                logger.info("使用自定义TCP端口"+customTcpPort);
            }
            String ipv6=properties.getProperty("ipv6.Port");
            if(ipv6!=null){
                enableIPv6=Boolean.valueOf(ipv6);
                logger.info("IPV6支持:"+enableIPv6);
            }
            logger.info("配置文件加载完成");

        }catch(IOException e){
            logger.warning("加载配置文件失败"+e.getMessage());
       }catch(NumberFormatException e){
            logger.warning("配置文件格式错误"+e.getMessage());
        }

   }
    /**
     * 保存配置到文件
     */
    public static void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {

            // 设置注释
            properties.store(fos, "LanShare Network Configuration");

            logger.info("配置文件保存成功");

        } catch (IOException e) {
            logger.severe("保存配置文件失败：" + e.getMessage());
        }
    }

    /**
     * 创建默认配置文件
     */
    public static void createDefaultConfig() {
        properties.setProperty("udp.port", String.valueOf(UDP_PORT));
        properties.setProperty("tcp.port", String.valueOf(TCP_PORT));
        properties.setProperty("enable.ipv6", "false");
        properties.setProperty("max.connections", String.valueOf(MAX_CONNECTIONS));
        properties.setProperty("transfer.threads", String.valueOf(DEFAULT_TRANSFER_THREADS));

        saveConfig();
    }

   //---------------------getter方法---------------------------

    public static int getUdpPort() {
        return customUdpPort != null ? customUdpPort : UDP_PORT;
    }


    public static int getTcpPort() {
        return customTcpPort != null ? customTcpPort : TCP_PORT;
    }


    public static boolean isIPv6Enabled() {
        return enableIPv6;
    }


    public static String getBroadcastAddress() {
        return enableIPv6 ? MULTICAST_ADDRESS_IPV6 : BROADCAST_ADDRESS_IPV4;
    }
//------------------------------setter方法---------------------------------------------
    //设置自定义UDP端口
    public static void setUdpPort(int port) {
        if(port<1024||port>65535){
            throw new IllegalArgumentException("端口号必须在1024-65535之间");
        }
        customUdpPort=port;
        properties.setProperty("udp.Port",String.valueOf(port));
    }
    public static void setTcpPort(int port) {
        if(port<1024||port>65535){
            throw new IllegalArgumentException("端口号必须在1024-65535之间");
        }
        customTcpPort=port;
        properties.setProperty("tcp.Port",String.valueOf(port));
    }

    public static void setIPv6Enabled(boolean enable) {
        enableIPv6=enable;
        properties.setProperty("enable.ipv6",String.valueOf(enable));
    }
    // ==================== 工具方法 ====================

    /**
     * 验证端口是否可用
     */
    public static boolean isPortAvailable(int port) {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 打印所有配置信息（用于调试）
     */
    public static void printConfig() {
        System.out.println("========== LanShare 网络配置 ==========");
        System.out.println("UDP端口: " + getUdpPort());
        System.out.println("TCP端口: " + getTcpPort());
        System.out.println("广播间隔: " + BROADCAST_INTERVAL + "ms");
        System.out.println("心跳间隔: " + HEARTBEAT_INTERVAL + "ms");
        System.out.println("设备超时: " + DEVICE_TIMEOUT + "ms");
        System.out.println("最大连接数: " + MAX_CONNECTIONS);
        System.out.println("传输线程数: " + DEFAULT_TRANSFER_THREADS);
        System.out.println("分片大小: " + CHUNK_SIZE + " bytes");
        System.out.println("IPv6支持: " + enableIPv6);
        System.out.println("协议版本: " + PROTOCOL_VERSION);
        System.out.println("======================================");
    }


    // ==================== 私有构造函数（防止实例化） ====================

    private NetworkConfig() {
        // 工具类，不允许实例化
        throw new AssertionError("NetworkConfig 是工具类，不能实例化");
    }
}




