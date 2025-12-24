package com.lanshare.network.transfer;

import com.lanshare.network.protocol.TransferRequest;
import com.lanshare.network.protocol.FileChunk;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TransferTask implements Comparable<TransferTask> {
    /**
     * 传输任务类
     * 支持发送和接受类型
     * 多线程传输的基础数据结构
     */
    public enum TransferType {
        SEND, RECEIVE
    }

    public enum TaskStatus {
        PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED
    }

    @FunctionalInterface
    public interface TransferProgressListener {
        void onProgress(String taskId, long bytesTransferred, long totalBytes);
    }

    private final String taskId;
    private final String filePath;
    private final String targetIp;// 目标Ip
    private final int targetPort;// 目标端口
    private final TransferType type;// 传输类型
    private final TransferRequest request;// 传输请求
    private final long fileSize;
    private final AtomicLong currentProgress = new AtomicLong(0);// 当前进度
    private final TransferProgressListener progressListener;// 进度回调（可选）

    private volatile TaskStatus status = TaskStatus.PENDING;
    private final Object pauseLock = new Object();

    public TransferTask(String taskId, String filePath, String targetIp, int targetPort, TransferType type,
            TransferRequest request) {
        this(taskId, filePath, targetIp, targetPort, type, request, null);
    }

    public TransferTask(String taskId, String filePath, String targetIp, int targetPort, TransferType type,
            TransferRequest request, TransferProgressListener progressListener) {
        this.taskId = taskId;
        this.filePath = filePath;
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.type = type;
        this.request = request;
        this.fileSize = calculateFileSize(filePath);
        this.progressListener = progressListener;
    }

    /**
     * 计算文件大小
     * 
     * @param filePath 文件路径
     * @return 文件大小（字节）
     */
    private long calculateFileSize(String filePath) {
        File file = new File(filePath);
        return file.exists() ? file.length() : 0;
    }

    /**
     * 文档分块
     * 
     * @param chunkSize 分块大小（字节）
     * @return 分块列表
     * @throws IOException 文件不存在或大小为0
     */
    public List<FileChunk> getChunks(int chunkSize) throws IOException {
        List<FileChunk> chunks = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists() || fileSize == 0) {
            return chunks;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long offset = 0;
            int chunkId = 0;
            while (offset < fileSize) {
                long remaining = fileSize - offset;
                int size = (int) Math.min(chunkSize, remaining);
                byte[] data = new byte[size];
                raf.seek(offset);
                raf.readFully(data);

                FileChunk chunk = new FileChunk(chunkId, data, this.taskId);
                chunks.add(chunk);
                offset += size;
                chunkId++;
            }
        }
        return chunks;
    }

    // 更新传输进度
    public void updateProgress(long transferred) {
        long newProgress = currentProgress.addAndGet(transferred);
        if (progressListener != null) {
            progressListener.onProgress(taskId, newProgress, fileSize);
        }
    }

    // 设置初始进度（断点续传用）
    public void setInitialProgress(long initial) {
        currentProgress.set(initial);
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void pause() {
        if (status == TaskStatus.RUNNING) {
            status = TaskStatus.PAUSED;
        }
    }

    public void resume() {
        if (status == TaskStatus.PAUSED) {
            synchronized (pauseLock) {
                status = TaskStatus.RUNNING;
                pauseLock.notifyAll();
            }
        }
    }

    public void cancel() {
        status = TaskStatus.CANCELED;
        synchronized (pauseLock) {
            pauseLock.notifyAll(); // 唤醒以便退出
        }
    }

    public void waitForResume() throws InterruptedException {
        synchronized (pauseLock) {
            while (status == TaskStatus.PAUSED) {
                pauseLock.wait();
            }
        }
    }

    // 当前任务是否成功
    public boolean isCompleted() {
        return currentProgress.get() >= fileSize;
    }

    @Override
    public int compareTo(TransferTask other) {
        return Long.compare(this.fileSize, other.fileSize);
    }

    // =====================getter方法
    public String getTaskId() {
        return taskId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public TransferType getType() {
        return type;
    }

    public TransferRequest getRequest() {
        return request;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getCurrentProgress() {
        return currentProgress.get();
    }
}
