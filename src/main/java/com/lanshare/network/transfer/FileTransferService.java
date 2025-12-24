package com.lanshare.network.transfer;

import com.lanshare.network.discovery.DeviceDiscovery;
import com.lanshare.network.discovery.DeviceRegistry;
import com.lanshare.network.config.NetworkConfig;
import com.lanshare.network.protocol.TransferRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * <h1>FileTransferService</h1>
 * <p>
 * 文件传输服务，作为系统的核心整合层。
 * 它负责协调设备发现、任务管理和文件传输引擎，为上层应用提供统一的文件发送和接收接口。
 * 该服务管理一个任务队列，并使用一个专用的线程来处理队列中的任务，然后将任务提交给TransferEngine执行。
 * </p>
 * 
 * @author <a href="https://github.com/xihuanxiaorang">hi</a>
 * @since 2024/7/31 17:21
 */
public class FileTransferService {

    /** 传输引擎，负责处理实际的文件发送和接收IO操作。 */
    private final TransferEngine transferEngine;
    /** 任务队列，用于存储待处理的传输任务，支持优先级排序。 */
    private final BlockingQueue<TransferTask> taskQueue;
    /** 设备发现服务，用于在局域网内发现其他设备。 */
    private final DeviceDiscovery deviceDiscovery;
    /** 设备注册表，存储已发现的设备信息。 */
    private final DeviceRegistry deviceRegistry;
    /** 协议处理器（如需握手，可在外部按需创建并传入）。 */
    // private final ProtocolHandler protocolHandler;
    /** 用于处理任务队列的单线程执行器。 */
    private final ExecutorService queueProcessor;
    /** 服务运行状态标志。 */
    private volatile boolean running = false;

    /** 状态监听器列表，用于向UI或其他组件通知传输状态变化。 */
    private final List<TransferStatusListener> listeners = new ArrayList<>();

    /**
     * 传输状态监听器接口，用于UI或其他组件接收传输状态的更新。
     */
    public interface TransferStatusListener {
        /**
         * 当一个新任务被添加到队列时调用。
         * 
         * @param task 被添加的任务。
         */
        void onTaskAdded(TransferTask task);

        /**
         * 当任务传输进度更新时调用。
         * 
         * @param taskId           任务ID。
         * @param bytesTransferred 已传输的字节数。
         * @param totalBytes       文件总字节数。
         */
        void onTaskProgress(String taskId, long bytesTransferred, long totalBytes);

        /**
         * 当任务成功完成时调用。
         * 
         * @param taskId 完成的任务ID。
         */
        void onTaskCompleted(String taskId);

        /**
         * 当任务失败时调用。
         * 
         * @param taskId 失败的任务ID。
         * @param reason 失败原因。
         */
        void onTaskFailed(String taskId, String reason);
    }

    /**
     * 构造一个新的FileTransferService实例。
     * 
     * @param deviceDiscovery 设备发现服务的实例。
     * @param deviceRegistry  设备注册表的实例。
     * @param protocolHandler 协议处理器的实例。
     */
    public FileTransferService(DeviceDiscovery deviceDiscovery, DeviceRegistry deviceRegistry) {
        this.transferEngine = new TransferEngine(NetworkConfig.DEFAULT_TRANSFER_THREADS);
        this.taskQueue = new PriorityBlockingQueue<>(); // 使用优先队列，可以根据任务属性（如文件大小）排序
        this.deviceDiscovery = deviceDiscovery;
        this.deviceRegistry = deviceRegistry;
        this.queueProcessor = Executors.newSingleThreadExecutor();
    }

    /**
     * 启动文件传输服务。
     * 这将启动设备发现广播，并开始处理任务队列。
     */
    public void startService() {
        running = true;
        try {
            deviceDiscovery.initialize();
            deviceDiscovery.start();
        } catch (IOException e) {
            System.err.println("启动设备发现失败: " + e.getMessage());
        }
        queueProcessor.submit(this::processQueue);
    }

    /**
     * 停止文件传输服务。
     * 这将停止设备发现，中断任务队列处理，并关闭传输引擎。
     */
    public void stopService() {
        running = false;
        deviceDiscovery.stop();
        queueProcessor.shutdownNow(); // 尝试立即停止所有正在执行的任务
        transferEngine.shutdown();
    }

    public void pauseTask(String taskId) {
        TransferTask task = transferEngine.getTask(taskId);
        if (task != null) {
            task.pause();
            System.out.println("暂停任务: " + taskId);
        }
    }

    public void resumeTask(String taskId) {
        TransferTask task = transferEngine.getTask(taskId);
        if (task != null) {
            task.resume();
            System.out.println("恢复任务: " + taskId);
        }
    }

