package com.wiyi.ss.view;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.wiyi.ss.task.BuyTask;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GoTab {

    private static List<File> uploadedFiles = new ArrayList<>();
    private static TextArea fileContentArea;
    private static TextField timeField;
    private static TextField intervalSpinner;
    private static ChoiceBox<String> modeChoice;
    private static TextField totalAttemptsSpinner;
    private static ChoiceBox<String> logDisplayChoice;

    public static VBox create() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        // 1. File upload section
        VBox fileUploadSection = new VBox(5);
        Label uploadLabel = new Label("上传多个配置文件,每一个上传的文件都会启动一个抢票程序");
        Button uploadButton = new Button("上传文件");
        fileContentArea = new TextArea();
        fileContentArea.setEditable(false);
        fileContentArea.setPrefRowCount(5);
        fileUploadSection.getChildren().addAll(uploadLabel, uploadButton, fileContentArea);

        uploadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择配置文件");
            // 默认打开程序当前相对目录的 ./configs 文件夹
            java.io.File defaultDir = new java.io.File("./configs");
            if (defaultDir.exists() && defaultDir.isDirectory()) {
                fileChooser.setInitialDirectory(defaultDir.getAbsoluteFile());
            }
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
            List<File> selectedFiles = fileChooser.showOpenMultipleDialog(layout.getScene().getWindow());
            if (selectedFiles != null) {
                uploadedFiles.clear();
                uploadedFiles.addAll(selectedFiles);
                StringBuilder sb = new StringBuilder();
                for (File file : selectedFiles) {
                    sb.append(file.getAbsolutePath()).append("\n");
                }
                fileContentArea.setText(sb.toString());
            }
        });

        // 2. Time selection section
        VBox timeSection = new VBox(5);
        timeSection.setStyle("-fx-border-color: #FFC0CB; -fx-border-width: 1px; -fx-padding: 10;");
        Label timeLabel = new Label("程序已经提前帮你校准时间，请设置成开票时间。切勿设置为开票前时间，否则有封号风险！");
        timeLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        
        Label timePickerLabel = new Label("输入抢票时间 (格式: yyyy-MM-dd HH:mm:ss)，留空则立即开始");
        
        HBox timeInputBox = new HBox(5);
        timeField = new TextField();
        timeField.setPromptText("yyyy-MM-dd HH:mm:ss");
        
        timeInputBox.getChildren().addAll(new Label("抢票时间:"), timeField);
        
        timeSection.getChildren().addAll(timeLabel, timePickerLabel, timeInputBox);

        // 3. Time offset section
        TitledPane timeOffsetPane = new TitledPane("手动设置/更新时间偏差", new VBox(5));
        VBox timeOffsetContent = new VBox(5);
        TextField timeOffsetField = new TextField("0");
        timeOffsetField.setPromptText("当前脚本时间偏差 (单位: ms)");
        Button refreshTimeButton = new Button("点击自动更新时间偏差");
        timeOffsetContent.getChildren().addAll(timeOffsetField, refreshTimeButton);
        timeOffsetPane.setContent(timeOffsetContent);
        timeOffsetPane.setExpanded(false);

        // 4. Captcha method section
        VBox captchaSection = new VBox(5);
        Label captchaLabel = new Label("过验证码的方式");
        ToggleGroup captchaGroup = new ToggleGroup();
        RadioButton localCaptcha = new RadioButton("本地过验证码v2(Amorter提供)");
        localCaptcha.setToggleGroup(captchaGroup);
        localCaptcha.setSelected(true);
        captchaSection.getChildren().addAll(captchaLabel, localCaptcha);

        // 5. Accordions for other settings
        Accordion settingsAccordion = new Accordion();
        
        // Proxy settings
        TitledPane proxyPane = new TitledPane("填写你的HTTP/SOCKS代理服务器[可选]", new VBox());
        VBox proxyContent = new VBox(5);
        TextField proxyField = new TextField();
        proxyField.setPromptText("例如： http://127.0.0.1:8080,http://127.0.0.1:8081");
        Button testProxyButton = new Button("🔍 测试代理连通性");
        
        // 添加代理测试功能
        testProxyButton.setOnAction(e -> {
            String proxyInput = proxyField.getText();
            final String finalProxyString = (proxyInput == null || proxyInput.trim().isEmpty()) ? "none" : proxyInput;

            // 检查是否已加载cookies（即已登录账号）
            final boolean[] hasCookies = {false};
            final String[] cookiesInfo = {""};
            try {
                // 尝试从BuyTask的静态方法获取最近一次加载的cookie配置
                java.lang.reflect.Method getLastCookiesMethod = com.wiyi.ss.task.BuyTask.class.getDeclaredMethod("getLastLoadedCookies");
                getLastCookiesMethod.setAccessible(true);
                Object cookies = getLastCookiesMethod.invoke(null);
                if (cookies != null && cookies.toString().length() > 0) {
                    hasCookies[0] = true;
                    cookiesInfo[0] = cookies.toString();
                }
            } catch (Exception ex) {
                // 兼容未实现静态方法时的情况
                hasCookies[0] = false;
            }

            // 显示测试进行中的对话框
            Stage testStage = new Stage();
            testStage.setTitle("代理连通性测试");
            testStage.initModality(Modality.APPLICATION_MODAL);

            VBox testLayout = new VBox(10);
            testLayout.setPadding(new Insets(20));

            Label statusLabel = new Label();
            statusLabel.setStyle("-fx-font-size: 14px;");

            TextArea resultArea = new TextArea();
            resultArea.setEditable(false);
            resultArea.setPrefRowCount(15);
            resultArea.setPrefColumnCount(60);

            Button closeButton = new Button("关闭");
            closeButton.setOnAction(ev -> testStage.close());
            closeButton.setDisable(true); // 测试期间禁用关闭按钮

            testLayout.getChildren().addAll(statusLabel, resultArea, closeButton);

            Scene testScene = new Scene(testLayout, 700, 500);
            testStage.setScene(testScene);
            testStage.show();

            if (!hasCookies[0]) {
                statusLabel.setText("账号未登录，无法进行带账号的代理连通性测试。");
                resultArea.setText("请先登录账号并加载cookie配置后再测试。");
                closeButton.setDisable(false);
                return;
            }

            statusLabel.setText("正在使用账号配置测试代理连通性，请稍候...");

            // 在后台线程中执行代理测试
            Thread testThread = new Thread(() -> {
                try {
                    // 这里可根据项目实际情况，构造带cookie的请求进行测试
                    String result = com.wiyi.ss.util.ProxyTester.testProxyConnectivity(finalProxyString, 10);

                    javafx.application.Platform.runLater(() -> {
                        statusLabel.setText("代理测试完成（已加载账号配置）");
                        String cookiePreview = cookiesInfo[0].length() > 200 ? cookiesInfo[0].substring(0, 200) + "..." : cookiesInfo[0];
                        resultArea.setText(result + "\n\n已加载cookie信息（部分预览）:\n" + cookiePreview);
                        closeButton.setDisable(false);
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        statusLabel.setText("代理测试失败");
                        resultArea.setText("测试过程中发生错误:\n" + ex.getMessage());
                        closeButton.setDisable(false);
                    });
                }
            });
            testThread.setDaemon(true);
            testThread.start();
        });
        
        proxyContent.getChildren().addAll(proxyField, testProxyButton);
        proxyPane.setContent(proxyContent);
        
        // Audio settings
        TitledPane audioPane = new TitledPane("配置抢票成功后播放音乐[可选]", new VBox());
        VBox audioContent = new VBox(5);
        Button audioUploadButton = new Button("上传提示声音[只支持格式wav]");
        audioContent.getChildren().add(audioUploadButton);
        audioPane.setContent(audioContent);
        
        // Notification settings
        TitledPane notifyPane = new TitledPane("配置抢票推送消息[可选]", new VBox());
        VBox notifyContent = new VBox(5);
        TextField serverChanField = new TextField();
        serverChanField.setId("serverChanField");
        serverChanField.setPromptText("Server酱的SendKey");
        TextField pushPlusField = new TextField();
        pushPlusField.setId("pushPlusField");
        pushPlusField.setPromptText("PushPlus的Token");
        TextField ntfyField = new TextField();
        ntfyField.setPromptText("Ntfy服务器URL");
        notifyContent.getChildren().addAll(
            new Label("Server酱:"), serverChanField,
            new Label("PushPlus:"), pushPlusField,
            new Label("Ntfy:"), ntfyField
        );
        notifyPane.setContent(notifyContent);
        
        settingsAccordion.getPanes().addAll(proxyPane, audioPane, notifyPane);

        // 6. Interval and mode section
        GridPane intervalGrid = new GridPane();
        intervalGrid.setHgap(10);
        intervalGrid.setVgap(5);
        
        intervalSpinner = new TextField("300");
        modeChoice = new ChoiceBox<>();
        modeChoice.getItems().addAll("无限", "有限");
        modeChoice.setValue("无限");
        
        totalAttemptsSpinner = new TextField("100");
        totalAttemptsSpinner.setVisible(false);
        
        logDisplayChoice = new ChoiceBox<>();
        logDisplayChoice.getItems().addAll("终端", "网页");
        logDisplayChoice.setValue("终端");
        
        intervalGrid.add(new Label("抢票间隔(毫秒):"), 0, 0);
        intervalGrid.add(intervalSpinner, 1, 0);
        intervalGrid.add(new Label("抢票次数:"), 0, 1);
        intervalGrid.add(modeChoice, 1, 1);
        intervalGrid.add(new Label("总次数:"), 0, 2);
        intervalGrid.add(totalAttemptsSpinner, 1, 2);
        intervalGrid.add(new Label("日志显示方式:"), 0, 3);
        intervalGrid.add(logDisplayChoice, 1, 3);
        
        modeChoice.setOnAction(e -> {
            totalAttemptsSpinner.setVisible("有限".equals(modeChoice.getValue()));
        });

        // 7. Start button
        Button startButton = new Button("开始抢票");
        startButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 10px 20px;");
        
        startButton.setOnAction(e -> {
            startTicketBuying();
        });

        layout.getChildren().addAll(
            fileUploadSection, 
            timeSection, 
            new Accordion(timeOffsetPane), 
            captchaSection, 
            settingsAccordion, 
            intervalGrid, 
            startButton
        );

        return layout;
    }

    private static void startTicketBuying() {
        if (uploadedFiles.isEmpty()) {
            showAlert("错误", "请先上传配置文件");
            return;
        }

        for (File configFile : uploadedFiles) {
            // 为每个配置文件创建一个独立的日志窗口和任务
            Stage logStage = new Stage();
            logStage.setTitle("抢票日志 - " + configFile.getName());
            logStage.initModality(Modality.NONE);
            
            VBox logLayout = new VBox(10);
            logLayout.setPadding(new Insets(10));
            
            TextArea logArea = new TextArea();
            logArea.setEditable(false);
            // 让文本框自动填充父容器
            logArea.setPrefRowCount(20);
            logArea.setPrefColumnCount(60);
            logArea.setWrapText(true);
            VBox.setVgrow(logArea, javafx.scene.layout.Priority.ALWAYS);

            Button stopButton = new Button("停止抢票");
            stopButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");

            logLayout.getChildren().addAll(logArea, stopButton);

            Scene logScene = new Scene(logLayout, 600, 400);
            logStage.setScene(logScene);

            // 绑定文本框宽高到窗口
            logArea.prefWidthProperty().bind(logLayout.widthProperty());
            logArea.prefHeightProperty().bind(logLayout.heightProperty().subtract(stopButton.heightProperty()).subtract(20));

            logStage.show();
            
            // Start ticket buying process
            String targetTime = timeField.getText();
            
            logArea.appendText("开始抢票任务: " + configFile.getName() + "\n");
            if (targetTime.isEmpty()) {
                logArea.appendText("目标时间: 立即执行\n");
            } else {
                logArea.appendText("目标时间: " + targetTime + "\n");
            }
            logArea.appendText("间隔: " + intervalSpinner.getText() + "ms\n");
            logArea.appendText("模式: " + modeChoice.getValue() + "\n");
            logArea.appendText("=================================\n");

            // Get tokens from the main scene, not the log stage scene
            String serverChanToken = ((TextField) fileContentArea.getScene().lookup("#serverChanField")).getText();
            String pushPlusToken = ((TextField) fileContentArea.getScene().lookup("#pushPlusField")).getText();
            
            BuyTask task = new BuyTask(
                configFile,
                targetTime,
                Integer.parseInt(intervalSpinner.getText()),
                modeChoice.getValue(),
                Integer.parseInt(totalAttemptsSpinner.getText()),
                logArea,
                serverChanToken,
                pushPlusToken
            );
            Thread taskThread = new Thread(task);
            taskThread.setDaemon(true);
            taskThread.start();
            
            stopButton.setOnAction(e -> {
                taskThread.interrupt();
                logArea.appendText("停止抢票任务: " + configFile.getName() + "\n");
            });
        }
    }


    private static void showAlert(String title, String message) {
        Stage alertStage = new Stage();
        alertStage.setTitle(title);
        alertStage.initModality(Modality.APPLICATION_MODAL);
        
        VBox alertLayout = new VBox(10);
        alertLayout.setPadding(new Insets(20));
        alertLayout.getChildren().addAll(
            new Label(message),
            new Button("确定") {{ setOnAction(e -> alertStage.close()); }}
        );
        
        Scene alertScene = new Scene(alertLayout, 300, 150);
        alertStage.setScene(alertScene);
        alertStage.showAndWait();
    }
}
