package com.wiyi.ss.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public class LogTab {

    public static VBox create() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

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

        // 动态调整字体大小
        layout.widthProperty().addListener((obs, oldVal, newVal) -> {
            double width = newVal.doubleValue();
            // 计算字体大小，最小12，最大24，随宽度变化
            double fontSize = Math.max(12, Math.min(24, width / 50));
            logArea.setStyle("-fx-font-size: " + fontSize + "px;");
        });

        Button refreshButton = new Button("刷新日志");
        
        // The file download can be implemented using a FileChooser
        Button downloadButton = new Button("下载完整日志");

        layout.getChildren().addAll(new Label("最近日志"), logArea, refreshButton, downloadButton);

        return layout;
    }
}
