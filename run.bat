@echo off
start "" javaw ^
  --module-path target\dependency ^
  --add-modules javafx.controls,javafx.fxml ^
  --enable-native-access=javafx.graphics ^
  -cp "LanShare.jar;target\dependency\*" ^
  com.lanshare.test.TransferDiscoveryFX
