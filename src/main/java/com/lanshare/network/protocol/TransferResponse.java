package com.lanshare.network.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

//文件传输响应协议
public class TransferResponse {
    private final String type = "TRANSFER_RESPONSE";
    private String taskId;// 任务ID
    private boolean accepted;// 是否接受
    private String savePath;// 保存路径
    private String rejectReason;// 拒绝原因
    private long existingOffset = 0; // 已存在的文件大小（用于断点续传）
    private long timestamp;// 时间戳

    // ==========================Gsons实例
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ==========================构造方法
    public TransferResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    // =======================静态工厂
    // 接受并指定续传位置
    public static TransferResponse accept(String taskId, String savePath, long existingOffset) {
        TransferResponse response = new TransferResponse();
        response.taskId = taskId;
        response.accepted = true;
        response.savePath = savePath;
        response.existingOffset = existingOffset;
        response.rejectReason = null;
        return response;
    }

    // 接受（默认从头开始）
    public static TransferResponse accept(String taskId, String savePath) {
        return accept(taskId, savePath, 0);
    }

    // 拒绝
    public static TransferResponse reject(String taskId, String rejectReason) {
        TransferResponse response = new TransferResponse();
        response.taskId = taskId;
        response.accepted = false;
        response.savePath = null;
        response.rejectReason = rejectReason;
        return response;
    }

    // 快速拒绝
    public static TransferResponse reject(String taskId) {
        return reject(taskId, "用户拒绝");
    }

    /**
     * 预定义的拒绝原因
     * 便于统一管理和国际化
     */
    public static class RejectReason {
        public static final String USER_DECLINED = "用户拒绝";
        public static final String INSUFFICIENT_SPACE = "磁盘空间不足";
        public static final String FILE_TYPE_NOT_SUPPORTED = "文件类型不支持";
        public static final String BUSY = "正在传输其他文件";
        public static final String DUPLICATE_FILE = "文件已存在";
        public static final String UNKNOWN = "未知原因";
    }

    // 使用预定义原因拒绝
    public static TransferResponse rejectWith(String taskId, String rejectReason) {
        return reject(taskId, rejectReason);
    }

    // ===================Json序列化
    public String toJson() {
        return GSON.toJson(this);
    }

    // ===================Json反序列化
    public static TransferResponse fromJson(String json) {
        return GSON.fromJson(json, TransferResponse.class);
    }

    // =============================工具方法
    public boolean isValid() {
        if (taskId == null || taskId.isEmpty()) {
            return false;
        }
        if (accepted && (savePath == null || savePath.isEmpty())) {
            return false;
        }
        if (!accepted && (rejectReason == null || rejectReason.isEmpty())) {
            return false;
        }
        return true;
    }

    public String getStatusDescription() {
        if (accepted) {
            return "接受,保存到" + savePath;
        }
        return "拒绝,原因:" + rejectReason;
    }

    public long getExistingOffset() {
        return existingOffset;
    }

    // ===================Getter
    public String getType() {
        return type;
    }

    public String getTaskId() {
        return taskId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getSavePath() {
        return savePath;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public long getTimestamp() {
        return timestamp;
    }
    // ==================== toString ====================

    @Override
    public String toString() {
        if (accepted) {
            return String.format(
                    "TransferResponse{taskId=%s, accepted=true, savePath=%s}",
                    taskId, savePath);
        } else {
            return String.format(
                    "TransferResponse{taskId=%s, accepted=false, reason=%s}",
                    taskId, rejectReason);
        }
    }
}
