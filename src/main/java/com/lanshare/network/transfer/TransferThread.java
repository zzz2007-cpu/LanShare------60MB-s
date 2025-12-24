package com.lanshare.network.transfer;

import com.lanshare.network.protocol.FileChunk;
import com.lanshare.network.protocol.ProtocolException;
import com.lanshare.network.protocol.ProtocolHandler;
import com.lanshare.network.transfer.TransferTask;
import java.io.IOException;
import java.net.Socket;

/**
 * 传输线程，负责单个文件块的传输
 * 实现了Runnable接口，可以被线程池管理
 * 每个线程处理一个块
 */
public class TransferThread implements Runnable {
    private final TransferTask task;// 传输任务
    private final FileChunk chunk;// 文件块
    private static final int MAX_RETRY_COUNT = 3;// 最大重试次数
    private final long RETRY_DELAY_MS = 1000;// 重试延迟毫秒

    public TransferThread(TransferTask task, FileChunk chunk) {
        this.task = task;
        this.chunk = chunk;
    }

    @Override
    public void run() {
        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            try {
                if (task.getType() == TransferTask.TransferType.SEND) {
                    executeSend();
                    return;
                } else {
                    // executeReceive();
                    return;
                }
            } catch (IOException e) {
                System.err.println(
                        "传输线程" + Thread.currentThread().getName() + "第" + (attempt + 1) + "次重试失败" + e.getMessage());
                if (attempt < MAX_RETRY_COUNT - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);

                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        System.err.println("传输线程" + Thread.currentThread().getName() + "被中断");
                        break;
                    }
                }
            }
        }
        System.err.printf("传输线程" + Thread.currentThread().getName() + "第" + MAX_RETRY_COUNT + "次重试后彻底失败");
    }

    private void executeSend() throws IOException {
        try (Socket socket = new Socket(task.getTargetIp(), task.getTargetPort())) {
            socket.setSoTimeout(10000);// 设置一个合理的超时时间
            // 为每一个线程创建一个独立的ProtoHandler
            ProtocolHandler handler = new ProtocolHandler(socket);
            System.out.printf("正在发送块 #%d...%n", chunk.getChunkIndex());
            handler.sendChunk(chunk);
            // 发送完成即认为成功（TCP可靠传输，服务端会进行CRC验证）
            task.updateProgress(chunk.getDataSize());
            System.out.printf("块 #%d 发送成功，进度: %.2f%%%n",
                    chunk.getChunkIndex(), (double) task.getCurrentProgress() / task.getFileSize() * 100);
        } catch (com.lanshare.network.protocol.ProtocolException e) {
            throw new IOException("协议处理失败: " + e.getMessage(), e);
        }
    }
}
