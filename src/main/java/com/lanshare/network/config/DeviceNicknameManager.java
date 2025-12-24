package com.lanshare.network.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 设备自定义名称管理器
 * 负责保存和加载设备的自定义别名
 */
public class DeviceNicknameManager {
    private static final Logger logger = Logger.getLogger(DeviceNicknameManager.class.getName());
    private static final String NICKNAME_FILE = "device-nicknames.json";
    private static DeviceNicknameManager instance;
    
    private final Map<String, String> nicknames; // IP -> 自定义名称
    private final Gson gson;
    private final File configFile;
    
    private DeviceNicknameManager() {
        this.nicknames = new HashMap<>();
        this.gson = new Gson();
        
        // 配置文件路径：优先使用项目根目录，其次用户目录
        File projectRoot = new File(".");
        this.configFile = new File(projectRoot, NICKNAME_FILE);
        
        loadNicknames();
    }
    
    public static synchronized DeviceNicknameManager getInstance() {
        if (instance == null) {
            instance = new DeviceNicknameManager();
        }
        return instance;
    }
    
    /**
     * 设置设备别名
     * @param ipAddress 设备IP地址
     * @param nickname 自定义名称
     */
    public void setNickname(String ipAddress, String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            nicknames.remove(ipAddress);
        } else {
            nicknames.put(ipAddress, nickname.trim());
        }
        saveNicknames();
        logger.info("设置设备别名: " + ipAddress + " -> " + nickname);
    }
    
    /**
     * 获取设备别名
     * @param ipAddress 设备IP地址
     * @return 自定义名称，如果没有则返回 null
     */
    public String getNickname(String ipAddress) {
        return nicknames.get(ipAddress);
    }
    
    /**
     * 删除设备别名
     * @param ipAddress 设备IP地址
     */
    public void removeNickname(String ipAddress) {
        nicknames.remove(ipAddress);
        saveNicknames();
        logger.info("删除设备别名: " + ipAddress);
    }
    
    /**
     * 检查设备是否有别名
     * @param ipAddress 设备IP地址
     * @return true 如果有别名
     */
    public boolean hasNickname(String ipAddress) {
        return nicknames.containsKey(ipAddress);
    }
    
    /**
     * 获取显示名称（优先返回别名，没有则返回默认名称）
     * @param ipAddress 设备IP地址
     * @param defaultName 默认名称
     * @return 显示名称
     */
    public String getDisplayName(String ipAddress, String defaultName) {
        String nickname = nicknames.get(ipAddress);
        return nickname != null ? nickname : defaultName;
    }
    
    /**
     * 从文件加载别名
     */
    private void loadNicknames() {
        if (!configFile.exists()) {
            logger.info("设备别名配置文件不存在，使用空配置");
            return;
        }
        
        try (Reader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                nicknames.putAll(loaded);
                logger.info("加载设备别名配置: " + nicknames.size() + " 条");
            }
        } catch (Exception e) {
            logger.warning("加载设备别名配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存别名到文件
     */
    private void saveNicknames() {
        try (Writer writer = new FileWriter(configFile)) {
            gson.toJson(nicknames, writer);
            logger.info("保存设备别名配置: " + nicknames.size() + " 条");
        } catch (Exception e) {
            logger.warning("保存设备别名配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有别名
     * @return 别名映射的副本
     */
    public Map<String, String> getAllNicknames() {
        return new HashMap<>(nicknames);
    }
}
