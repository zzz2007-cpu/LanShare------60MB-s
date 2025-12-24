package com.lanshare.network.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lanshare.network.config.NetworkConfig;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 协议处理器
 * 
 * 封装 TCP Socket 的消息收发，支持：
 * 1. JSON 控制消息（TransferRequest、TransferResponse等）
 * 2. 二进制分片数据（FileChunk）
 * 
 * 特点：
 * - 线程安全：所有方法都是同步的
 * - 长度前缀：每个消息前都有长度字段，解决粘包问题
 * - 自动flush：发送后自动刷新缓冲区
 * - 清晰简洁：去掉了不必要的CRC32（TCP已有校验）
 * 
 * 使用示例：
 * 
 * <pre>
 * // 客户端
 * Socket socket = new Socket("192.168.1.100", 9999);
 * ProtocolHandler handler = new ProtocolHandler(socket);
 * handler.sendJson(request.toJson());
 * String responseJson = handler.receiveJson();
 * 
 * // 服务端
 * ServerSocket server = new ServerSocket(9999);
 * Socket client = server.accept();
 * ProtocolHandler handler = new ProtocolHandler(client);
 * </pre>
 * 
 * @author 主人
 * @version 2.0
 */
public class ProtocolHandler implements Closeable {

    private static final Logger logger = Logger.getLogger(ProtocolHandler.class.getName());

    // ==================== 核心组件 ====================

    /**
     * TCP Socket 连接
     */
    private final Socket socket;

    /**
     * 数据输入流（带缓冲）
     */
    private final DataInputStream input;

    /**
     * 数据输出流（带缓冲）
     */
    private final DataOutputStream output;

    /**
     * Gson 实例（用于 JSON 序列化）
     */
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting() // 美化输出（便于调试）
            .create();

    // ==================== 状态标志 ====================

    /**
     * 是否已关闭
     */
    private volatile boolean closed = false;

    // ==================== 锁对象 ====================
    private final Object sendLock = new Object();
    private final Object receiveLock = new Object();

    // ==================== 构造函数 ====================
    
    /**
     * 构造函数
     * 
     * @param socket TCP Socket（必须已连接）
     * @throws IOException 初始化失败
     */
    public ProtocolHandler(Socket socket) throws IOException {
        if (socket == null || !socket.isConnected()) {
            throw new IllegalArgumentException("Socket 必须已连接");
        }

        this.socket = socket;

        // 设置 TCP 参数
        socket.setTcpNoDelay(true); // 禁用 Nagle 算法，减少延迟
        socket.setSoTimeout(NetworkConfig.SOCKET_READ_TIMEOUT); // 设置读取超时

        // 创建带缓冲的输入输出流
        this.input = new DataInputStream(
                new BufferedInputStream(socket.getInputStream(), NetworkConfig.TCP_BUFFER_SIZE));
        this.output = new DataOutputStream(
                new BufferedOutputStream(socket.getOutputStream(), NetworkConfig.TCP_BUFFER_SIZE));

        logger.info(String.format("ProtocolHandler 已创建: %s:%d",
                socket.getInetAddress().getHostAddress(), socket.getPort()));
    }

    // ==================== JSON 消息收发 ====================

