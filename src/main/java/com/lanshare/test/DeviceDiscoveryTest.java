package com.lanshare.test;

import com.lanshare.network.discovery.DeviceDiscovery;
import com.lanshare.network.model.DeviceInfo;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * DeviceDiscovery 测试类
 *
 * 这是一个 JavaFX 应用，可以：
 * 1. 启动设备发现服务
 * 2. 实时显示发现的设备
 * 3. 测试各种功能
 *
 * 运行方法：
 * 1. 在两台电脑上分别运行这个程序
 * 2. 观察是否能互相发现
 *
 * @author 主人
 */
public class DeviceDiscoveryTest extends Application {

    private DeviceDiscovery discovery;
    private Label statusLabel;
    private Label deviceCountLabel;
    private ListView<String> deviceListView;

    @Override
    public void start(Stage primaryStage) {
        System.out.println("========== DeviceDiscovery 测试程序 ==========\n");

        // 创建设备发现服务
        discovery = new DeviceDiscovery();

        // ===== 创建 UI =====
        VBox root = new VBox(10);
        root.setStyle("-fx-padding: 20;");

        // 状态标签
        statusLabel = new Label("状态: 未启动");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // 设备数量标签
        deviceCountLabel = new Label("发现设备: 0");
        deviceCountLabel.setStyle("-fx-font-size: 12px;");

        // 设备列表
        deviceListView = new ListView<>();
        deviceListView.setPrefHeight(300);

        // 按钮
        Button startButton = new Button("启动服务");
        Button stopButton = new Button("停止服务");
        Button refreshButton = new Button("刷新设备");
        Button clearButton = new Button("清空列表");
        Button printButton = new Button("打印设备");

        stopButton.setDisable(true);
        refreshButton.setDisable(true);
        clearButton.setDisable(true);

        // 按钮事件
        startButton.setOnAction(e -> {
            try {
                startService();
                statusLabel.setText("状态: 运行中");
                startButton.setDisable(true);
                stopButton.setDisable(false);
                refreshButton.setDisable(false);
                clearButton.setDisable(false);
            } catch (Exception ex) {
                statusLabel.setText("状态: 启动失败 - " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        stopButton.setOnAction(e -> {
            stopService();
            statusLabel.setText("状态: 已停止");
            startButton.setDisable(false);
            stopButton.setDisable(true);
            refreshButton.setDisable(true);
            clearButton.setDisable(true);
        });

        refreshButton.setOnAction(e -> {
            discovery.refreshDevices();
            statusLabel.setText("状态: 已刷新");
        });

        clearButton.setOnAction(e -> {
            discovery.clearAllDevices();
            statusLabel.setText("状态: 已清空设备列表");
        });

        printButton.setOnAction(e -> {
            discovery.printDevices();
        });

        // 布局
        VBox buttonBox = new VBox(5, startButton, stopButton, refreshButton, clearButton, printButton);
        root.getChildren().addAll(statusLabel, deviceCountLabel, deviceListView, buttonBox);

        // 场景
        Scene scene = new Scene(root, 500, 500);
        primaryStage.setTitle("设备发现测试");
        primaryStage.setScene(scene);
        primaryStage.show();

        // 窗口关闭时停止服务
        primaryStage.setOnCloseRequest(e -> {
            if (discovery.isRunning()) {
                discovery.stop();
            }
            Platform.exit();
        });
    }

    /**
     * 启动服务
     */
    private void startService() throws Exception {
        System.out.println("正在启动服务...\n");

        // 1. 初始化
        discovery.initialize();

        // 2. 设置设备列表监听器
        discovery.getDeviceList().addListener((ListChangeListener<DeviceInfo>) change -> {
            Platform.runLater(() -> updateDeviceList());
        });

        // 3. 启动服务
        discovery.start();

        System.out.println("✅ 服务已启动！");
        System.out.println("本机设备信息：");
        System.out.println("  " + discovery.getLocalDevice());
        System.out.println("\n等待发现其他设备...\n");
    }

    /**
     * 停止服务
     */
    private void stopService() {
        System.out.println("\n正在停止服务...");
        discovery.stop();
        System.out.println("✅ 服务已停止\n");
    }

    /**
     * 更新设备列表显示
     */
    private void updateDeviceList() {
        deviceListView.getItems().clear();

        int onlineCount = 0;
        for (DeviceInfo device : discovery.getDeviceList()) {
            String status = device.getStatus().toString();
            String info = String.format("[%s] %s (%s) - %s:%d",
                    status,
                    device.getUserName(),
                    device.getDeviceName(),
                    device.getIpAddress(),
                    device.getPort()
            );
            deviceListView.getItems().add(info);

            if (device.getStatus() == com.lanshare.network.model.DeviceStatus.ONLINE) {
                onlineCount++;
            }
        }

        deviceCountLabel.setText("发现设备: " + discovery.getDeviceList().size() + " (在线: " + onlineCount + ")");
    }

    public static void main(String[] args) {
        launch(args);
    }
}