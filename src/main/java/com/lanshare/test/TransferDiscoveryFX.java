package com.lanshare.test;

import com.lanshare.network.config.NetworkConfig;
import com.lanshare.network.discovery.DeviceDiscovery;
import com.lanshare.network.discovery.UdpBrodcaster;
import com.lanshare.network.model.DeviceInfo;
import com.lanshare.network.protocol.FileChunk;
import com.lanshare.network.protocol.ProtocolException;
import com.lanshare.network.protocol.ProtocolHandler;
import com.lanshare.network.protocol.TransferRequest;
import com.lanshare.network.protocol.TransferResponse;
import com.lanshare.network.transfer.FileTransferService;
import com.lanshare.network.transfer.TransferTask;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.lanshare.test.ui.TransferDiscoveryController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferDiscoveryFX extends Application {
    private FileTransferService service;
    private DeviceDiscovery discovery;

    private final ListView<DeviceInfo> deviceList = new ListView<>();
    private final TextArea logArea = new TextArea();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressLabel = new Label("0%");
    private final Button startBtn = new Button("启动发现");
    private final Button stopBtn = new Button("停止发现");
    private final Button refreshBtn = new Button("刷新广播");
    private final Button connectBtn = new Button("建立连接");
    private final Button chooseBtn = new Button("选择文件");
    private final Button sendBtn = new Button("发送到选中设备");
    private final CheckBox localServerCheck = new CheckBox("本机开启接收");
    private final FileChooser chooser = new FileChooser();
    private File selectedFile;
    private File lastSaveDir;
    private ExecutorService serverExecutor;
    private volatile boolean serverRunning = false;
    private volatile long lastProgressUpdateTs = 0L;

    @Override
    public void start(Stage stage) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/ui/transfer_discovery.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1000, 640);
            scene.getStylesheets().add(getClass().getResource("/ui/theme.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("LanShare");
            TransferDiscoveryController controller = loader.getController();
            stage.setOnCloseRequest(e -> {
                try { controller.shutdown(); } catch (Exception ignored) {}
                Platform.exit();
                System.exit(0);
            });
            stage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void startDiscovery() {
        try {
            if (!discovery.isRunning()) {
                discovery.initialize();
                deviceList.setItems(discovery.getDeviceList());
                discovery.getDeviceList()
                        .addListener((ListChangeListener<DeviceInfo>) change -> Platform.runLater(deviceList::refresh));
                discovery.start();
                appendLog("设备发现已启动");
                // TransferDiscoveryFX.java 里 startDiscovery() 加两行
                discovery.printDevices(); // 看本机设备信息
                System.out.println("checksum=" + discovery.getDeviceListChecksum());
                // discovery 没有 getUdpBroadcaster() 方法，移除无效调用
                // UdpBrodcaster broadcaster = discovery.getUdpBroadcaster();
                // UdpBrodcaster 没有 getLocalPort() 方法，先注释掉
                // System.out.println("localPort=" + broadcaster.getLocalPort());
            }
        } catch (Exception ex) {
            appendLog("启动发现失败: " + ex.getMessage());
        }
        service.startService();
    }

    private void stopDiscovery() {
        try {
            discovery.stop();
            appendLog("设备发现已停止");
        } catch (Exception ex) {
            appendLog("停止发现失败: " + ex.getMessage());
        }
        service.stopService();
    }

    private void refreshDeviceView() {
        deviceList.refresh();
    }

    private void chooseFile(Stage stage) {
        selectedFile = chooser.showOpenDialog(stage);
        if (selectedFile != null) {
            appendLog("选择文件: " + selectedFile.getAbsolutePath());
            progressBar.setProgress(0);
            progressLabel.setText("0%");
        }
    }

    private void connectSelected() {
        DeviceInfo target = deviceList.getSelectionModel().getSelectedItem();
        if (target == null) {
            appendLog("请先选择目标设备");
            return;
        }
        try (Socket socket = new Socket(target.getIpAddress(), target.getPort());
                ProtocolHandler handler = new ProtocolHandler(socket)) {
            String hello = "{\"type\":\"CONNECT\",\"from\":\"" + discovery.getLocalDevice().getIpAddress() + "\"}";
            handler.sendJson(hello);
            String resp = handler.receiveJson();
            appendLog("连接响应: " + resp);
        } catch (Exception e) {
            appendLog("建立连接失败: " + e.getMessage());
        }
    }

    private void sendToSelected() {
        DeviceInfo target = deviceList.getSelectionModel().getSelectedItem();
        if (target == null) {
            appendLog("请先选择目标设备");
            return;
        }
        if (selectedFile == null) {
            appendLog("请先选择文件");
            return;
        }
        appendLog("开始发送到: " + target.getIpAddress() + ":" + target.getPort());
        service.sendFile(selectedFile.getAbsolutePath(), target.getIpAddress());
    }

    private void startServer() {
        if (serverRunning)
            return;
        serverRunning = true;
        serverExecutor = Executors.newFixedThreadPool(2);
        serverExecutor.submit(() -> {
            try (ServerSocket server = new ServerSocket(NetworkConfig.getTcpPort())) {
                appendLog("接收端启动，监听: " + NetworkConfig.getTcpPort());
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
            if (json != null && json.contains("\"type\":\"CONNECT\"")) {
                boolean accepted = askUserAccept("来自 " + client.getInetAddress().getHostAddress() + " 的连接请求，是否接受？");
                handler.sendJson(accepted ? "{\"type\":\"ACCEPT\"}" : "{\"type\":\"REJECT\"}");
                appendLog("连接请求" + (accepted ? "已接受" : "已拒绝"));
                return;
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
            handler.sendMessage(TransferResponse.accept(request.getTaskId(), savePath));
            Platform.runLater(() -> {
                progressBar.setProgress(0);
                progressLabel.setText("0%");
            });
            if (request.getFileSize() == 0) {
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                }
                appendLog("收到空文件，已创建: " + outFile.getAbsolutePath());
            } else {
                long received = 0L;
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    while (true) {
                        FileChunk chunk = handler.receiveChunk();
                        byte[] data = chunk.getData();
                        int len = chunk.getDataSize();
                        fos.write(data, 0, len);
                        received += len;
                        appendLog("收到分片 #" + chunk.getChunkIndex() + " 长度=" + len);
                        long now = System.currentTimeMillis();
                        if (now - lastProgressUpdateTs >= 100 || received >= request.getFileSize()) {
                            lastProgressUpdateTs = now;
                            double p = request.getFileSize() > 0 ? (received * 1.0 / request.getFileSize()) : 0.0;
                            Platform.runLater(() -> {
                                progressBar.setProgress(p);
                                progressLabel.setText(String.format("%d%%", (int) Math.round(p * 100)));
                            });
                        }
                        if (chunk.isLastChunk())
                            break;
                    }
                }
            }
            boolean ok = md5(outFile).equals(request.getMd5());
            appendLog("接收完成: " + outFile.getAbsolutePath() + " MD5校验: " + (ok ? "通过" : "失败"));
        } catch (ProtocolException pe) {
            appendLog("协议错误: " + pe.getMessage());
        } catch (Exception e) {
            appendLog("处理连接异常: " + e.getMessage());
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
            File init = null;
            if (lastSaveDir != null && lastSaveDir.exists()) {
                init = lastSaveDir;
            } else {
                File home = new File(System.getProperty("user.home", "."));
                File downloads = new File(home, "Downloads");
                if (downloads.exists() && downloads.isDirectory())
                    init = downloads;
                else
                    init = home;
            }
            try {
                fc.setInitialDirectory(init);
            } catch (Exception ignored) {
            }
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("所有文件", "*.*"));
            File file = fc.showSaveDialog(null);
            synchronized (lock) {
                path[0] = file != null ? file.getAbsolutePath() : null;
                if (file != null && file.getParentFile() != null) {
                    lastSaveDir = file.getParentFile();
                }
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
        Platform.runLater(() -> logArea.appendText(s + "\n"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
