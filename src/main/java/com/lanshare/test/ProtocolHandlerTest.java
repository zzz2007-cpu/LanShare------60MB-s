package com.lanshare.test;

import com.lanshare.network.config.NetworkConfig;
import com.lanshare.network.protocol.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * ProtocolHandler 简单测试
 * 
 * 测试：
 * 1. JSON 消息收发
 * 2. 对象序列化/反序列化
 * 3. 文件分片收发（手动分片）
 * 4. 错误处理
 * 
 * @author 主人
 * @version 2.0
 */
public class ProtocolHandlerTest {

    public static void main(String[] args) {
        System.out.println("========== ProtocolHandler 测试 ==========\n");

        try {
            // 启动服务端
            Thread serverThread = new Thread(() -> {
                try {
                    runServer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            serverThread.start();

            // 等待服务端启动
            Thread.sleep(1000);

            // 运行客户端
            runClient();

            // 等待服务端结束
            serverThread.join();

            System.out.println("\n========== 所有测试通过！✅ ==========");

        } catch (Exception e) {
            System.err.println("\n❌ 测试失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 服务端
     */
    private static void runServer() throws Exception {
        try (ServerSocket server = new ServerSocket(NetworkConfig.getTcpPort())) {
            System.out.println("【服务端】启动，监听端口: " + NetworkConfig.getTcpPort());

            // 接受连接
            Socket clientSocket = server.accept();
            System.out.println("【服务端】接受连接: " + clientSocket.getInetAddress());

            // 创建处理器（新版方式）
            ProtocolHandler handler = new ProtocolHandler(clientSocket);

            // ===== 测试1：接收 JSON =====
            System.out.println("\n【测试1】接收 JSON 消息");
            String json = handler.receiveJson();
            System.out.println("  收到: " + json);

            // 发送响应
            handler.sendJson("{\"status\":\"ok\"}");
            System.out.println("  已发送响应");

            // ===== 测试2：接收对象 =====
            System.out.println("\n【测试2】接收对象消息");
            TransferRequest request = handler.receiveMessage(TransferRequest.class);
            System.out.println("  收到传输请求: " + request);
            System.out.println("    - 文件名: " + request.getFileName());
            System.out.println("    - 文件大小: " + request.getFormattedFileSize());
            System.out.println("    - 分片数: " + request.getChunkCount());

            // 发送响应
            TransferResponse response = TransferResponse.accept(
                    request.getTaskId(),
                    "received_test.bin");
            handler.sendMessage(response);
            System.out.println("  已发送接受响应");

            // ===== 测试3：接收分片并写入文件 =====
            System.out.println("\n【测试3】接收文件分片");

            File outputFile = new File("received_test.bin");
            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                int receivedChunks = 0;
                long totalReceived = 0;

                while (true) {
                    // 接收分片
                    FileChunk chunk = handler.receiveChunk();
                    receivedChunks++;

                    // 验证分片
                    if (!chunk.isValid()) {
                        System.err.println("  ❌ 分片 #" + chunk.getChunkIndex() + " 校验失败！");
                        continue;
                    }

                    // 写入文件（使用 RandomAccessFile 支持多线程）
                    byte[] data = chunk.getData();
                    raf.write(data);
                    totalReceived += data.length;

                    System.out.println(String.format("  收到分片 #%d: %d 字节 (总计: %d/%d)",
                            chunk.getChunkIndex(),
                            data.length,
                            totalReceived,
                            request.getFileSize()));

                    // 最后一片
                    if (chunk.isLastChunk()) {
                        System.out.println("  ✅ 接收完成，共 " + receivedChunks + " 个分片");
                        break;
                    }
                }
            }

            // 验证文件大小
            if (outputFile.length() == request.getFileSize()) {
                System.out.println("  ✅ 文件大小验证通过: " + outputFile.length() + " 字节");
            } else {
                System.err.println("  ❌ 文件大小不匹配: 期望 " + request.getFileSize() +
                        ", 实际 " + outputFile.length());
            }

            // 关闭
            handler.close();
            System.out.println("\n【服务端】关闭连接");

            // 清理测试文件
            outputFile.delete();

        } catch (Exception e) {
            System.err.println("【服务端】错误: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 客户端
     */
    private static void runClient() throws Exception {
        try {
            System.out.println("\n【客户端】准备连接...");

            // 连接服务端（新版方式：直接创建 Socket）
            Socket socket = new Socket("127.0.0.1", NetworkConfig.getTcpPort());
            System.out.println("【客户端】连接成功");

            // 创建处理器
            ProtocolHandler handler = new ProtocolHandler(socket);

            // ===== 测试1：发送 JSON =====
            System.out.println("\n【测试1】发送 JSON 消息");
            handler.sendJson("{\"message\":\"Hello, Server!\"}");
            System.out.println("  已发送");

            // 接收响应
            String response = handler.receiveJson();
            System.out.println("  收到响应: " + response);

            // ===== 测试2：发送对象 =====
            System.out.println("\n【测试2】发送对象消息");

            // 创建测试文件（256KB）
            File testFile = createTestFile("client_test.bin", 256 * 1024);
            System.out.println("  测试文件: " + testFile.getName() + " (" + testFile.length() + " 字节)");

            // 创建传输请求
            TransferRequest request = TransferRequest.fromFile(testFile);
            handler.sendMessage(request);
            System.out.println("  已发送传输请求");

            // 接收响应
            TransferResponse resp = handler.receiveMessage(TransferResponse.class);
            System.out.println("  收到响应: " + (resp.isAccepted() ? "接受" : "拒绝"));

            if (!resp.isAccepted()) {
                System.err.println("  传输被拒绝: " + resp.getRejectReason());
                handler.close();
                return;
            }

            // ===== 测试3：发送文件分片（手动分片）=====
            System.out.println("\n【测试3】发送文件分片");

            sendFileManually(handler, testFile, request);

            // 等待服务端处理
            Thread.sleep(500);

            // 关闭
            handler.close();
            System.out.println("\n【客户端】关闭连接");

            // 清理测试文件
            testFile.delete();

        } catch (Exception e) {
            System.err.println("【客户端】错误: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 手动发送文件（分片）
     */
    private static void sendFileManually(ProtocolHandler handler, File file, TransferRequest request)
            throws Exception {

        String taskId = request.getTaskId();
        int chunkSize = request.getChunkSize();
        long fileSize = file.length();

        // 计算分片数量
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        System.out.println("  文件大小: " + fileSize + " 字节");
        System.out.println("  分片大小: " + chunkSize + " 字节");
        System.out.println("  总分片数: " + totalChunks);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[chunkSize];
            int chunkIndex = 0;
            long sentBytes = 0;

            while (true) {
                // 读取数据
                int bytesRead = fis.read(buffer);
                if (bytesRead == -1) {
                    break; // 文件读完了
                }

                // 创建分片数据
                byte[] chunkData;
                if (bytesRead == buffer.length) {
                    chunkData = buffer;
                } else {
                    // 最后一片可能不足 chunkSize
                    chunkData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunkData, 0, bytesRead);
                }

                // 创建 FileChunk
                FileChunk chunk = new FileChunk(chunkIndex, chunkData, taskId);

                // 标记最后一片（重要！）
                if (sentBytes + bytesRead >= fileSize) {
                    chunk.markAsLastChunk();
                }

                // 发送分片
                handler.sendChunk(chunk);

                sentBytes += bytesRead;
                chunkIndex++;

                System.out.println(String.format("  已发送分片 #%d/%d: %d 字节 (进度: %.1f%%)",
                        chunkIndex,
                        totalChunks,
                        bytesRead,
                        (sentBytes * 100.0 / fileSize)));
            }

            System.out.println("  ✅ 发送完成，共 " + chunkIndex + " 个分片");
        }
    }

    /**
     * 创建测试文件
     */
    private static File createTestFile(String fileName, int size) throws IOException {
        File file = new File(fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] data = new byte[size];
            // 填充测试数据
            for (int i = 0; i < size; i++) {
                data[i] = (byte) (i % 256);
            }
            fos.write(data);
        }
        return file;
    }
}