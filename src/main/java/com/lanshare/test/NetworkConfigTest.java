package com.lanshare.test;

import com.lanshare.network.config.NetworkConfig;

/**
 * NetworkConfig 测试类
 *
 * @author 主人
 */
public class NetworkConfigTest {

    public static void main(String[] args) {
        System.out.println("========== 测试 NetworkConfig ==========\n");

        // ===== 测试1：打印所有配置 =====
        System.out.println("【测试1】打印默认配置：");
        NetworkConfig.printConfig();
        System.out.println();

        // ===== 测试2：读取配置 =====
        System.out.println("【测试2】读取配置：");
        System.out.println("UDP端口：" + NetworkConfig.getUdpPort());
        System.out.println("TCP端口：" + NetworkConfig.getTcpPort());
        System.out.println("广播间隔：" + NetworkConfig.BROADCAST_INTERVAL + "ms");
        System.out.println("心跳超时：" + NetworkConfig.HEARTBEAT_TIMEOUT + "ms");
        System.out.println("最大连接数：" + NetworkConfig.MAX_CONNECTIONS);
        System.out.println();

        // ===== 测试3：检查端口是否可用 =====
        System.out.println("【测试3】检查端口可用性：");
        int testPort = 8888;
        boolean available = NetworkConfig.isPortAvailable(testPort);
        System.out.println("端口 " + testPort + " 是否可用：" + available);
        System.out.println();

        // ===== 测试4：修改配置 =====
        System.out.println("【测试4】修改配置：");
        try {
            System.out.println("修改UDP端口为 9000...");
            NetworkConfig.setUdpPort(9000);
            System.out.println("修改后的UDP端口：" + NetworkConfig.getUdpPort());

            System.out.println("启用IPv6...");
            NetworkConfig.setIPv6Enabled(true);
            System.out.println("IPv6已启用：" + NetworkConfig.isIPv6Enabled());
            System.out.println("当前广播地址：" + NetworkConfig.getBroadcastAddress());
        } catch (Exception e) {
            System.err.println("修改配置失败：" + e.getMessage());
        }
        System.out.println();

        // ===== 测试5：创建并保存配置文件 =====
        System.out.println("【测试5】创建配置文件：");
        try {
            System.out.println("创建默认配置文件...");
            NetworkConfig.createDefaultConfig();
            System.out.println("配置文件已保存到：lanshare.properties");
            System.out.println("可以手动编辑该文件来修改配置");
        } catch (Exception e) {
            System.err.println("创建配置文件失败：" + e.getMessage());
        }
        System.out.println();

        // ===== 测试6：测试无效端口 =====
        System.out.println("【测试6】测试异常处理：");
        try {
            System.out.println("尝试设置无效端口 999（< 1024）...");
            NetworkConfig.setUdpPort(999);
        } catch (IllegalArgumentException e) {
            System.out.println("✅ 正确捕获异常：" + e.getMessage());
        }

        try {
            System.out.println("尝试设置无效端口 99999（> 65535）...");
            NetworkConfig.setTcpPort(99999);
        } catch (IllegalArgumentException e) {
            System.out.println("✅ 正确捕获异常：" + e.getMessage());
        }
        System.out.println();

        // ===== 测试7：常用配置读取 =====
        System.out.println("【测试7】读取常用配置：");
        System.out.println("文件分片大小：" + formatBytes(NetworkConfig.CHUNK_SIZE));
        System.out.println("最大文件大小：" + formatBytes(NetworkConfig.MAX_FILE_SIZE));
        System.out.println("Socket超时：" + NetworkConfig.SOCKET_TIMEOUT + "ms");
        System.out.println("协议版本：" + NetworkConfig.PROTOCOL_VERSION);
        System.out.println("协议魔数：0x" + Integer.toHexString(NetworkConfig.PROTOCOL_MAGIC).toUpperCase());
        System.out.println();

        System.out.println("========== 测试完成 ==========");
    }

    /**
     * 格式化字节大小
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}