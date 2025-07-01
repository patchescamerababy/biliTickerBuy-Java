package com.example.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

public class TrainTab {

    public static VBox create() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        Label description = new Label("> 补充\n\n在这里，你可以测试本地过验证码是否可行");
        
        VBox captchaSection = new VBox(5);
        Label captchaLabel = new Label("过验证码的方式");
        ToggleGroup captchaGroup = new ToggleGroup();
        // In a real application, these would be populated from a list like in the Python code
        RadioButton rb1 = new RadioButton("本地过验证码v2(Amorter提供)");
        rb1.setToggleGroup(captchaGroup);
        rb1.setSelected(true);
        captchaSection.getChildren().addAll(captchaLabel, rb1);

        Button startTestButton = new Button("开始测试");
        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPromptText("测试结果（显示验证码过期则说明成功）");

        layout.getChildren().addAll(description, captchaSection, startTestButton, resultArea);

        return layout;
    }
}
