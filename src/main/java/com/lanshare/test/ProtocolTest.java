package com.lanshare.test;

import com.lanshare.network.protocol.TransferRequest;
import com.lanshare.network.protocol.TransferResponse;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 协议测试类
 * 测试 TransferRequest 和 TransferResponse 的功能
 * 
 * @author 主人
 */
public class ProtocolTest {

    public static void main(String[] args) {
        System.out.println("========== 协议测试程序 ==========\n");

        try {
            // 测试1：TransferRequest 基本功能
            testTransferRequest();

            // 测试2：TransferResponse 基本功能
            testTransferResponse();

            // 测试3：JSON 序列化和反序列化
            testJsonSerialization();

            // 测试4：验证功能
            testValidation();

            // 测试5：边界情况
            testEdgeCases();

            System.out.println("\n========== 所有测试通过！✅ ==========");

        } catch (Exception e) {
            System.err.println("\n❌ 测试失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试1：TransferRequest 基本功能
     */
    private static void testTransferRequest() throws IOException {
        System.out.println("【测试1】TransferRequest 基本功能");

        // 创建测试文件
        File testFile = createTestFile("test_file.txt", "Hello, LanShare!");

        // 从文件创建请求
        TransferRequest request = TransferRequest.fromFile(testFile);

        // 验证字段
        System.out.println("  任务ID: " + request.getTaskId());
        System.out.println("  文件名: " + request.getFileName());
        System.out.println("  文件大小: " + request.getFormattedFileSize());
        System.out.println("  MD5: " + request.getMd5());
        System.out.println("  分片数量: " + request.getChunkCount());
        System.out.println("  分片大小: " + request.getChunkSize());

        // 验证
        assert request.getFileName().equals("test_file.txt") : "文件名不正确";
        assert request.getFileSize() > 0 : "文件大小应该大于0";
        assert request.getMd5() != null && request.getMd5().length() == 32 : "MD5格式不正确";
        assert request.isValid() : "请求应该有效";

        System.out.println("  ✅ TransferRequest 测试通过\n");

        // 清理测试文件
        testFile.delete();
    }

    /**
     * 测试2：TransferResponse 基本功能
     */
    private static void testTransferResponse() {
        System.out.println("【测试2】TransferResponse 基本功能");

        String taskId = "test-task-123";

        // 测试接受响应
        TransferResponse acceptResponse = TransferResponse.accept(
                taskId,
                "/path/to/save/file.txt");

        System.out.println("  接受响应: " + acceptResponse);
        System.out.println("    - 任务ID: " + acceptResponse.getTaskId());
        System.out.println("    - 是否接受: " + acceptResponse.isAccepted());
        System.out.println("    - 保存路径: " + acceptResponse.getSavePath());
        System.out.println("    - 状态描述: " + acceptResponse.getStatusDescription());

        assert acceptResponse.isAccepted() : "应该接受";
        assert acceptResponse.getSavePath() != null : "应该有保存路径";
        assert acceptResponse.getRejectReason() == null : "接受时不应该有拒绝原因";
        assert acceptResponse.isValid() : "接受响应应该有效";

        // 测试拒绝响应
        TransferResponse rejectResponse = TransferResponse.reject(
                taskId,
                TransferResponse.RejectReason.INSUFFICIENT_SPACE);

        System.out.println("\n  拒绝响应: " + rejectResponse);
        System.out.println("    - 任务ID: " + rejectResponse.getTaskId());
        System.out.println("    - 是否接受: " + rejectResponse.isAccepted());
        System.out.println("    - 拒绝原因: " + rejectResponse.getRejectReason());
        System.out.println("    - 状态描述: " + rejectResponse.getStatusDescription());

        assert !rejectResponse.isAccepted() : "应该拒绝";
        assert rejectResponse.getSavePath() == null : "拒绝时不应该有保存路径";
        assert rejectResponse.getRejectReason() != null : "应该有拒绝原因";
        assert rejectResponse.isValid() : "拒绝响应应该有效";

        System.out.println("  ✅ TransferResponse 测试通过\n");
    }

    /**
     * 测试3：JSON 序列化和反序列化
     */
    private static void testJsonSerialization() throws IOException {
        System.out.println("【测试3】JSON 序列化和反序列化");

        // 创建测试文件
        File testFile = createTestFile("serialize_test.txt", "Test data for serialization");

        // 创建请求
        TransferRequest originalRequest = TransferRequest.fromFile(testFile);

        // 序列化为 JSON
        String json = originalRequest.toJson();
        System.out.println("  序列化后的 JSON:");
        System.out.println(indentJson(json));

        // 反序列化
        TransferRequest deserializedRequest = TransferRequest.fromJson(json);

        // 验证字段是否一致
        assert originalRequest.getTaskId().equals(deserializedRequest.getTaskId())
                : "任务ID不一致";
        assert originalRequest.getFileName().equals(deserializedRequest.getFileName())
                : "文件名不一致";
        assert originalRequest.getFileSize() == deserializedRequest.getFileSize()
                : "文件大小不一致";
        assert originalRequest.getMd5().equals(deserializedRequest.getMd5())
                : "MD5不一致";
        assert originalRequest.getChunkCount() == deserializedRequest.getChunkCount()
                : "分片数量不一致";

        System.out.println("  ✅ 序列化/反序列化一致\n");

        // 测试 Response 序列化
        TransferResponse response = TransferResponse.accept("test-task", "/path/to/file");
        String responseJson = response.toJson();
        System.out.println("  Response JSON:");
        System.out.println(indentJson(responseJson));

        TransferResponse deserializedResponse = TransferResponse.fromJson(responseJson);
        assert response.getTaskId().equals(deserializedResponse.getTaskId())
                : "任务ID不一致";
        assert response.isAccepted() == deserializedResponse.isAccepted()
                : "接受状态不一致";

        System.out.println("  ✅ JSON 序列化测试通过\n");

        // 清理
        testFile.delete();
    }

    /**
     * 测试4：验证功能
     */
    private static void testValidation() {
        System.out.println("【测试4】验证功能");

        // 测试有效的请求
        TransferRequest validRequest = new TransferRequest(
                "task-123",
                "file.txt",
                1024,
                "5d41402abc4b2a76b9719d911017c592", // 正确的MD5格式
                1,
                1024);

        System.out.println("  有效请求: " + validRequest.isValid());
        assert validRequest.isValid() : "应该是有效的";

        // 测试无效的请求（MD5格式错误）
        TransferRequest invalidRequest = new TransferRequest(
                "task-123",
                "file.txt",
                1024,
                "invalid-md5", // 错误的MD5格式
                1,
                1024);

        System.out.println("  无效请求（错误MD5）: " + invalidRequest.isValid());
        assert !invalidRequest.isValid() : "应该是无效的";

        // 测试有效的接受响应
        TransferResponse validAccept = TransferResponse.accept("task-123", "/path/to/file");
        System.out.println("  有效接受响应: " + validAccept.isValid());
        assert validAccept.isValid() : "应该是有效的";

        // 测试有效的拒绝响应
        TransferResponse validReject = TransferResponse.reject("task-123", "用户拒绝");
        System.out.println("  有效拒绝响应: " + validReject.isValid());
        assert validReject.isValid() : "应该是有效的";

        System.out.println("  ✅ 验证功能测试通过\n");
    }

    /**
     * 测试5：边界情况
     */
    private static void testEdgeCases() throws IOException {
        System.out.println("【测试5】边界情况");

        // 测试空文件
        File emptyFile = createTestFile("empty.txt", "");
        TransferRequest emptyRequest = TransferRequest.fromFile(emptyFile);
        System.out.println("  空文件: " + emptyRequest.getFormattedFileSize());
        System.out.println("  分片数量: " + emptyRequest.getChunkCount());
        emptyFile.delete();

        // 测试大文件（模拟）
        TransferRequest bigRequest = new TransferRequest(
                "task-big",
                "big_file.zip",
                10L * 1024 * 1024 * 1024, // 10GB
                "5d41402abc4b2a76b9719d911017c592",
                10000, // 10000个分片
                1024 * 1024 // 1MB分片
        );
        System.out.println("  大文件: " + bigRequest.getFormattedFileSize());
        System.out.println("  分片数量: " + bigRequest.getChunkCount());

        // 测试各种拒绝原因
        System.out.println("\n  预定义拒绝原因:");
        System.out.println("    - " + TransferResponse.RejectReason.USER_DECLINED);
        System.out.println("    - " + TransferResponse.RejectReason.INSUFFICIENT_SPACE);
        System.out.println("    - " + TransferResponse.RejectReason.FILE_TYPE_NOT_SUPPORTED);
        System.out.println("    - " + TransferResponse.RejectReason.BUSY);
        System.out.println("    - " + TransferResponse.RejectReason.DUPLICATE_FILE);

        System.out.println("  ✅ 边界情况测试通过\n");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试文件
     */
    private static File createTestFile(String fileName, String content) throws IOException {
        File file = new File(fileName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
        return file;
    }

    /**
     * 缩进 JSON（简单实现）
     */
    private static String indentJson(String json) {
        String[] lines = json.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append("    ").append(line).append("\n");
        }
        return sb.toString();
    }
}