    public void cancelTask(String taskId) {
        TransferTask task = transferEngine.getTask(taskId);
        if (task != null) {
            task.cancel();
            System.out.println("取消任务: " + taskId);
        }
    }

    public void registerActiveTask(TransferTask task) {
        transferEngine.registerActiveTask(task);
    }

    public void removeActiveTask(String taskId) {
        transferEngine.removeActiveTask(taskId);
    }

    /**
     * 在一个单独的线程中运行，持续从任务队列中取出任务并提交到传输引擎。
     */
    private void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // 阻塞式地从队列中获取任务
                TransferTask task = taskQueue.take();
                // 异步提交任务到引擎，并处理完成或异常情况
                transferEngine.submitTask(task).thenAccept(v -> {
                    notifyTaskCompleted(task.getTaskId());
                }).exceptionally(ex -> {
                    notifyTaskFailed(task.getTaskId(), ex.getMessage());
                    return null;
                });
            } catch (InterruptedException e) {
                // 捕获中断异常，恢复中断状态并退出循环
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 创建一个文件发送任务并将其添加到队列中。
     * 
     * @param filePath       要发送的文件的完整路径。
     * @param targetDeviceIp 目标设备的IP地址。
     */
    public void sendFile(String filePath, String targetDeviceIp) {
        sendFile(filePath, targetDeviceIp, NetworkConfig.getTcpPort());
    }

    public void sendFile(String filePath, String targetDeviceIp, int targetPort) {
        File file = new File(filePath);
        if (!file.exists()) {
            notifyTaskFailed(filePath, "File not found");
            return;
        }

        try {
            // 1. 创建传输请求
            TransferRequest request = TransferRequest.fromFile(file);
            // 2. 根据请求创建传输任务（发送）
            TransferTask task = new TransferTask(
                    request.getTaskId(),
                    file.getAbsolutePath(),
                    targetDeviceIp,
                    targetPort,
                    TransferTask.TransferType.SEND,
                    request,
                    // 注册进度回调，将引擎的进度更新转发给服务监听器
                    (taskId, bytes, total) -> notifyTaskProgress(taskId, bytes, total));

            // 3. 将任务添加到队列并通知监听器
            taskQueue.offer(task);
            notifyTaskAdded(task);
        } catch (IOException e) {
            notifyTaskFailed(filePath, "创建传输请求失败: " + e.getMessage());
        }
    }

    /**
     * 根据传入的传输请求创建一个文件接收任务，并将其添加到队列中。
     * 
     * @param request 包含文件元数据和发送方信息的传输请求。
     */
    public void receiveFile(TransferRequest request) {
        // 由于接收流程涉及 Socket 监听与落盘路径协商，这里仅创建占位任务以保持接口完整。
        TransferTask task = new TransferTask(
                request.getTaskId(),
                request.getFileName(),
                "0.0.0.0",
                NetworkConfig.getTcpPort(),
                TransferTask.TransferType.RECEIVE,
                request,
                (taskId, bytes, total) -> notifyTaskProgress(taskId, bytes, total));

        taskQueue.offer(task);
        notifyTaskAdded(task);
    }

    /**
     * 添加一个传输状态监听器。
     * 
     * @param listener 要添加的监听器。
     */
    public void addListener(TransferStatusListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * 移除一个传输状态监听器。
     * 
     * @param listener 要移除的监听器。
     */
    public void removeListener(TransferStatusListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /** 通知所有监听器，一个新任务已被添加。 */
    private void notifyTaskAdded(TransferTask task) {
        synchronized (listeners) {
            for (TransferStatusListener listener : listeners) {
                listener.onTaskAdded(task);
            }
        }
    }

    /** 通知所有监听器，任务进度已更新。 */
    private void notifyTaskProgress(String taskId, long bytes, long total) {
        synchronized (listeners) {
            for (TransferStatusListener listener : listeners) {
                listener.onTaskProgress(taskId, bytes, total);
            }
        }
    }

    /** 通知所有监听器，任务已成功完成。 */
    private void notifyTaskCompleted(String taskId) {
        synchronized (listeners) {
            for (TransferStatusListener listener : listeners) {
                listener.onTaskCompleted(taskId);
            }
        }
    }

    /** 通知所有监听器，任务已失败。 */
    private void notifyTaskFailed(String taskId, String reason) {
        synchronized (listeners) {
            for (TransferStatusListener listener : listeners) {
                listener.onTaskFailed(taskId, reason);
            }
        }
    }

    public com.lanshare.network.discovery.DeviceDiscovery getDeviceDiscovery() {
        return deviceDiscovery;
    }

    public com.lanshare.network.discovery.DeviceRegistry getDeviceRegistry() {
        return deviceRegistry;
    }
}