    /**
     * 发送 JSON 字符串
     * 
     * 格式：[4 bytes 长度][N bytes UTF-8 数据]
     * 
     * 注意：
     * - TCP 本身已有校验和，不需要额外的 CRC32
     * - 长度前缀解决粘包问题
     * 
     * @param json JSON 字符串
     * @throws ProtocolException 发送失败
     */
    public void sendJson(String json) throws ProtocolException {
        checkClosed();

        if (json == null || json.isEmpty()) {
            throw new ProtocolException("JSON 不能为空");
        }

        synchronized (sendLock) {
            try {
                // 转换为字节数组
                byte[] data = json.getBytes(StandardCharsets.UTF_8);

                // 发送长度
                output.writeInt(data.length);

                // 发送数据
                output.write(data);

                // 立即刷新
                output.flush();

                logger.fine(String.format("已发送 JSON: %d 字节", data.length));

            } catch (SocketTimeoutException e) {
                throw new ProtocolException("发送 JSON 超时", e);
            } catch (SocketException e) {
                throw new ProtocolException("连接已断开", e);
            } catch (IOException e) {
                throw new ProtocolException("发送 JSON 失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 接收 JSON 字符串
     * 
     * 格式：[4 bytes 长度][N bytes UTF-8 数据]
     * 
     * @return JSON 字符串
     * @throws ProtocolException 接收失败
     */
    public String receiveJson() throws ProtocolException {
        checkClosed();

        synchronized (receiveLock) {
            try {
                // 读取长度
                int length = input.readInt();

                // 验证长度
                if (length <= 0 || length > 10 * 1024 * 1024) { // 最大 10MB
                    throw new ProtocolException(
                            String.format("无效的 JSON 长度: %d (应该在 1 到 10MB 之间)", length));
                }

                // 读取数据
                byte[] data = new byte[length];
                input.readFully(data);

                // 转换为字符串
                String json = new String(data, StandardCharsets.UTF_8);

                logger.fine(String.format("已接收 JSON: %d 字节", length));

                return json;

            } catch (SocketTimeoutException e) {
                throw new ProtocolException("接收 JSON 超时", e);
            } catch (EOFException e) {
                throw new ProtocolException("连接已关闭（对方主动断开）", e);
            } catch (SocketException e) {
                throw new ProtocolException("连接已断开", e);
            } catch (IOException e) {
                throw new ProtocolException("接收 JSON 失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 发送对象（自动序列化为 JSON）
     * 
     * @param object 要发送的对象
     * @throws ProtocolException 发送失败
     */
    public void sendMessage(Object object) throws ProtocolException {
        if (object == null) {
            throw new ProtocolException("对象不能为空");
        }

        String json = gson.toJson(object);
        sendJson(json);
    }

    /**
     * 接收对象（自动从 JSON 反序列化）
     * 
     * @param clazz 对象类型
     * @param <T>   泛型类型
     * @return 对象实例
     * @throws ProtocolException 接收失败
     */
    public <T> T receiveMessage(Class<T> clazz) throws ProtocolException {
        String json = receiveJson();

        try {
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            throw new ProtocolException("JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    // ==================== 二进制分片收发 ====================

    /**
     * 发送文件分片
     * 
     * 格式：[4 bytes 长度][N bytes 分片数据]
     * 
     * 注意：
     * - FileChunk 内部已包含 CRC32 校验
     * - 不需要额外的校验层
     * 
     * @param chunk 文件分片
     * @throws ProtocolException 发送失败
     */
    public void sendChunk(FileChunk chunk) throws ProtocolException {
        checkClosed();

        if (chunk == null) {
            throw new ProtocolException("分片不能为空");
        }

        synchronized (sendLock) {
            try {
                // 序列化分片（头部 64 bytes + 数据）
                byte[] data = chunk.toBytes();

                // 发送长度
                output.writeInt(data.length);

                // 发送数据
                output.write(data);

                // 立即刷新（重要！确保数据立即发送）
                output.flush();

                logger.fine(String.format("已发送分片 #%d: %d 字节",
                        chunk.getChunkIndex(), data.length));

            } catch (SocketTimeoutException e) {
                throw new ProtocolException(
                        String.format("发送分片 #%d 超时", chunk.getChunkIndex()), e);
            } catch (SocketException e) {
                throw new ProtocolException("连接已断开", e);
            } catch (IOException e) {
                throw new ProtocolException(
                        String.format("发送分片 #%d 失败: %s", chunk.getChunkIndex(), e.getMessage()), e);
            }
        }
    }

    /**
     * 接收文件分片
     * 
     * 格式：[4 bytes 长度][N bytes 分片数据]
     * 
     * @return 文件分片
     * @throws ProtocolException 接收失败
     */
    public FileChunk receiveChunk() throws ProtocolException {
        checkClosed();

        synchronized (receiveLock) {
            try {
                // 读取长度
                int length = input.readInt();

                // 验证长度（头部 64 字节 + 数据，最大 2MB）
                if (length < ChunkHeader.HEADER_SIZE || length > 2 * 1024 * 1024) {
                    throw new ProtocolException(
                            String.format("无效的分片长度: %d (应该在 64 到 2MB 之间)", length));
                }

                // 读取数据
                byte[] data = new byte[length];
                input.readFully(data);

                // 反序列化分片（会自动验证 CRC32）
                FileChunk chunk = FileChunk.fromBytes(data);

                logger.fine(String.format("已接收分片 #%d: %d 字节",
                        chunk.getChunkIndex(), length));

                return chunk;

            } catch (SocketTimeoutException e) {
                throw new ProtocolException("接收分片超时", e);
            } catch (EOFException e) {
                throw new ProtocolException("连接已关闭（对方主动断开）", e);
            } catch (SocketException e) {
                throw new ProtocolException("连接已断开", e);
            } catch (IOException e) {
                throw new ProtocolException("接收分片失败: " + e.getMessage(), e);
            }
        }
    }

    // ==================== 连接管理 ====================

    /**
     * 检查连接是否可用
     * 
     * @return true 如果连接正常
     */
    public boolean isConnected() {
        return !closed && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * 获取远程地址
     * 
     * @return IP地址
     */
    public String getRemoteAddress() {
        if (socket != null) {
            return socket.getInetAddress().getHostAddress();
        }
        return "未知";
    }

    /**
     * 获取远程端口
     * 
     * @return 端口号
     */
    public int getRemotePort() {
        if (socket != null) {
            return socket.getPort();
        }
        return 0;
    }

    /**
     * 设置读取超时
     * 
     * @param timeoutMs 超时时间（毫秒）
     * @throws ProtocolException 设置失败
     */
    public void setReadTimeout(int timeoutMs) throws ProtocolException {
        try {
            socket.setSoTimeout(timeoutMs);
        } catch (SocketException e) {
            throw new ProtocolException("设置超时失败", e);
        }
    }

    /**
     * 关闭连接
     * 
     * 注意：
     * - 调用后无法继续使用
     * - 多次调用是安全的
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;

        // 先刷新输出流（确保数据发送完毕）
        try {
            if (output != null) {
                output.flush();
            }
        } catch (IOException e) {
            logger.warning("刷新输出流失败: " + e.getMessage());
        }

        // 关闭输入流
        try {
            if (input != null) {
                input.close();
            }
        } catch (IOException e) {
            logger.warning("关闭输入流失败: " + e.getMessage());
        }

        // 关闭输出流
        try {
            if (output != null) {
                output.close();
            }
        } catch (IOException e) {
            logger.warning("关闭输出流失败: " + e.getMessage());
        }

        // 关闭 Socket
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.warning("关闭 Socket 失败: " + e.getMessage());
        }

        logger.info(String.format("连接已关闭: %s:%d", getRemoteAddress(), getRemotePort()));
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查是否已关闭
     * 
     * @throws ProtocolException 如果已关闭
     */
    private void checkClosed() throws ProtocolException {
        if (closed) {
            throw new ProtocolException("连接已关闭，无法继续操作");
        }

        if (!socket.isConnected() || socket.isClosed()) {
            throw new ProtocolException("Socket 连接已断开");
        }
    }

    /**
     * 检查连接是否仍然活跃（对方未关闭）
     * 注意：此方法会尝试读取数据，只有在协议预期此时无数据传输时才能调用。
     * 
     * @return true 如果连接看起来是活跃的（超时未读到数据）
     */
    public boolean checkConnectionAlive() {
        try {
            int oldTimeout = socket.getSoTimeout();
            try {
                // 设置极短超时
                socket.setSoTimeout(50);
                
                // 尝试读取一个字节
                // 如果对方已关闭，将返回 -1 (EOF)
                // 如果对方发送了数据（违反协议或取消消息），将读到数据
                // 如果连接正常且对方在等待，将抛出 SocketTimeoutException
                input.mark(1);
                int byteRead = input.read();
                
                if (byteRead == -1) {
                    return false; // EOF, 连接已关闭
                }
                
                // 读到了数据，说明对方可能发送了消息（如取消）或者协议错乱
                // 在当前协议下，这意味着连接状态不再单纯是等待
                // 我们尝试回退这个字节，虽然在这个场景下可能不需要
                if (input.markSupported()) {
                    try {
                        input.reset();
                    } catch (IOException ignored) {}
                }
                
                // 如果读到了数据，我们在“连接建立”阶段通常认为这是不正常的（除非我们定义了取消消息）
                // 但为了保守起见，如果读到了数据但没断开，我们暂且认为它活着，
                // 只是这个数据可能会干扰后续的 receiveJson。
                // 鉴于目前 Sender 取消是直接 close，所以这里应该主要是检测 EOF。
                return true;
                
            } catch (SocketTimeoutException e) {
                // 超时意味着没有数据也没有 EOF，这是预期的“等待中”状态
                return true;
            } finally {
                // 恢复超时
                socket.setSoTimeout(oldTimeout);
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== toString ====================

    @Override
    public String toString() {
        return String.format("ProtocolHandler{remote=%s:%d, connected=%s}",
                getRemoteAddress(), getRemotePort(), isConnected());
    }
}