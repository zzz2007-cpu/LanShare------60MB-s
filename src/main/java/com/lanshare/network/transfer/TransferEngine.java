package com.lanshare.network.transfer;

import com.lanshare.network.config.NetworkConfig;
import com.lanshare.network.protocol.FileChunk;
import com.lanshare.network.protocol.ProtocolHandler;
import com.lanshare.network.protocol.TransferResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class TransferEngine {
    /**
     * 传输引擎
     * 1。维护一个线程池执行传输任务
     * 2.接受Task，分成多个FIleTask
     * 3.为每一个Task分配一个TransferThread
     * 4.将TransferThread提交给线程池执行
     * 5.跟踪任务进度和状态
     */
    private final ExecutorService executorService;
    //键为任务ID，值为任务对象
    private final Map<String, TransferTask> activeTasks;

    private final Map<String, List<Future<?>>> taskFutures;

    private static final int DEFAU_CHUNK_SIZE = NetworkConfig.CHUNK_SIZE;

    public TransferEngine(int poolSize) {
        this.executorService = Executors.newFixedThreadPool(poolSize);
        this.activeTasks = new ConcurrentHashMap<>();
        this.taskFutures = new ConcurrentHashMap<>();
        System.out.println("【传输引擎】已初始化，线程池大小: " + poolSize);
    }

    public TransferTask getTask(String taskId) {
        return activeTasks.get(taskId);
    }

    /**
     * 手动注册一个正在运行的任务（例如接收任务）
     */
    public void registerActiveTask(TransferTask task) {
        if (task != null && !activeTasks.containsKey(task.getTaskId())) {
            activeTasks.put(task.getTaskId(), task);
        }
    }

    /**
     * 移除任务
     */
    public void removeActiveTask(String taskId) {
        activeTasks.remove(taskId);
    }

    //提交一个新的传输任务（共享单连接，流式发送分片）
    public CompletableFuture<Void> submitTask(TransferTask task) {
        if (activeTasks.containsKey(task.getTaskId())) {
            System.out.println("【传输引擎】任务ID已存在: " + task.getTaskId());
            return CompletableFuture.failedFuture(new IllegalArgumentException("任务ID已存在"));
        }
        activeTasks.put(task.getTaskId(), task);
        taskFutures.put(task.getTaskId(), new ArrayList<>());
        System.out.println("提交新任务: " + task.getTaskId() + "，文件: " + task.getFilePath());

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            task.setStatus(TransferTask.TaskStatus.RUNNING);
            try (Socket socket = new Socket(task.getTargetIp(), task.getTargetPort());
                 ProtocolHandler handler = new ProtocolHandler(socket);
                 RandomAccessFile raf = new RandomAccessFile(task.getFilePath(), "r")) {

                // 握手：发送请求并等待响应
                handler.sendMessage(task.getRequest());
                TransferResponse response = handler.receiveMessage(TransferResponse.class);
                if (response == null || !response.isAccepted()) {
                    task.setStatus(TransferTask.TaskStatus.FAILED);
                    throw new IOException("对方拒绝传输或响应为空");
                }

                long fileSize = task.getFileSize();
                long offset = response.getExistingOffset(); // 支持断点续传
                
                // 如果对方说已经传完了，直接结束
                if (offset >= fileSize) {
                    task.updateProgress(fileSize - task.getCurrentProgress()); // 补齐进度
                    task.setStatus(TransferTask.TaskStatus.COMPLETED);
                    System.out.println("文件已存在，跳过传输");
                    return;
                }
                
                // 设置初始进度
                task.setInitialProgress(offset);
                int chunkIndex = (int) (offset / DEFAU_CHUNK_SIZE); // 简单估算，或者不依赖chunkIndex顺序
                // 注意：如果chunkSize变了，这里的index可能不准，但只要offset对就行
                
                // 启动监听线程处理接收方的控制消息
                CompletableFuture<Void> listenerFuture = CompletableFuture.runAsync(() -> {
                    try {
                        while (!task.getStatus().equals(TransferTask.TaskStatus.COMPLETED) && 
                               !task.getStatus().equals(TransferTask.TaskStatus.FAILED) && 
                               !task.getStatus().equals(TransferTask.TaskStatus.CANCELED) && 
                               handler.isConnected()) {
                            try {
                                String json = handler.receiveJson();
                                JsonObject obj = new Gson().fromJson(json, JsonObject.class);
                                if (obj.has("type")) {
                                    String type = obj.get("type").getAsString();
                                    if ("PAUSE".equals(type)) {
                                        task.pause();
                                    } else if ("RESUME".equals(type)) {
                                        task.resume();
                                    }
                                }
                            } catch (Exception e) {
                                // 忽略读取错误（可能是连接关闭）
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }, executorService);
                
                System.out.println("开始传输: " + task.getTaskId() + " offset=" + offset);

                boolean lastWasPaused = false;
                while (offset < fileSize) {
                    // 检查暂停或取消状态
                    if (task.getStatus() == TransferTask.TaskStatus.CANCELED) {
                        listenerFuture.cancel(true);
                        throw new IOException("任务已取消");
                    }
                    
                    if (task.getStatus() == TransferTask.TaskStatus.PAUSED) {
                        if (!lastWasPaused) {
                             // 发送暂停信号
                             handler.sendChunk(FileChunk.createControlChunk("PAUSE", task.getTaskId()));
                             lastWasPaused = true;
                        }
                        task.waitForResume();
                        
                        // 唤醒后再次检查
                        if (task.getStatus() == TransferTask.TaskStatus.RUNNING && lastWasPaused) {
                             // 发送恢复信号
                             handler.sendChunk(FileChunk.createControlChunk("RESUME", task.getTaskId()));
                             lastWasPaused = false;
                        }
                    }

                    long remaining = fileSize - offset;
                    int size = (int) Math.min(DEFAU_CHUNK_SIZE, remaining);
                    byte[] data = new byte[size];
                    raf.seek(offset);
                    raf.readFully(data);

                    FileChunk chunk = new FileChunk(chunkIndex, data, task.getTaskId());
                    if (offset + size >= fileSize) {
                        chunk.markAsLastChunk();
                    }
                    handler.sendChunk(chunk);
                    task.updateProgress(chunk.getDataSize());

                    offset += size;
                    chunkIndex++;
                }

                task.setStatus(TransferTask.TaskStatus.COMPLETED);
                System.out.println("任务 " + task.getTaskId() + " 发送完成");

            } catch (Exception e) {
                if (task.getStatus() != TransferTask.TaskStatus.CANCELED) {
                    task.setStatus(TransferTask.TaskStatus.FAILED);
                }
                throw new CompletionException(e);
            }
        }, executorService);

        taskFutures.get(task.getTaskId()).add(future);

        return future.whenComplete((v, ex) -> {
            if (ex != null) {
                System.err.println("任务 " + task.getTaskId() + " 失败: " + ex.getCause().getMessage());
            }
            activeTasks.remove(task.getTaskId());
            taskFutures.remove(task.getTaskId());
        });
    }

    //取消一个任务
    public void cancelTask(String taskId) {
        List<Future<?>> futures = taskFutures.get(taskId);
        if (futures != null) {
            for (Future<?> future : futures) {
                future.cancel(true);
            }
        }
        //从活跃任务列表中移除
        activeTasks.remove(taskId);
        taskFutures.remove(taskId);
    }

    //获取任务的当前进度
    public double getTaskProgress(String taskId) {
        TransferTask task = activeTasks.get(taskId);
        if (task != null && task.getFileSize() > 0) {
            return (double) task.getCurrentProgress() / task.getFileSize() * 100.0;
        }
        return -1.0;
    }

    public void shutdown() {
        System.out.println("正在关闭传输引擎");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("传输引擎已关闭");
    }

}
