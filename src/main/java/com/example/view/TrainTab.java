package com.example.view;

import org.json.JSONObject;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
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

        // 测试控制区域
        HBox testControls = new HBox(10);
        Label testCountLabel = new Label("测试次数:");
        TextField testCountField = new TextField("10");
        testCountField.setPrefWidth(50);
        Button startTestButton = new Button("开始测试");
        testControls.getChildren().addAll(testCountLabel, testCountField, startTestButton);

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefHeight(200);
        resultArea.setPromptText("测试结果（显示验证码过期则说明成功）");

        // 为开始测试按钮添加事件处理器
        startTestButton.setOnAction(e -> {
            int n;
            try {
                n = Integer.parseInt(testCountField.getText());
                if (n <= 0) {
                    resultArea.setText("测试次数必须为正整数。");
                    return;
                }
            } catch (NumberFormatException ex) {
                resultArea.setText("请输入有效的测试次数。");
                return;
            }

            startTestButton.setDisable(true);
            resultArea.clear();
            resultArea.appendText("测试开始...\n");

            new Thread(() -> {
                System.out.println("Starting validation test for " + n + " iterations...");
                javafx.application.Platform.runLater(() -> resultArea.appendText("Starting validation test for " + n + " iterations...\n"));
                
                int successCount = 0;
                double totalTime = 0;

                for (int i = 0; i < n; i++) {
                    final int currentTest = i + 1;
                    System.out.println("--- Test " + currentTest + " ---");
                    javafx.application.Platform.runLater(() -> resultArea.appendText("--- Test " + currentTest + " ---\n"));
                    
                    try {
                        long startTime = System.currentTimeMillis();

                        // 1. Register and get gt/challenge
                        String gtChallengeJson = com.example.geetest.TripleValidator.registerTest();
                        if (gtChallengeJson == null || gtChallengeJson.isEmpty()) {
                            String failMsg = "Failed to get gt and challenge";
                            System.out.println(failMsg);
                            javafx.application.Platform.runLater(() -> resultArea.appendText(failMsg + "\n"));
                            continue;
                        }
                        JSONObject jsonObj = new JSONObject(gtChallengeJson);
                        String gt = jsonObj.getString("gt");
                        String challenge = jsonObj.getString("challenge");
                        System.out.println("Successfully registered. gt: " + gt);
                        javafx.application.Platform.runLater(() -> resultArea.appendText("Successfully registered. gt: " + gt + "\n"));

                        // 2. Validate
                        String validateResult = com.example.geetest.TripleValidator.simpleMatchRetry(gt, challenge);
                        
                        long endTime = System.currentTimeMillis();
                        double elapsedTime = (endTime - startTime) / 1000.0;
                        totalTime += elapsedTime;

                        if (validateResult != null && !validateResult.isEmpty()) {
                            successCount++;
                            String successMsg = String.format("Test %d: Result = %s, Time = %.4fs", currentTest, validateResult, elapsedTime);
                            System.out.println(successMsg);
                            javafx.application.Platform.runLater(() -> resultArea.appendText(successMsg + "\n"));
                        } else {
                            String failMsg = String.format("Test %d: FAILED! Result = %s, Time = %.4fs", currentTest, validateResult, elapsedTime);
                            System.err.println(failMsg);
                            javafx.application.Platform.runLater(() -> resultArea.appendText(failMsg + "\n"));
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        final String errorLog = String.format("Test %d: Exception: %s", currentTest, ex.getMessage());
                        System.err.println(errorLog);
                        javafx.application.Platform.runLater(() -> resultArea.appendText(errorLog + "\n"));
                    }
                }

                double accuracy = (double) successCount / n * 100;
                double avgTime = totalTime / n;
                
                String summary = String.format("\n✅ Testing complete. Total iterations: %d\n✅ Accuracy: %.2f%%\n✅ Average Time: %.4fs\n", n, accuracy, avgTime);
                System.out.println(summary);
                
                javafx.application.Platform.runLater(() -> {
                    resultArea.appendText(summary);
                    startTestButton.setDisable(false);
                });
            }).start();
        });

        layout.getChildren().addAll(description, captchaSection, testControls, resultArea);

        return layout;
    }
}
