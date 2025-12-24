package com.lanshare.network.protocol;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * 文件分片头部
 * 
 * 二进制协议，固定64字节
 * 用于标识每个文件分片的元数据
 * 
 * 结构：
 * [0-3] Magic Number: 0x4C414E53 ("LANS")
 * [4-5] Version: 0x0001
 * [6-9] Chunk Index: 分片索引
 * [10-13] Chunk Size: 分片大小
 * [14-21] Task ID Hash: 任务ID哈希
 * [22-25] CRC32: 数据校验
 * [26-29] Flags: 标志位
 * [30-63] Reserved: 保留字段
 * 
 * @author ZZZ
 * @version 1.0
 */
public class ChunkHeader {
    public static final int HEADER_SIZE = 64;
    private static final int MAGIC_NUMBER = 0x4C414E53;
    private static final int VERSION = 0x0001;

    public static final int FLAG_LAST_CHUNK = 0x0001;
    public static final int FLAG_RESERVED = 0x0002;

    //=================字段
    private int chunkIndex;//分片索引
    private int chunkSize;//分片数据大小
    private long taskIdHash ;//任务哈希
    private int crc32;//效验值
    private int flags;//标志位

    public ChunkHeader(int chunkIndex, int chunkSize, String taskId) {
        this.chunkIndex = chunkIndex;
        this.chunkSize = chunkSize;
        this.taskIdHash = calculateHash(taskId);
        this.crc32 = 0; // 稍后计算
        this.flags = 0;
    }

    public ChunkHeader() {

    }
    //================序列化为字节数组
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
        buffer.putInt(MAGIC_NUMBER);
        buffer.putShort((short) VERSION);
        buffer.putInt(chunkIndex);
        buffer.putInt(chunkSize);
        buffer.putLong(taskIdHash);
        buffer.putInt(crc32);
        buffer.putInt(flags);
        buffer.put(new byte[34]); // 保留字段
        return buffer.array();
    }

    //================从字节数组反序列化
    public static ChunkHeader fromBytes(byte[] data) throws ProtocolException {
        if (data.length < HEADER_SIZE) {
            throw new ProtocolException("数据太短，至少需要" + HEADER_SIZE + "字节");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int magicNumber = buffer.getInt();
        if (magicNumber != MAGIC_NUMBER) {
            throw new ProtocolException("无效的魔数：" + Integer.toHexString(magicNumber));
        }
        int version = buffer.getShort();
        if (version != VERSION) {
            throw new ProtocolException("无效的版本号：" + Integer.toHexString(version));
        }
        ChunkHeader chunkHeader = new ChunkHeader();
        chunkHeader.chunkIndex = buffer.getInt();
        chunkHeader.chunkSize = buffer.getInt();
        chunkHeader.taskIdHash = buffer.getLong();
        chunkHeader.crc32 = buffer.getInt();
        chunkHeader.flags = buffer.getInt();
        buffer.position(HEADER_SIZE); // 跳过保留字段
        return chunkHeader;
    }
    //===============CRC32效验
    public void calculateCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        this.crc32 = (int) crc32.getValue();
    }

    public boolean verifyCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        int expectedCRC32 = (int) crc32.getValue();
        return this.crc32 == expectedCRC32;
    }
    
    // ==================== 标志位操作 ====================
    
    /**
     * 设置标志位
     * 
     * @param flag 标志位（使用 FLAG_* 常量）
     */
    public void setFlag(int flag) {
        this.flags |= flag;
    }
    
    /**
     * 清除标志位
     * 
     * @param flag 标志位
     */
    public void clearFlag(int flag) {
        this.flags &= ~flag;
    }
    
    /**
     * 检查标志位是否设置
     * 
     * @param flag 标志位
     * @return true 如果设置了该标志
     */
    public boolean hasFlag(int flag) {
        return (this.flags & flag) != 0;
    }
    
    /**
     * 标记为最后一片
     */
    public void markAsLastChunk() {
        setFlag(FLAG_LAST_CHUNK);
    }
    
    /**
     * 是否为最后一片
     */
    public boolean isLastChunk() {
        return hasFlag(FLAG_LAST_CHUNK);
    }
    
    /**
     * 标记为压缩数据
     */
    public void markAsCompressed() {
        setFlag(FLAG_RESERVED);
    }
    
    /**
     * 是否为压缩数据
     */
    public boolean isCompressed() {
        return hasFlag(FLAG_RESERVED);
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 计算字符串的哈希值
     * 
     * @param str 字符串
     * @return 64位哈希值
     */
    private long calculateHash(String str) {
        if (str == null) {
            return 0;
        }
        
        long hash = 0;
        for (int i = 0; i < str.length(); i++) {
            hash = 31 * hash + str.charAt(i);
        }
        return hash;
    }
    
    /**
     * 验证任务ID是否匹配
     * 
     * @param taskId 任务ID
     * @return true 如果匹配
     */
    public boolean matchesTask(String taskId) {
        return this.taskIdHash == calculateHash(taskId);
    }
    
    // ==================== Getter 方法 ====================
    
    public int getChunkIndex() {
        return chunkIndex;
    }
    
    public int getChunkSize() {
        return chunkSize;
    }
    
    public long getTaskIdHash() {
        return taskIdHash;
    }
    
    public int getCrc32() {
        return crc32;
    }
    
    public int getFlags() {
        return flags;
    }
    
    // ==================== toString ====================
    
    @Override
    public String toString() {
        return String.format(
            "ChunkHeader{index=%d, size=%d, taskHash=0x%016X, crc32=0x%08X, flags=0x%08X}",
            chunkIndex, chunkSize, taskIdHash, crc32, flags
        );
    }
}


