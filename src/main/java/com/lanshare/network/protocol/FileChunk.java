package com.lanshare.network.protocol;

/**
 * 文件分片
 * 
 * 完整的分片数据包 = ChunkHeader (64 bytes) + Data (可变长度)
 * 
 * 结构：
 * ┌────────────────────────────┐
 * │ ChunkHeader (64 bytes) │
 * ├────────────────────────────┤
 * │ Data (可变长度) │
 * │ [实际的文件内容] │
 * └────────────────────────────┘
 * 
 * 用途：
 * - 网络传输时的基本单元
 * - 包含头部信息和实际数据
 * - 支持校验和验证
 * 
 * @author ZZZ
 * @version 1.0
 */
public class FileChunk {
    public static final int INDEX_CONTROL = -1;

    private ChunkHeader header;
    private byte[] data;

    public FileChunk(int chunkIndex, byte data[], String taskTd) {
        this.header = new ChunkHeader(chunkIndex, data.length, taskTd);
        this.data = data;
        this.header.calculateCRC32(data);
    }

    // 创建控制分片
    public static FileChunk createControlChunk(String command, String taskId) {
        return new FileChunk(INDEX_CONTROL, command.getBytes(java.nio.charset.StandardCharsets.UTF_8), taskId);
    }

    // 检查是否为控制分片
    public boolean isControlChunk() {
        return header.getChunkIndex() == INDEX_CONTROL;
    }

    // 获取控制命令
    public String getControlCommand() {
        if (!isControlChunk()) return null;
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    //私有构造函数，用于反序列化
    private FileChunk(ChunkHeader header, byte[] data) {
        this.header = header;
        this.data = data;
    }

    //序列化
    public byte[] toBytes() {
        byte[] headerBytes = header.toBytes();
        byte[] result = new byte[headerBytes.length + data.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);//复制头部
        System.arraycopy(data, 0, result, headerBytes.length, data.length);//复制数据
        return result;
    }

    //从字节数组反序列化
    public static FileChunk fromBytes(byte[] bytes) throws ProtocolException {
        if (bytes.length < ChunkHeader.HEADER_SIZE) {
            throw new ProtocolException("数据太短，无法解析分片");
        }

        // 1. 解析头部
        byte[] headerBytes = new byte[ChunkHeader.HEADER_SIZE];
        System.arraycopy(bytes, 0, headerBytes, 0, ChunkHeader.HEADER_SIZE);
        ChunkHeader header = ChunkHeader.fromBytes(headerBytes);

        // 2. 提取数据
        int dataLength = bytes.length - ChunkHeader.HEADER_SIZE;
        byte[] data = new byte[dataLength];
        System.arraycopy(bytes, ChunkHeader.HEADER_SIZE, data, 0, dataLength);

        // 3. 验证数据大小
        if (dataLength != header.getChunkSize()) {
            throw new ProtocolException(
                    String.format("数据大小不匹配: 期望 %d 字节, 实际 %d 字节",
                            header.getChunkSize(), dataLength));
        }

        // 4. 验证 CRC32
        if (!header.verifyCRC32(data)) {
            throw new ProtocolException(
                    String.format("CRC32 校验失败: 分片 #%d", header.getChunkIndex()));
        }

        return new FileChunk(header, data);
    }
      
      //=================验证方法
      //验证分片是否有效
    public boolean isValid() {
        if(data.length!=header.getChunkSize()){
          return false;
        }
        if(!header.verifyCRC32(data)){
          return false;
        }
        return true;
      }

      //验证任务ID是否有效
    public boolean matchesTask(String taskId) {
        return header.matchesTask(taskId);
      }

      //工具方法============================
      public int getTotalSize(){
        return ChunkHeader.HEADER_SIZE+data.length;
      }

      //标记为最后一片
    
    public void markAsLastChunk(){
        header.markAsLastChunk();
    }

      //检查是否为最后一片
      public boolean isLastChunk(){
        return header.isLastChunk();
      }

       public String getFormattedSize() {
        return String.format("Header: 64B, Data: %s, Total: %s",
            formatBytes(data.length),
            formatBytes(getTotalSize())
        );
    }
    //格式化字节数为易读字符串
    private String formatBytes(int bytes) {
        if (bytes \u003c 1024) {
            return String.format("%d B", bytes);
        } else if (bytes \u003c 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        }
    }
    //======Getter方法
    public ChunkHeader getHeader() {
        return header;
    }
    public byte[] getData() {
        return data;
    }
    //获取分片索引
    public int getChunkIndex(){
        return header.getChunkIndex();
    }
    public int getDataSize(){
        return data.length;
    }
    public int getChunkSize(){
        return header.getChunkSize();
    }
    //获取任务ID
   @Override
    public String toString() {
        return String.format(
            "FileChunk{index=%d, dataSize=%d, totalSize=%d, valid=%s}",
            getChunkIndex(), data.length, getTotalSize(), isValid()
        );
    }

}
