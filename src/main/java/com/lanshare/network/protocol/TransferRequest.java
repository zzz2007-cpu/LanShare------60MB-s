package com.lanshare.network.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TransferRequest {
    private final String type = "TRANSFER_REQUEST";// 传输请求类型
    private String taskId;// 任务唯一标识
    private String fileName;// 文件名
    private long fileSize;// 文件大小
    private String md5;// 文件MD5校验值
    private int chunkCount;// 数据块数量
    private int chunkSize;// 数据块大小
    private long timestamp;// 请求时间戳
    // =========================Gson实例
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // =========================构造方法
    private TransferRequest() {

    }// 私有构造方法，防止外部实例化

    public TransferRequest(String taskId, String fileName, long fileSize, String md5, int chunkCount, int chunkSize) {
        this.taskId = taskId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.md5 = md5;
        this.chunkCount = chunkCount;
        this.chunkSize = chunkSize;
        this.timestamp = System.currentTimeMillis();
    }

    // ===================静态工厂方法
    public static TransferRequest fromFile(File file, int chunkSize) throws IOException {
        String taskId = java.util.UUID.randomUUID().toString();// 生成任务ID
        String fileName = file.getName();// 获取文件名
        long fileSize = file.length();// 获取文件大小
        String md5 = calculateMD5(file);// 计算文件MD5校验值
        int chunkCount = (int) Math.ceil((double) fileSize / chunkSize);// 计算数据块数量
        return new TransferRequest(taskId, fileName, fileSize, md5, chunkCount, chunkSize);
    }

    // 从文件创建传输请求
    public static TransferRequest fromFile(File file) throws IOException {
        return fromFile(file, 1024 * 1024);// 默认块大小为1MB
    }

    // ===================计算文件MD5校验值
    private static String calculateMD5(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] byteArray = new byte[1024];
                int bytesCount;
                while ((bytesCount = fis.read(byteArray)) != -1) {
                    md.update(byteArray, 0, bytesCount);
                }
            }
            // 将字节数组转换为16进制字符串
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("计算文件MD5校验值时发生错误", e);
        }

    }

    // =========================序列化方法
    public String toJson() {
        return gson.toJson(this);
    }

    // =========================反序列化方法
    public static TransferRequest fromJson(String json) {
        return gson.fromJson(json, TransferRequest.class);
    }

    // ========================工具方法
    // 验证请求是否有效
    public boolean isValid() {
        return type != null && !type.isEmpty() &&
                taskId != null && !taskId.isEmpty() &&
                fileName != null && !fileName.isEmpty() &&
                fileSize >= 0 &&
                md5 != null && !md5.isEmpty() &&
                chunkCount >= 0 &&
                chunkSize > 0;
    }

    // 获取格式化的文件大小
    public String getFormattedFileSize() {
        return formatBytes(fileSize);
    }

    private String formatBytes(long bytes) {
        if (bytes < 10244) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2fKB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2fMB", bytes / 1024.0 / 1024);
        } else {
            return String.format("%.2fGB", bytes / 1024.0 / 1024 / 1024);
        }
    }

    // =========================Getter和Setter方法
    public String getType() {
        return type;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    @Override
    public String toString() {
        return String.format(
                "TransferRequest{taskId=%s, fileName=%s, fileSize=%s, chunkCount=%d}",
                taskId, fileName, getFormattedFileSize(), chunkCount);
    }

}
