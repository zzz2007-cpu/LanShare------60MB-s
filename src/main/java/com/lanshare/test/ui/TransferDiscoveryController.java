package com.lanshare.test.ui;

import com.lanshare.network.config.NetworkConfig;
import com.lanshare.network.config.DeviceNicknameManager;
import com.lanshare.network.discovery.DeviceDiscovery;
import com.lanshare.network.model.DeviceInfo;
import com.lanshare.network.protocol.FileChunk;
import com.lanshare.network.protocol.ProtocolException;
import com.lanshare.network.protocol.ProtocolHandler;
import com.lanshare.network.protocol.TransferRequest;
import com.lanshare.network.protocol.TransferResponse;
import com.lanshare.network.transfer.FileTransferService;
import com.lanshare.network.transfer.TransferTask;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.util.Duration;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferDiscoveryController {
    @FXML
    private BorderPane root;
    @FXML
    private HBox paneHeader;
    @FXML
    private ListView<DeviceInfo> listDevices;
    @FXML
    private ListView<LogItem> listLogs;
    @FXML
    private Button btnClearLog;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label lblProgress;
    @FXML
    private Label lblSpeed;
    @FXML
    private Label lblTimeRemaining;
    @FXML
    private Button btnStart, btnStop, btnRefresh, btnConnect, btnChoose, btnSend;
    @FXML
    private Button btnPause, btnResume, btnCancel;
    @FXML
    private Button btnSettings;
    @FXML
    private CheckBox chkLocalServer;
    @FXML
    private Label lblFileName;
    @FXML
    private Label lblFileIcon;
    @FXML
    private ImageView imgFileIcon;
    @FXML
    private VBox fileInfoBox;
    @FXML
    private VBox targetInfoBox;
    @FXML
    private Label lblTargetIcon;
    @FXML
    private Label lblTargetName;
    @FXML
    private Button btnCancelFile;
    @FXML
    private Button btnCancelTarget;

    private DeviceInfo connectedDevice = null;

    private FileTransferService service;
    private DeviceDiscovery discovery;
    private final FileChooser chooser = new FileChooser();
    private File selectedFile;
    private File lastSaveDir;
    private ExecutorService serverExecutor;
    private volatile boolean serverRunning = false;
    private volatile long lastProgressUpdateTs = 0L;
    private volatile long lastBytesTransferred = 0L;
    private volatile long speedUpdateTs = 0L;
    private volatile boolean transferPaused = false;
    private String currentTaskId = null;
    private DeviceNicknameManager nicknameManager;

    @FXML
    public void initialize() {
        service = new FileTransferService(new com.lanshare.network.discovery.DeviceDiscovery(),
                new com.lanshare.network.discovery.DeviceRegistry());
        discovery = service.getDeviceDiscovery();
        nicknameManager = DeviceNicknameManager.getInstance();

        javafx.collections.ObservableList<LogItem> logItems = javafx.collections.FXCollections.observableArrayList();
        listLogs.setItems(logItems);
        listLogs.setFocusTraversable(false);
        listLogs.setCellFactory(lv -> new ListCell<>() {
            private final HBox root = new HBox(10);
            private final Region marker = new Region();
            private final VBox box = new VBox(2);
            private final Label timeLbl = new Label();
            private final Label textLbl = new Label();
            {
                root.setAlignment(Pos.TOP_LEFT);
                marker.setPrefSize(6, 6);
                marker.setStyle("-fx-background-color: #4F6BED; -fx-background-radius: 3;");
                HBox.setMargin(marker, new Insets(8, 0, 0, 0));
                timeLbl.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 11px;");
                textLbl.setWrapText(true);
                textLbl.setStyle("-fx-font-size: 13px;");
                textLbl.maxWidthProperty().bind(listLogs.widthProperty().subtract(80));
                box.getChildren().addAll(timeLbl, textLbl);
                root.getChildren().addAll(marker, box);
                root.setMaxWidth(Double.MAX_VALUE);
            }
            @Override
            protected void updateItem(LogItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    timeLbl.setText(item.time);
                    textLbl.setText(item.text);
                    setGraphic(root);
                }
            }
        });
        listDevices.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DeviceInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setContextMenu(null);
                } else {
                    // 显示自定义名称（如果有）
                    String displayName = nicknameManager.getDisplayName(
                            item.getIpAddress(),
                            item.getUserName() + "(" + item.getDeviceName() + ")"
                    );
                    setText(String.format("[%s] %s - %s:%d",
                            item.getStatus(), displayName, item.getIpAddress(), item.getPort()));
                    
                    // 添加右键菜单
                    setContextMenu(createDeviceContextMenu(item));
                }
            }
        });

        btnStart.setOnAction(e -> startDiscovery());
        btnStop.setOnAction(e -> stopDiscovery());
        btnRefresh.setOnAction(e -> {
            discovery.refreshDevices();
            appendLog("已触发刷新设备列表");
        });
        btnConnect.setOnAction(e -> connectSelected());
        btnChoose.setOnAction(e -> chooseFile());
        btnSend.setOnAction(e -> sendToSelected());
        
        // 传输控制按钮
        btnPause.setOnAction(e -> pauseTransfer());
        btnResume.setOnAction(e -> resumeTransfer());
        btnCancel.setOnAction(e -> cancelTransfer());
        
        // 设置按钮
        btnSettings.setOnAction(e -> showSettingsDialog());
        
        // 清空日志按钮
        btnClearLog.setOnAction(e -> {
            listLogs.getItems().clear();
        });
        
        chkLocalServer.setOnAction(e -> {
            if (chkLocalServer.isSelected())
                startServer();
            else
                stopServer();
        });

        service.addListener(new FileTransferService.TransferStatusListener() {
            @Override
            public void onTaskAdded(TransferTask task) {
                currentTaskId = task.getTaskId();
                transferPaused = false;
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblProgress.setText("0%");
                    lblSpeed.setText("速度: --");
                    lblTimeRemaining.setText("剩余时间: --");
                });
                lastBytesTransferred = 0L;
                speedUpdateTs = System.currentTimeMillis();
                updateTransferButtons(true);
            }

            @Override
            public void onTaskProgress(String taskId, long bytesTransferred, long totalBytes) {
                long now = System.currentTimeMillis();
                if (now - lastProgressUpdateTs < 100 && bytesTransferred < totalBytes)
                    return;
                lastProgressUpdateTs = now;
                double p = totalBytes > 0 ? (bytesTransferred * 1.0 / totalBytes) : 0.0;
                
                // 计算传输速度
                long timeDelta = now - speedUpdateTs;
                long bytesDelta = bytesTransferred - lastBytesTransferred;
                String speedText = "速度: --";
                String timeText = "剩余时间: --";
                
                if (timeDelta > 0 && bytesDelta > 0) {
                    double speed = (bytesDelta * 1000.0) / timeDelta; // 字节/秒
                    speedText = "速度: " + formatSpeed(speed);
                    
                    // 计算剩余时间
                    long remaining = totalBytes - bytesTransferred;
                    if (speed > 0 && remaining > 0) {
                        long secondsRemaining = (long) (remaining / speed);
                        timeText = "剩余时间: " + formatTime(secondsRemaining);
                    }
                }
                
                lastBytesTransferred = bytesTransferred;
                speedUpdateTs = now;
                
                String finalSpeedText = speedText;
                String finalTimeText = timeText;
                Platform.runLater(() -> {
                    progressBar.setProgress(p);
                    lblProgress.setText(String.format("%d%%", (int) Math.round(p * 100)));
                    lblSpeed.setText(finalSpeedText);
                    lblTimeRemaining.setText(finalTimeText);
                });
            }

            @Override
            public void onTaskCompleted(String taskId) {
                currentTaskId = null;
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    lblProgress.setText("100%");
                    lblSpeed.setText("速度: 完成");
                    lblTimeRemaining.setText("剩余时间: 0秒");
                    appendLog("发送完成: " + taskId);
                });
                updateTransferButtons(false);
            }

            @Override
            public void onTaskFailed(String taskId, String reason) {
                currentTaskId = null;
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblProgress.setText("失败");
                    lblSpeed.setText("速度: --");
                    lblTimeRemaining.setText("剩余时间: --");
                    appendLog("发送失败(" + taskId + "): " + reason);
                });
                updateTransferButtons(false);
            }
        });

        try {
            File home = new File(System.getProperty("user.home", "."));
            if (home.exists())
                chooser.setInitialDirectory(home);
        } catch (Exception ignored) {
        }
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("所有文件", "*.*"),
                new FileChooser.ExtensionFilter("常见类型", "*.txt", "*.jpg", "*.png", "*.pdf"));

        setupAnimations();

        progressBar.setProgress(0);
        lblProgress.setText("0%");
        btnSend.setDisable(true);
        btnConnect.setDisable(true);
        
        // 初始化启动/停止按钮状态
        btnStart.setDisable(false);
        btnStop.setDisable(true);
        updateDiscoveryButtonStyles();

        listDevices.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            updateActionButtons();
            updateTargetInfoBox();
        });
        
        // 设置拖拽文件发送功能
        setupDragAndDrop();
        
        // 取消按钮逻辑
        btnCancelFile.setOnAction(e -> {
            selectedFile = null;
            updateActionButtons();
            appendLog("已取消选择文件");
            Platform.runLater(() -> {
                lblFileName.setText("未选择文件");
                lblFileName.setTooltip(null);
                imgFileIcon.setVisible(false);
                imgFileIcon.setManaged(false);
                lblFileIcon.setVisible(true);
                lblFileIcon.setManaged(true);
                
                // 恢复灰色样式
                fileInfoBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 15; -fx-border-radius: 15; -fx-border-color: #dee2e6; -fx-border-width: 2;");
                lblFileIcon.setStyle("-fx-font-size: 64px; -fx-text-fill: #6c757d;");
                lblFileName.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d; -fx-font-weight: bold;");
                
                btnCancelFile.setVisible(false);
            });
        });
        
        btnCancelTarget.setOnAction(e -> disconnectTarget());
    }

    private void setupAnimations() {
        FadeTransition ft = new FadeTransition(Duration.millis(300), paneHeader);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
        applyHover(btnStart);
        applyHover(btnStop);
        applyHover(btnRefresh);
        applyHover(btnConnect);
        applyHover(btnChoose);
        applyHover(btnSend);
        applyHover(btnSettings);
        applyHover(btnClearLog);
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), paneHeader);
        tt.setFromY(-12);
        tt.setToY(0);
        tt.play();
        
        applyCancelHover(btnCancelFile);
        applyCancelHover(btnCancelTarget);
    }

    private void applyCancelHover(Button b) {
        b.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), b);
            st.setToX(1.2);
            st.setToY(1.2);
            st.play();
        });
        b.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(200), b);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    private void applyHover(Button b) {
        b.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), b);
            st.setToX(1.03);
            st.setToY(1.03);
            st.play();
        });
        b.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), b);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    private void startDiscovery() {
        try {
            if (!discovery.isRunning()) {
                discovery.initialize();
                listDevices.setItems(discovery.getDeviceList());
                discovery.getDeviceList().addListener(
                        (ListChangeListener<DeviceInfo>) change -> Platform.runLater(listDevices::refresh));
                discovery.start();
                appendLog("设备发现已启动");
                discovery.printDevices();
                
                // 更新按钮状态：启动变灰，停止变蓝
                btnStart.setDisable(true);
                btnStop.setDisable(false);
                updateDiscoveryButtonStyles();
            }
        } catch (Exception ex) {
            appendLog("启动发现失败: " + ex.getMessage());
        }
        service.startService();
        // 自动启动接收服务端
        startServer();
    }

    private void stopDiscovery() {
        try {
            discovery.stop();
            appendLog("设备发现已停止");
            
            // 更新按钮状态：停止变灰，启动变蓝
            btnStop.setDisable(true);
            btnStart.setDisable(false);
            updateDiscoveryButtonStyles();
        } catch (Exception ex) {
            appendLog("停止发现失败: " + ex.getMessage());
        }
        service.stopService();
    }

    private void chooseFile() {
        // 创建新的对话框 Stage
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("选择文件");

        // 对话框根布局
        VBox dialogRoot = new VBox(20);
        dialogRoot.setAlignment(Pos.CENTER);
        dialogRoot.setPadding(new Insets(20));
        dialogRoot.setStyle("-fx-background-color: white;");

        // 拖拽/点击区域
        StackPane dropArea = new StackPane();
        dropArea.setPrefSize(400, 300);
        // 默认样式：圆角、虚线边框
        String defaultStyle = "-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 2; -fx-border-style: dashed; -fx-border-radius: 10; -fx-background-radius: 10;";
        String hoverStyle = "-fx-background-color: #e7f1ff; -fx-border-color: #4F6BED; -fx-border-width: 2; -fx-border-style: dashed; -fx-border-radius: 10; -fx-background-radius: 10;";
        
        dropArea.setStyle(defaultStyle);

        // 中心加号和提示文字
        Label plusLabel = new Label("+");
        plusLabel.setStyle("-fx-font-size: 60px; -fx-text-fill: #adb5bd;");
        
        Label hintLabel = new Label("点击或拖拽文件到此处");
        hintLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #6c757d;");
        
        VBox centerContent = new VBox(10, plusLabel, hintLabel);
        centerContent.setAlignment(Pos.CENTER);
        // 让鼠标事件穿透 VBox，以便 StackPane 捕获
        centerContent.setMouseTransparent(true);
        
        dropArea.getChildren().add(centerContent);

        // 点击事件：打开文件选择器
        dropArea.setOnMouseClicked(e -> {
            String choosePath = System.getProperty("file.choose.path", "");
            if (!choosePath.isEmpty()) {
                File dir = new File(choosePath);
                if (dir.exists() && dir.isDirectory()) {
                    chooser.setInitialDirectory(dir);
                }
            }
            File file = chooser.showOpenDialog(dialog);
            if (file != null) {
                handleFileSelection(file);
                dialog.close();
            }
        });

        // 鼠标悬停效果
        dropArea.setOnMouseEntered(e -> dropArea.setStyle(hoverStyle));
        dropArea.setOnMouseExited(e -> dropArea.setStyle(defaultStyle));

        // 拖拽进入
        dropArea.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                dropArea.setStyle(hoverStyle);
            }
            event.consume();
        });
        
        // 拖拽离开
        dropArea.setOnDragExited(event -> {
             dropArea.setStyle(defaultStyle);
             event.consume();
        });

        // 拖拽放下
        dropArea.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                handleFileSelection(file);
                success = true;
                dialog.close();
            }
            event.setDropCompleted(success);
            event.consume();
        });

        dialogRoot.getChildren().add(dropArea);
        Scene scene = new Scene(dialogRoot);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void handleFileSelection(File file) {
        selectedFile = file;
        appendLog("选择文件: " + selectedFile.getAbsolutePath());
        progressBar.setProgress(0);
        lblProgress.setText("0%");
        updateActionButtons();
        
        // 更新文件信息展示
        Platform.runLater(() -> {
            lblFileName.setText(file.getName());
            lblFileName.setTooltip(new Tooltip(file.getAbsolutePath()));
            
            String name = file.getName().toLowerCase();
            boolean isImage = name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                              name.endsWith(".png") || name.endsWith(".gif") || 
                              name.endsWith(".bmp");
            
            boolean showImage = false;
            if (isImage) {
                 try {
                    // 尝试加载图片作为缩略图
                    Image image = new Image(file.toURI().toString(), 64, 64, true, true);
                    if (!image.isError()) {
                        imgFileIcon.setImage(image);
                        showImage = true;
                    }
                } catch (Exception e) {
                    // 加载失败则忽略
                }
            } 
            
            if (showImage) {
                imgFileIcon.setVisible(true);
                imgFileIcon.setManaged(true);
                lblFileIcon.setVisible(false);
                lblFileIcon.setManaged(false);
            } else {
                // 非图片或加载失败，显示默认图标
                imgFileIcon.setVisible(false);
                imgFileIcon.setManaged(false);
                lblFileIcon.setVisible(true);
                lblFileIcon.setManaged(true);
            }
            
            // 选中状态：蓝色
            fileInfoBox.setStyle("-fx-background-color: #e7f1ff; -fx-background-radius: 15; -fx-border-radius: 15; -fx-border-color: #4F6BED; -fx-border-width: 2;");
            lblFileIcon.setStyle("-fx-font-size: 64px; -fx-text-fill: #4F6BED;");
            lblFileName.setStyle("-fx-font-size: 14px; -fx-text-fill: #4F6BED; -fx-font-weight: bold;");
            btnCancelFile.setVisible(true);
        });
    }

    private volatile Socket connectingSocket = null;

    private void connectSelected() {
        DeviceInfo target = listDevices.getSelectionModel().getSelectedItem();
        if (target == null) {
            appendLog("请先选择目标设备");
            return;
        }
        
        // 不再禁用取消按钮，允许用户中断连接
        btnConnect.setDisable(true);
        // btnCancelTarget.setDisable(true); 
        // listDevices.setDisable(true);
        appendLog("正在请求连接 " + target.getUserName() + "...");
        
        new Thread(() -> {
            try {
                Socket socket = new Socket(target.getIpAddress(), target.getPort());
                connectingSocket = socket; // 保存引用以便取消
                
                try (ProtocolHandler handler = new ProtocolHandler(socket)) {
                    DeviceInfo local = discovery.getLocalDevice();
                    JsonObject connectMsg = new JsonObject();
                    connectMsg.addProperty("type", "CONNECT");
                    connectMsg.addProperty("from", local.getIpAddress());
                    connectMsg.addProperty("deviceName", local.getDeviceName());
                    connectMsg.addProperty("userName", local.getUserName());
                    String hello = new Gson().toJson(connectMsg);
                    handler.sendJson(hello);
                    String resp = handler.receiveJson();
                    
                    Platform.runLater(() -> {
                        if (resp != null && resp.contains("ACCEPT")) {
                            appendLog("连接请求已被接受");
                            connectedDevice = target;
                            updateTargetInfoBox();
                        } else {
                            appendLog("连接请求被拒绝或无效响应: " + resp);
                        }
                        // btnCancelTarget.setDisable(false);
                        // listDevices.setDisable(false);
                        updateActionButtons();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    // 如果是手动关闭的，socket会抛出异常，这里可以区分处理
                    if (connectingSocket == null) {
                        appendLog("连接请求已取消");
                    } else {
                        appendLog("建立连接失败: " + e.getMessage());
                    }
                    // btnCancelTarget.setDisable(false);
                    // listDevices.setDisable(false);
                    updateActionButtons();
                });
            } finally {
                connectingSocket = null;
            }
        }).start();
    }

    private void sendToSelected() {
        DeviceInfo target = connectedDevice;
        if (target == null) {
            target = listDevices.getSelectionModel().getSelectedItem();
        }

        if (target == null) {
            appendLog("请先选择目标设备");
            return;
        }
        if (selectedFile == null) {
            appendLog("请先选择文件");
            return;
        }
        String ip = target.getIpAddress();
        int port = target.getPort();
        String selfIp = discovery != null && discovery.getLocalDevice() != null ? discovery.getLocalDevice().getIpAddress() : "";
        if (ip != null && ip.equals(selfIp) && connectedDevice != null) {
            ip = connectedDevice.getIpAddress();
            port = connectedDevice.getPort();
        }
        appendLog("开始发送到: " + ip + ":" + port);
        service.sendFile(selectedFile.getAbsolutePath(), ip, port);
    }

    private void updateActionButtons() {
        boolean hasDevice = listDevices.getSelectionModel().getSelectedItem() != null;
        boolean hasFile = selectedFile != null;
        // 如果已经连接，建立连接按钮禁用；如果未连接但有选择，建立连接按钮可用
        btnConnect.setDisable(connectedDevice != null || !hasDevice);
        // 只有在已连接且选择了文件时，发送按钮才可用
        btnSend.setDisable(!(connectedDevice != null && hasFile));
    }

    private void updateTargetInfoBox() {
        Platform.runLater(() -> {
            DeviceInfo target = connectedDevice;
            boolean isConnected = target != null;
            
            // 如果没有连接，显示选中的设备（灰色）
            if (!isConnected) {
                target = listDevices.getSelectionModel().getSelectedItem();
            }

            if (target != null) {
                String displayName = nicknameManager.getDisplayName(
                    target.getIpAddress(),
                    target.getUserName() + "(" + target.getDeviceName() + ")"
                );
                lblTargetName.setText(displayName);
                lblTargetName.setTooltip(new Tooltip(target.getIpAddress()));
                
                if (isConnected) {
                    // 选中状态：蓝色
                    targetInfoBox.setStyle("-fx-background-color: #e7f1ff; -fx-background-radius: 15; -fx-border-radius: 15; -fx-border-color: #4F6BED; -fx-border-width: 2;");
                    lblTargetIcon.setStyle("-fx-font-size: 64px; -fx-text-fill: #4F6BED;");
                    lblTargetName.setStyle("-fx-font-size: 14px; -fx-text-fill: #4F6BED; -fx-font-weight: bold;");
                    btnCancelTarget.setVisible(true);
                } else {
                    // 未连接状态：灰色
                    targetInfoBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 15; -fx-border-radius: 15; -fx-border-color: #dee2e6; -fx-border-width: 2;");
                    lblTargetIcon.setStyle("-fx-font-size: 64px; -fx-text-fill: #6c757d;");
                    lblTargetName.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d; -fx-font-weight: bold;");
                    // 选中了但未连接，也显示取消按钮
                    btnCancelTarget.setVisible(true);
                }
            } else {
                lblTargetName.setText("未选择设备");
                lblTargetName.setTooltip(null);
                
                // 空状态：灰色
                targetInfoBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 15; -fx-border-radius: 15; -fx-border-color: #dee2e6; -fx-border-width: 2;");
                lblTargetIcon.setStyle("-fx-font-size: 64px; -fx-text-fill: #6c757d;");
                lblTargetName.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d; -fx-font-weight: bold;");
                btnCancelTarget.setVisible(false);
            }
        });
    }

    private void disconnectTarget() {
        if (connectingSocket != null) {
            // 正在连接中，取消连接
            try {
                connectingSocket.close();
            } catch (Exception e) {
                // ignore
            }
            connectingSocket = null;
            // UI更新会由connectSelected的异常处理块触发
            return;
        }

        // 保存当前状态用于处理
        boolean wasConnected = (connectedDevice != null);
        DeviceInfo target = wasConnected ? connectedDevice : listDevices.getSelectionModel().getSelectedItem();

        if (wasConnected && target != null) {
            final DeviceInfo finalTarget = target;
            new Thread(() -> {
                try (Socket socket = new Socket(finalTarget.getIpAddress(), finalTarget.getPort());
                     ProtocolHandler handler = new ProtocolHandler(socket)) {
                    String msg = "{\"type\":\"DISCONNECT\",\"from\":\"" + discovery.getLocalDevice().getIpAddress() + "\"}";
                    handler.sendJson(msg);
                } catch (Exception e) {
                    // 忽略发送失败
                }
                Platform.runLater(() -> appendLog("已断开与 " + finalTarget.getUserName() + " 的连接"));
            }).start();
        } else if (target != null) {
            appendLog("已取消选择目标设备");
        }

        // 无论何种状态，都重置连接状态并清除选择
        connectedDevice = null;
        
        // 强制清除选择（确保UI回到初始状态）
        listDevices.getSelectionModel().clearSelection();
        
        // 显式更新UI
        updateTargetInfoBox();
        updateActionButtons();
    }

    private void startServer() {
        if (serverRunning)
            return;
        serverRunning = true;
        serverExecutor = Executors.newFixedThreadPool(2);
        serverExecutor.submit(() -> {
            try (ServerSocket server = new ServerSocket(NetworkConfig.getTcpPort())) {
                int port = server.getLocalPort();
                appendLog("接收端启动，监听: " + port);
                if (discovery != null) {
                    discovery.updateLocalPort(port);
                }
                while (serverRunning) {
                    Socket client = server.accept();
                    serverExecutor.submit(() -> handleClient(client));
                }
            } catch (Exception ex) {
                appendLog("接收端异常: " + ex.getMessage());
            }
        });
    }

    private void stopServer() {
        serverRunning = false;
        if (serverExecutor != null)
            serverExecutor.shutdownNow();
        appendLog("接收端已停止");
    }

    private void handleClient(Socket client) {
        try (ProtocolHandler handler = new ProtocolHandler(client)) {
            String json = handler.receiveJson();
            if (json != null) {
                // 尝试解析为JSON对象以检查类型
                JsonObject jsonObj = null;
                try {
                    jsonObj = new Gson().fromJson(json, JsonObject.class);
                } catch (Exception e) {
                    // 忽略解析错误
                }

                if (jsonObj != null && jsonObj.has("type")) {
                    String type = jsonObj.get("type").getAsString();
                    
                    if ("CONNECT".equals(type)) {
                        String remoteIp = client.getInetAddress().getHostAddress();
                        
                        String remoteDeviceName = jsonObj.has("deviceName") ? jsonObj.get("deviceName").getAsString() : "Unknown Device";
                        String remoteUserName = jsonObj.has("userName") ? jsonObj.get("userName").getAsString() : "Unknown User";
                        
                        final String finalDeviceName = remoteDeviceName;
                        final String finalUserName = remoteUserName;

                    boolean accepted = false;
                    try {
                        accepted = askUserAccept("来自 " + finalUserName + " (" + remoteIp + ") 的连接请求，是否接受？");
                    } catch (Exception e) {
                        appendLog("连接请求处理中断");
                        return;
                    }
                    
                    // 在发送响应前再次检查连接状态
                    // 因为在等待用户确认期间，对方可能已经取消（关闭）了连接
                    if (accepted && !handler.checkConnectionAlive()) {
                        appendLog("对方已取消连接请求");
                        return;
                    }
                    
                    try {
                        handler.sendJson(accepted ? "{\"type\":\"ACCEPT\"}" : "{\"type\":\"REJECT\"}");
                    } catch (Exception e) {
                        appendLog("无法发送响应，可能对方已取消连接");
                        return;
                    }
                    
                    appendLog("连接请求" + (accepted ? "已接受" : "已拒绝"));
                        
                        if (accepted) {
                            Platform.runLater(() -> {
                                DeviceInfo sender = null;
                                if (discovery != null && discovery.getDeviceList() != null) {
                                    for (DeviceInfo d : discovery.getDeviceList()) {
                                        if (d.getIpAddress().equals(remoteIp)) {
                                            sender = d;
                                            break;
                                        }
                                    }
                                }
                                
                                if (sender == null) {
                                    sender = new DeviceInfo("unknown-" + remoteIp, finalDeviceName, finalUserName, remoteIp, NetworkConfig.getTcpPort());
                                }
                                
                                connectedDevice = sender;
                                updateTargetInfoBox();
                                updateActionButtons();
                            });
                        }
                        return;
                    } else if ("DISCONNECT".equals(type)) {
                        Platform.runLater(() -> {
                            appendLog("对方已断开连接");
                            connectedDevice = null;
                            listDevices.getSelectionModel().clearSelection();
                            updateTargetInfoBox();
                            updateActionButtons();
                        });
                        return;
                    }
                }
            }

            TransferRequest request = TransferRequest.fromJson(json);
            if (request == null || !request.isValid()) {
                appendLog("收到无效的传输请求");
                return;
            }

            String savePath = chooseSavePath(request.getFileName());
            if (savePath == null) {
                handler.sendMessage(
                        TransferResponse.reject(request.getTaskId(), TransferResponse.RejectReason.USER_DECLINED));
                appendLog("用户拒绝接收文件");
                return;
            }
            File outFile = new File(savePath);
            File parent = outFile.getParentFile();
            if (parent != null)
                parent.mkdirs();
            long need = Math.max(0, request.getFileSize());
            long usable = parent != null ? parent.getUsableSpace() : new File(".").getUsableSpace();
            if (usable > 0 && need > 0 && usable < need) {
                handler.sendMessage(
                        TransferResponse.reject(request.getTaskId(), TransferResponse.RejectReason.INSUFFICIENT_SPACE));
                appendLog("磁盘空间不足，已拒绝接收");
                return;
            }

            // 注册接收任务以便控制
            TransferTask task = new TransferTask(
                    request.getTaskId(),
                    savePath,
                    client.getInetAddress().getHostAddress(),
                    client.getPort(),
                    TransferTask.TransferType.RECEIVE,
                    request,
                    null);
            task.setStatus(TransferTask.TaskStatus.RUNNING);
            service.registerActiveTask(task);
            currentTaskId = task.getTaskId();
            transferPaused = false;
            Platform.runLater(() -> updateTransferButtons(true));

            // 启动监控线程，同步本地暂停/恢复状态给发送方
            new Thread(() -> {
                boolean lastWasPaused = false;
                while (!task.getStatus().equals(TransferTask.TaskStatus.COMPLETED) && 
                       !task.getStatus().equals(TransferTask.TaskStatus.FAILED) && 
                       !task.getStatus().equals(TransferTask.TaskStatus.CANCELED)) {
                    
                    if (task.getStatus() == TransferTask.TaskStatus.PAUSED) {
                        if (!lastWasPaused) {
                             try {
                                 JsonObject controlMsg = new JsonObject();
                                 controlMsg.addProperty("type", "PAUSE");
                                 handler.sendJson(new Gson().toJson(controlMsg));
                                 lastWasPaused = true;
                             } catch (Exception e) {}
                        }
                    } else if (task.getStatus() == TransferTask.TaskStatus.RUNNING) {
                         if (lastWasPaused) {
                             try {
                                 JsonObject controlMsg = new JsonObject();
                                 controlMsg.addProperty("type", "RESUME");
                                 handler.sendJson(new Gson().toJson(controlMsg));
                                 lastWasPaused = false;
                             } catch (Exception e) {}
                         }
                    }
                    try { Thread.sleep(200); } catch (InterruptedException e) { break; }
                }
            }).start();

            long existingOffset = 0;
            if (outFile.exists()) {
                long len = outFile.length();
                // 如果本地文件存在且小于远程文件，尝试续传
                if (len < request.getFileSize() && len > 0) {
                    existingOffset = len;
                    appendLog("发现未完成文件，准备从 " + formatSpeed(existingOffset) + " 处续传");
                }
            }

            handler.sendMessage(TransferResponse.accept(request.getTaskId(), savePath, existingOffset));
            Platform.runLater(() -> {
                progressBar.setProgress(0);
                lblProgress.setText("0%");
                lblSpeed.setText("速度: --");
                lblTimeRemaining.setText("剩余时间: --");
            });
            long receiveStartTime = System.currentTimeMillis();
            long lastReceivedBytes = existingOffset; // 初始已接收量
            if (request.getFileSize() == 0) {
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                }
                appendLog("收到空文件，已创建: " + outFile.getAbsolutePath());
            } else {
                long received = existingOffset;
                // 使用 RandomAccessFile 支持断点写入
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(outFile, "rw")) {
                    raf.seek(received);
                    while (true) {
                        if (task.getStatus() == TransferTask.TaskStatus.CANCELED) {
                            throw new java.io.IOException("任务已取消");
                        }
                        task.waitForResume();
                        
                        FileChunk chunk = handler.receiveChunk();
                        
                        // 处理控制分片（来自发送方的暂停/恢复信号）
                        if (chunk.isControlChunk()) {
                            String cmd = chunk.getControlCommand();
                            if ("PAUSE".equals(cmd)) {
                                task.pause();
                                transferPaused = true;
                                Platform.runLater(() -> {
                                    btnPause.setDisable(true);
                                    btnResume.setDisable(false);
                                    lblSpeed.setText("速度: 对方已暂停");
                                });
                            } else if ("RESUME".equals(cmd)) {
                                task.resume();
                                transferPaused = false;
                                Platform.runLater(() -> {
                                    btnPause.setDisable(false);
                                    btnResume.setDisable(true);
                                    lblSpeed.setText("速度: 恢复中...");
                                });
                            }
                            continue;
                        }

                        byte[] data = chunk.getData();
                        int len = chunk.getDataSize();
                        raf.write(data, 0, len);
                        received += len;
                        // appendLog("收到分片 #" + chunk.getChunkIndex() + " 长度=" + len);
                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdateTs >= 100 || received >= request.getFileSize()) {
                            lastProgressUpdateTs = now;
                            double p = request.getFileSize() > 0 ? (received * 1.0 / request.getFileSize()) : 0.0;
                            
                            // 计算接收速度
                            long timeDelta = now - receiveStartTime;
                            long bytesDelta = received - lastReceivedBytes;
                            String speedText = "速度: --";
                            String timeText = "剩余时间: --";
                            
                            if (timeDelta > 0) {
                                double speed = (received * 1000.0) / timeDelta; // 平均速度
                                speedText = "速度: " + formatSpeed(speed);
                                
                                long remaining = request.getFileSize() - received;
                                if (speed > 0 && remaining > 0) {
                                    long secondsRemaining = (long) (remaining / speed);
                                    timeText = "剩余时间: " + formatTime(secondsRemaining);
                                }
                            }
                            
                            lastReceivedBytes = received;
                            String finalSpeedText = speedText;
                            String finalTimeText = timeText;
                            
                            Platform.runLater(() -> {
                                progressBar.setProgress(p);
                                lblProgress.setText(String.format("%d%%", (int) Math.round(p * 100)));
                                lblSpeed.setText(finalSpeedText);
                                lblTimeRemaining.setText(finalTimeText);
                            });
                        }
                        if (chunk.isLastChunk())
                            break;
                    }
                }
            }
            boolean ok = md5(outFile).equals(request.getMd5());
            task.setStatus(TransferTask.TaskStatus.COMPLETED);
            appendLog("接收完成: " + outFile.getAbsolutePath() + " MD5校验: " + (ok ? "通过" : "失败"));
        } catch (ProtocolException pe) {
            appendLog("协议错误: " + pe.getMessage());
        } catch (Exception e) {
            boolean isCancel = e.getMessage() != null && e.getMessage().contains("任务已取消");
            if (isCancel) {
                appendLog("接收已取消");
            } else {
                appendLog("处理连接异常: " + e.getMessage());
            }
        } finally {
            if (currentTaskId != null) {
                service.removeActiveTask(currentTaskId);
                currentTaskId = null;
                Platform.runLater(() -> updateTransferButtons(false));
            }
        }
    }

    private boolean askUserAccept(String msg) {
        final boolean[] result = { false };
        final Object lock = new Object();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
            alert.setHeaderText(null);
            ButtonType bt = alert.showAndWait().orElse(ButtonType.CANCEL);
            synchronized (lock) {
                result[0] = bt == ButtonType.OK;
                lock.notify();
            }
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ignored) {
            }
        }
        return result[0];
    }

    private String chooseSavePath(String suggestedName) {
        final String[] path = { null };
        final Object lock = new Object();
        Platform.runLater(() -> {
            FileChooser fc = new FileChooser();
            fc.setInitialFileName(suggestedName);
            File init;
            
            // 优先使用设置中配置的保存路径
            String configuredPath = System.getProperty("file.save.path", "");
            if (!configuredPath.isEmpty()) {
                File configuredDir = new File(configuredPath);
                if (configuredDir.exists() && configuredDir.isDirectory()) {
                    init = configuredDir;
                    lastSaveDir = configuredDir; // 同步更新
                } else if (lastSaveDir != null && lastSaveDir.exists()) {
                    init = lastSaveDir;
                } else {
                    File home = new File(System.getProperty("user.home", "."));
                    File downloads = new File(home, "Downloads");
                    init = (downloads.exists() && downloads.isDirectory()) ? downloads : home;
                }
            } else if (lastSaveDir != null && lastSaveDir.exists()) {
                init = lastSaveDir;
            } else {
                File home = new File(System.getProperty("user.home", "."));
                File downloads = new File(home, "Downloads");
                init = (downloads.exists() && downloads.isDirectory()) ? downloads : home;
            }
            
            try {
                fc.setInitialDirectory(init);
            } catch (Exception ignored) {
            }
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("所有文件", "*.*"));
            File file = fc.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
            synchronized (lock) {
                path[0] = file != null ? file.getAbsolutePath() : null;
                if (file != null && file.getParentFile() != null)
                    lastSaveDir = file.getParentFile();
                lock.notify();
            }
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ignored) {
            }
        }
        return path[0];
    }

    private static String md5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest())
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void appendLog(String s) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        LogItem item = new LogItem(time, s);
        Platform.runLater(() -> {
            listLogs.getItems().add(item);
            listLogs.scrollTo(listLogs.getItems().size() - 1);
        });
    }

    private static class LogItem {
        final String time;
        final String text;
        LogItem(String time, String text) { this.time = time; this.text = text; }
    }

    public void shutdown() {
        try { stopServer(); } catch (Exception ignored) {}
        try { if (service != null) service.stopService(); } catch (Exception ignored) {}
        try { if (discovery != null && discovery.isRunning()) discovery.stop(); } catch (Exception ignored) {}
    }
    
    /**
     * 格式化速度显示
     * @param bytesPerSecond 每秒字节数
     * @return 格式化的速度字符串（如 "5.2 MB/s"）
     */
    private String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return String.format("%.0f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format("%.2f MB/s", bytesPerSecond / (1024 * 1024));
        } else {
            return String.format("%.2f GB/s", bytesPerSecond / (1024 * 1024 * 1024));
        }
    }
    
    /**
     * 格式化剩余时间显示
     * @param seconds 剩余秒数
     * @return 格式化的时间字符串（如 "5分20秒"）
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "分" + secs + "秒";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "小时" + minutes + "分";
        }
    }
    
    /**
     * 设置拖拽文件发送功能
     * (已弃用：改为在选择文件对话框中进行拖拽)
     */
    private void setupDragAndDrop() {
        // 原有的设备列表拖拽功能已禁用，统一使用新的文件选择对话框
    }
    
    /**
     * 获取指定Y坐标对应的设备索引
     */
    private int getDeviceIndexAtPosition(double y) {
        int cellHeight = 40; // 与 CSS 中的 cell-size 对应
        return (int) (y / cellHeight);
    }
    
    /**
     * 创建设备右键菜单
     */
    private ContextMenu createDeviceContextMenu(DeviceInfo device) {
        ContextMenu contextMenu = new ContextMenu();
        
        // 重命名菜单项
        MenuItem renameItem = new MenuItem("设置自定义名称...");
        renameItem.setOnAction(e -> showRenameDialog(device));
        
        // 清除自定义名称菜单项
        MenuItem clearNicknameItem = new MenuItem("清除自定义名称");
        clearNicknameItem.setOnAction(e -> {
            nicknameManager.removeNickname(device.getIpAddress());
            listDevices.refresh();
            appendLog("已清除设备别名: " + device.getIpAddress());
        });
        clearNicknameItem.setDisable(!nicknameManager.hasNickname(device.getIpAddress()));
        
        contextMenu.getItems().addAll(renameItem, clearNicknameItem);
        return contextMenu;
    }
    
    /**
     * 显示设备重命名对话框
     */
    private void showRenameDialog(DeviceInfo device) {
        TextInputDialog dialog = new TextInputDialog(
            nicknameManager.getNickname(device.getIpAddress())
        );
        dialog.setTitle("设置自定义名称");
        dialog.setHeaderText("为设备设置自定义名称");
        dialog.setContentText("设备: " + device.getIpAddress() + "\n新名称:");
        
        dialog.showAndWait().ifPresent(nickname -> {
            if (nickname != null && !nickname.trim().isEmpty()) {
                nicknameManager.setNickname(device.getIpAddress(), nickname.trim());
                listDevices.refresh();
                appendLog("设置设备别名: " + device.getIpAddress() + " -> " + nickname);
            }
        });
    }
    
    /**
     * 暂停传输
     */
    private void pauseTransfer() {
        if (currentTaskId != null && !transferPaused) {
            transferPaused = true;
            service.pauseTask(currentTaskId);
            Platform.runLater(() -> {
                btnPause.setDisable(true);
                btnResume.setDisable(false);
                lblSpeed.setText("速度: 已暂停");
                appendLog("传输已暂停");
            });
        }
    }
    
    /**
     * 恢复传输
     */
    private void resumeTransfer() {
        if (currentTaskId != null && transferPaused) {
            transferPaused = false;
            service.resumeTask(currentTaskId);
            Platform.runLater(() -> {
                btnPause.setDisable(false);
                btnResume.setDisable(true);
                lblSpeed.setText("速度: 恢复中...");
                appendLog("传输已恢复");
            });
        }
    }
    
    /**
     * 取消传输
     */
    private void cancelTransfer() {
        if (currentTaskId != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("取消传输");
            alert.setHeaderText("确认取消传输");
            alert.setContentText("确定要取消当前的文件传输吗？");
            
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    service.cancelTask(currentTaskId);
                    String cancelledTaskId = currentTaskId;
                    currentTaskId = null;
                    transferPaused = false;
                    
                    Platform.runLater(() -> {
                        progressBar.setProgress(0);
                        lblProgress.setText("已取消");
                        lblSpeed.setText("速度: --");
                        lblTimeRemaining.setText("剩余时间: --");
                        btnPause.setDisable(true);
                        btnResume.setDisable(true);
                        btnCancel.setDisable(true);
                        appendLog("传输已取消: " + cancelledTaskId);
                    });
                }
            });
        }
    }
    
    /**
     * 更新传输控制按钮状态
     */
    private void updateTransferButtons(boolean transferActive) {
        Platform.runLater(() -> {
            if (transferActive) {
                btnPause.setDisable(false);
                btnResume.setDisable(true);
                btnCancel.setDisable(false);
            } else {
                btnPause.setDisable(true);
                btnResume.setDisable(true);
                btnCancel.setDisable(true);
            }
        });
    }
    
    /**
     * 更新启动/停止发现按钮的样式
     * 启用的按钮显示为蓝色（primary-btn），禁用的按钮显示为灰色（secondary-btn）
     */
    private void updateDiscoveryButtonStyles() {
        // 启动按钮样式
        btnStart.getStyleClass().removeAll("primary-btn", "secondary-btn");
        if (btnStart.isDisabled()) {
            btnStart.getStyleClass().add("secondary-btn");
        } else {
            btnStart.getStyleClass().add("primary-btn");
        }
        
        // 停止按钮样式
        btnStop.getStyleClass().removeAll("primary-btn", "secondary-btn");
        if (btnStop.isDisabled()) {
            btnStop.getStyleClass().add("secondary-btn");
        } else {
            btnStop.getStyleClass().add("primary-btn");
        }
    }
    
    /**
     * 显示设置对话框
     */
    private void showSettingsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("设置");
        dialog.setHeaderText("应用程序设置");
        
        // 创建设置界面
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));
        
        // 本机设备名称设置
        Label deviceNameLabel = new Label("本机设备名称:");
        TextField deviceNameField = new TextField();
        
        // 获取当前设备名称
        if (discovery != null && discovery.getLocalDevice() != null) {
            String currentName = discovery.getLocalDevice().getDeviceName();
            deviceNameField.setText(currentName);
            deviceNameField.setPromptText("请输入设备名称");
        } else {
            deviceNameField.setText(System.getProperty("user.name", "未知设备"));
            deviceNameField.setPromptText("请输入设备名称");
        }
        
        Label userNameLabel = new Label("用户名:");
        TextField userNameField = new TextField();
        if (discovery != null && discovery.getLocalDevice() != null) {
            userNameField.setText(discovery.getLocalDevice().getUserName());
        } else {
            userNameField.setText(System.getProperty("user.name", ""));
        }
        userNameField.setPromptText("请输入用户名");

        // 文件选择默认路径设置
        Label choosePathLabel = new Label("文件选择默认路径:");
        TextField choosePathField = new TextField();
        Button browseChoosePathBtn = new Button("浏览...");

        String currentChoosePath = System.getProperty("file.choose.path", "");
        choosePathField.setText(currentChoosePath);
        choosePathField.setPromptText("选择点击加号后默认弹出的路径");
        choosePathField.setPrefWidth(300);

        browseChoosePathBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
            dirChooser.setTitle("选择文件选择默认路径");
            String path = choosePathField.getText();
            if (!path.isEmpty()) {
                File currentDir = new File(path);
                if (currentDir.exists() && currentDir.isDirectory()) {
                    dirChooser.setInitialDirectory(currentDir);
                }
            }
            File selectedDir = dirChooser.showDialog(dialog.getOwner());
            if (selectedDir != null) {
                choosePathField.setText(selectedDir.getAbsolutePath());
            }
        });

        HBox choosePathBox = new HBox(5);
        choosePathBox.getChildren().addAll(choosePathField, browseChoosePathBtn);
        
        // 接收文件保存路径设置
        Label savePathLabel = new Label("接收文件保存路径:");
        TextField savePathField = new TextField();
        Button browseSavePathBtn = new Button("浏览...");
        
        // 获取当前保存路径（优先使用设置的路径，否则使用 Downloads）
        String currentSavePath = System.getProperty("file.save.path", "");
        if (currentSavePath.isEmpty()) {
            File home = new File(System.getProperty("user.home", "."));
            File downloads = new File(home, "Downloads");
            currentSavePath = downloads.exists() ? downloads.getAbsolutePath() : home.getAbsolutePath();
        }
        savePathField.setText(currentSavePath);
        savePathField.setPromptText("选择接收文件的保存位置");
        savePathField.setPrefWidth(300);
        
        // 浏览按钮事件
        browseSavePathBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
            dirChooser.setTitle("选择接收文件保存路径");
            
            // 设置初始目录
            File currentDir = new File(savePathField.getText());
            if (currentDir.exists() && currentDir.isDirectory()) {
                dirChooser.setInitialDirectory(currentDir);
            }
            
            File selectedDir = dirChooser.showDialog(dialog.getOwner());
            if (selectedDir != null) {
                savePathField.setText(selectedDir.getAbsolutePath());
            }
        });
        
        HBox savePathBox = new HBox(5);
        savePathBox.getChildren().addAll(savePathField, browseSavePathBtn);
        
        Label tipLabel = new Label("提示: 修改设备信息后需要重启发现才能生效");
        tipLabel.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 11px;");
        
        grid.add(deviceNameLabel, 0, 0);
        grid.add(deviceNameField, 1, 0);
        grid.add(userNameLabel, 0, 1);
        grid.add(userNameField, 1, 1);
        grid.add(choosePathLabel, 0, 2);
        grid.add(choosePathBox, 1, 2);
        grid.add(savePathLabel, 0, 3);
        grid.add(savePathBox, 1, 3);
        grid.add(tipLabel, 0, 4, 2, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // 显示对话框并处理结果
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String newDeviceName = deviceNameField.getText().trim();
                String newUserName = userNameField.getText().trim();
                String newChoosePath = choosePathField.getText().trim();
                String newSavePath = savePathField.getText().trim();
                
                boolean hasChanges = false;
                
                // 保存设备名称
                if (!newDeviceName.isEmpty()) {
                    System.setProperty("device.name", newDeviceName);
                    appendLog("设备名称已更新为: " + newDeviceName);
                    hasChanges = true;
                }
                
                // 保存用户名
                if (!newUserName.isEmpty()) {
                    System.setProperty("user.name.custom", newUserName);
                    appendLog("用户名已更新为: " + newUserName);
                    hasChanges = true;
                }

                // 保存文件选择默认路径
                if (!newChoosePath.isEmpty()) {
                     File choosePath = new File(newChoosePath);
                     if (choosePath.exists() && choosePath.isDirectory()) {
                         System.setProperty("file.choose.path", newChoosePath);
                         appendLog("文件选择默认路径已更新为: " + newChoosePath);
                         hasChanges = true;
                     }
                }
                
                // 保存文件路径
                if (!newSavePath.isEmpty()) {
                    File savePath = new File(newSavePath);
                    if (savePath.exists() && savePath.isDirectory()) {
                        System.setProperty("file.save.path", newSavePath);
                        lastSaveDir = savePath; // 立即更新当前保存目录
                        appendLog("接收文件保存路径已更新为: " + newSavePath);
                        hasChanges = true;
                    } else {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("路径无效");
                        alert.setHeaderText("保存路径不存在");
                        alert.setContentText("请选择一个有效的文件夹路径。");
                        alert.showAndWait();
                    }
                }
                
                // 如果有更改且发现正在运行，提示用户重启
                if (hasChanges && discovery != null && discovery.isRunning()) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("设置已保存");
                    alert.setHeaderText("设置更改成功");
                    alert.setContentText("设备信息修改后，请点击'停止发现'后再点击'启动发现'以使更改生效。");
                    alert.showAndWait();
                } else if (hasChanges) {
                    appendLog("设置已保存");
                }
            }
        });
    }
}
