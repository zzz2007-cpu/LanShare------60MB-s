package com.lanshare.test;

import com.lanshare.network.protocol.ChunkHeader;
import com.lanshare.network.protocol.FileChunk;
import com.lanshare.network.protocol.ProtocolException;

import java.util.Arrays;

/**
 * 二进制协议测试
 * 测试 ChunkHeader 和 FileChunk 的功能
 * 
 * @author 主人
 */
public class BinaryProtocolTest {

    public static void main(String[] args) {
        System.out.println("========== 二进制协议测试 ==========\n");

        try {
            // 测试1：ChunkHeader 序列化/反序列化
            testChunkHeaderSerialization();

            // 测试2：CRC32 校验
            testCRC32Verification();

            // 测试3：标志位操作
            testFlags();

            // 测试4：FileChunk 序列化/反序列化
            testFileChunkSerialization();

            // 测试5：错误检测
            testErrorDetection();

            // 测试6：大数据分片
            testLargeChunk();

            System.out.println("\n========== 所有测试通过！✅ ==========");

        } catch (Exception e) {
            System.err.println("\n❌ 测试失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 测试1：ChunkHeader 序列化/反序列化
     */
    private static void testChunkHeaderSerialization() throws ProtocolException {
        System.out.println("【测试1】ChunkHeader 序列化/反序列化");

        String taskId = "test-task-12345";
        int chunkIndex = 42;
        int chunkSize = 1048576; // 1MB

        // 创建头部
        ChunkHeader original = new ChunkHeader(chunkIndex, chunkSize, taskId);
        original.calculateCRC32(new byte[] { 1, 2, 3, 4, 5 });

        System.out.println("  原始头部: " + original);

        // 序列化
        byte[] bytes = original.toBytes();
        System.out.println("  序列化后大小: " + bytes.length + " 字节");
        assert bytes.length == ChunkHeader.HEADER_SIZE : "头部大小应该是64字节";

        // 反序列化
        ChunkHeader deserialized = ChunkHeader.fromBytes(bytes);
        System.out.println("  反序列化后: " + deserialized);

        // 验证字段
        assert original.getChunkIndex() == deserialized.getChunkIndex() : "分片索引不一致";
        assert original.getChunkSize() == deserialized.getChunkSize() : "分片大小不一致";
        assert original.getTaskIdHash() == deserialized.getTaskIdHash() : "任务哈希不一致";
        assert original.getCrc32() == deserialized.getCrc32() : "CRC32不一致";

        System.out.println("  ✅ 序列化/反序列化一致\n");
    }

    /**
     * 测试2：CRC32 校验
     */
    private static void testCRC32Verification() {
        System.out.println("【测试2】CRC32 校验");

        String taskId = "test-task";
        byte[] data = "Hello, LanShare!".getBytes();

        // 创建头部并计算CRC
        ChunkHeader header = new ChunkHeader(0, data.length, taskId);
        header.calculateCRC32(data);

        System.out.println("  数据: " + new String(data));
        System.out.println("  CRC32: 0x" + Integer.toHexString(header.getCrc32()).toUpperCase());

        // 验证正确的数据
        boolean validOrig = header.verifyCRC32(data);
        System.out.println("  验证原始数据: " + (validOrig ? "✅ 通过" : "❌ 失败"));
        assert validOrig : "原始数据校验应该通过";

        // 验证损坏的数据
        byte[] corruptedData = data.clone();
        corruptedData[0] = (byte) ~corruptedData[0]; // 损坏第一个字节

        boolean validCorrupt = header.verifyCRC32(corruptedData);
        System.out.println("  验证损坏数据: " + (validCorrupt ? "❌ 通过" : "✅ 失败"));
        assert !validCorrupt : "损坏的数据校验应该失败";

        System.out.println("  ✅ CRC32 校验工作正常\n");
    }

    /**
     * 测试3：标志位操作
     */
    private static void testFlags() {
        System.out.println("【测试3】标志位操作");

        ChunkHeader header = new ChunkHeader(0, 1024, "test");

        // 初始状态
        System.out.println("  初始标志: 0x" + Integer.toHexString(header.getFlags()).toUpperCase());
        assert !header.isLastChunk() : "初始应该不是最后一片";
        assert !header.isCompressed() : "初始应该不是压缩";

        // 设置最后一片标志
        header.markAsLastChunk();
        System.out.println("  设置最后一片: 0x" + Integer.toHexString(header.getFlags()).toUpperCase());
        assert header.isLastChunk() : "应该是最后一片";
        assert !header.isCompressed() : "不应该是压缩";

        // 设置压缩标志
        header.markAsCompressed();
        System.out.println("  设置压缩: 0x" + Integer.toHexString(header.getFlags()).toUpperCase());
        assert header.isLastChunk() : "仍然应该是最后一片";
        assert header.isCompressed() : "应该是压缩";

        // 清除标志
        header.clearFlag(ChunkHeader.FLAG_LAST_CHUNK);
        System.out.println("  清除最后一片: 0x" + Integer.toHexString(header.getFlags()).toUpperCase());
        assert !header.isLastChunk() : "不应该是最后一片";
        assert header.isCompressed() : "仍然应该是压缩";

        System.out.println("  ✅ 标志位操作正常\n");
    }

    /**
     * 测试4：FileChunk 序列化/反序列化
     */
    private static void testFileChunkSerialization() throws ProtocolException {
        System.out.println("【测试4】FileChunk 序列化/反序列化");

        String taskId = "test-task-abc123";
        int chunkIndex = 5;
        byte[] data = "This is test data for FileChunk serialization!".getBytes();

        // 创建分片
        FileChunk original = new FileChunk(chunkIndex, data, taskId);

        System.out.println("  原始分片: " + original);
        System.out.println("  大小信息: " + original.getFormattedSize());

        // 序列化
        byte[] bytes = original.toBytes();
        int expectedSize = ChunkHeader.HEADER_SIZE + data.length;
        System.out.println("  序列化后大小: " + bytes.length + " 字节 (期望: " + expectedSize + ")");
        assert bytes.length == expectedSize : "总大小不正确";

        // 反序列化
        FileChunk deserialized = FileChunk.fromBytes(bytes);
        System.out.println("  反序列化后: " + deserialized);

        // 验证字段
        assert original.getChunkIndex() == deserialized.getChunkIndex() : "分片索引不一致";
        assert original.getDataSize() == deserialized.getDataSize() : "数据大小不一致";
        assert Arrays.equals(original.getData(), deserialized.getData()) : "数据内容不一致";
        assert deserialized.isValid() : "反序列化后应该有效";

        System.out.println("  ✅ FileChunk 序列化/反序列化一致\n");
    }

    /**
     * 测试5：错误检测
     */
    private static void testErrorDetection() {
        System.out.println("【测试5】错误检测");

        // 测试无效的魔数
        try {
            byte[] invalidMagic = new byte[64];
            invalidMagic[0] = 0x00; // 错误的魔数
            ChunkHeader.fromBytes(invalidMagic);
            assert false : "应该抛出异常";
        } catch (ProtocolException e) {
            System.out.println("  ✅ 正确检测到无效魔数: " + e.getMessage());
        }

        // 测试数据太短
        try {
            byte[] shortData = new byte[10];
            ChunkHeader.fromBytes(shortData);
            assert false : "应该抛出异常";
        } catch (ProtocolException e) {
            System.out.println("  ✅ 正确检测到数据太短: " + e.getMessage());
        }

        // 测试 CRC 校验失败
        try {
            String taskId = "test";
            byte[] data = "Original data".getBytes();
            FileChunk chunk = new FileChunk(0, data, taskId);

            // 序列化
            byte[] bytes = chunk.toBytes();

            // 损坏数据部分
            bytes[ChunkHeader.HEADER_SIZE + 5] = (byte) ~bytes[ChunkHeader.HEADER_SIZE + 5];

            // 尝试反序列化
            FileChunk.fromBytes(bytes);
            assert false : "应该抛出异常";
        } catch (ProtocolException e) {
            System.out.println("  ✅ 正确检测到 CRC 校验失败: " + e.getMessage());
        }

        System.out.println("  ✅ 错误检测正常\n");
    }

    /**
     * 测试6：大数据分片
     */
    private static void testLargeChunk() throws ProtocolException {
        System.out.println("【测试6】大数据分片");

        // 创建 1MB 的测试数据
        int dataSize = 1024 * 1024; // 1MB
        byte[] largeData = new byte[dataSize];
        for (int i = 0; i < dataSize; i++) {
            largeData[i] = (byte) (i % 256);
        }

        System.out.println("  数据大小: " + formatBytes(dataSize));

        // 创建分片
        long startTime = System.currentTimeMillis();
        FileChunk chunk = new FileChunk(99, largeData, "large-task");
        long createTime = System.currentTimeMillis() - startTime;

        System.out.println("  创建耗时: " + createTime + " ms");
        System.out.println("  分片信息: " + chunk.getFormattedSize());

        // 序列化
        startTime = System.currentTimeMillis();
        byte[] bytes = chunk.toBytes();
        long serializeTime = System.currentTimeMillis() - startTime;

        System.out.println("  序列化耗时: " + serializeTime + " ms");
        System.out.println("  序列化后大小: " + formatBytes(bytes.length));

        // 反序列化
        startTime = System.currentTimeMillis();
        FileChunk deserialized = FileChunk.fromBytes(bytes);
        long deserializeTime = System.currentTimeMillis() - startTime;

        System.out.println("  反序列化耗时: " + deserializeTime + " ms");

        // 验证
        assert deserialized.isValid() : "大分片应该有效";
        assert Arrays.equals(largeData, deserialized.getData()) : "数据应该一致";

        System.out.println("  ✅ 大数据分片测试通过\n");
    }

    // ==================== 辅助方法 ====================

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