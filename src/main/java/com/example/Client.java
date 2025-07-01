package com.example;

import com.example.view.GoTab;
import com.example.view.LogTab;
import com.example.view.ProblemsTab;
import com.example.view.SettingsTab;
import com.example.view.TrainTab;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class Client extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("biliTickerBuy");

        TabPane tabPane = new TabPane();

        Tab configTab = new Tab("生成配置");
        configTab.setContent(SettingsTab.create());
        Tab buyTab = new Tab("操作抢票");
        buyTab.setContent(GoTab.create());
        Tab captchaTab = new Tab("过码测试");
        captchaTab.setContent(TrainTab.create());
        Tab aboutTab = new Tab("项目说明");
        aboutTab.setContent(ProblemsTab.create());
        Tab logTab = new Tab("日志查看");
        logTab.setContent(LogTab.create());

        // Prevent tabs from being closed
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabPane.getTabs().addAll(configTab, buyTab, captchaTab, aboutTab, logTab);

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        
        // 设置主窗口关闭事件处理，确保所有线程被正确中断
        primaryStage.setOnCloseRequest(e -> {
            System.out.println("主窗口正在关闭，停止所有抢票任务...");
            GoTab.stopAllTasks();
            System.exit(0);
        });
        
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
