package com.example.view;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class LogTab {
    
    // 日志相关常量
    private static final String LOG_DIRECTORY = "logs";

    public static VBox create() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        // 创建日志目录
        createLogDirectoryIfNotExists();

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPromptText("最近日志");

        // 默认宽度适当调大
        logArea.setPrefWidth(800);
        logArea.setPrefHeight(400);

        // 让TextArea宽度/高度随父容器动态调整
        logArea.setMaxWidth(Double.MAX_VALUE);
        logArea.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(logArea, javafx.scene.layout.Priority.ALWAYS);

        // 移除动态字体大小调整监听器，避免窗口闪烁
        // 设置固定字体大小
        logArea.setStyle("-fx-font-size: 14px;");

        // 日志管理按钮
        HBox buttonBox = new HBox(10);
        
        // 刷新日志按钮
        Button refreshButton = new Button("刷新日志");
        refreshButton.setOnAction(e -> {
            logArea.clear();
            logArea.appendText("日志区域已清空\n");
        });
        
        // 下载单个日志文件按钮
        Button downloadButton = new Button("下载完整日志");
        downloadButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择日志文件");
            File logDir = new File(LOG_DIRECTORY);
            if (logDir.exists() && logDir.isDirectory()) {
                fileChooser.setInitialDirectory(logDir);
            }
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Log files (*.log)", "*.log"));
            File selectedFile = fileChooser.showOpenDialog(layout.getScene().getWindow());
            if (selectedFile != null) {
                try {
                    String logContent = new String(Files.readAllBytes(selectedFile.toPath()));
                    logArea.clear();
                    logArea.appendText("已加载日志文件: " + selectedFile.getName() + "\n\n");
                    logArea.appendText(logContent);
                } catch (IOException ex) {
                    logArea.appendText("读取日志文件失败: " + ex.getMessage() + "\n");
                }
            }
        });
        
        // 打开日志目录按钮
        Button openLogDirButton = new Button("打开日志目录");
        openLogDirButton.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().open(new File(LOG_DIRECTORY));
            } catch (IOException ex) {
                logArea.appendText("打开日志目录失败: " + ex.getMessage() + "\n");
            }
        });
        
        buttonBox.getChildren().addAll(refreshButton, downloadButton, openLogDirButton);
        layout.getChildren().addAll(new Label("最近日志"), logArea, buttonBox);

        return layout;
    }
    
    /**
     * 确保日志目录存在
     */
    private static void createLogDirectoryIfNotExists() {
        try {
            Files.createDirectories(Paths.get(LOG_DIRECTORY));
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(LogTab.class).error("创建日志目录失败", e);
        }
    }
}
