package com.example.view;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import com.example.service.LoginService;
import com.example.util.BiliRequest;
import com.example.util.CookieManager;

import javafx.geometry.Insets;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import okhttp3.Response;

public class SettingsTab {

    private static TextField usernameField;
    private static TextField cookieFileField;
    private static BiliRequest biliRequest;
    private static ChoiceBox<String> ticketChoice;
    private static ChoiceBox<String> contactChoice;
    private static ChoiceBox<String> addressChoice;
    private static VBox ticketDetailsPane;
    private static VBox idCheckboxes;
    private static List<JSONObject> ticketValue = new ArrayList<>();
    private static List<JSONObject> buyerValue = new ArrayList<>();
    private static List<JSONObject> addrValue = new ArrayList<>();
    private static String projectName = "";
    private static Accordion phoneAccordion;

    public static VBox create() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        // 1. Warning message
        Label warningLabel = new Label("⚠️ 使用前必读\n请确保在抢票前已完成以下配置：\n- 收货地址：会员购中心 → 地址管理\n- 购买人信息：会员购中心 → 购买人信息\n> 即使暂时不需要，也请提前填写。否则生成表单时将没有任何选项。");
        warningLabel.setStyle("-fx-background-color: #FFFFE0; -fx-padding: 10px; -fx-border-color: #F0E68C; -fx-border-width: 1px;");
        warningLabel.setPrefHeight(100);

        // 2. Login section
        VBox loginSection = new VBox(5);
        loginSection.setPadding(new Insets(10));
        loginSection.setStyle("-fx-border-color: #FFDAB9; -fx-border-width: 1px;");

        Text loginHelpText = new Text("如果遇到登录问题，请使用 备用登录入口");

        GridPane loginGrid = new GridPane();
        loginGrid.setHgap(10);
        loginGrid.setVgap(5);

        usernameField = new TextField("未登录");
        usernameField.setEditable(false);

        cookieFileField = new TextField();
        cookieFileField.setEditable(false);

        loginGrid.add(new Label("账号名称:"), 0, 0);
        loginGrid.add(usernameField, 1, 0);
        loginGrid.add(new Label("当前登录信息文件:"), 0, 1);
        loginGrid.add(cookieFileField, 1, 1);

        // Chromium path selection
        VBox chromiumPathSection = new VBox(5);
        Label chromiumPathLabel = new Label("浏览器路径配置（可选，用于解决默认Chromium下载问题）：");
        chromiumPathLabel.setStyle("-fx-font-weight: bold;");
        
        HBox chromiumPathBox = new HBox(10);
        TextField chromiumPathField = new TextField();
        chromiumPathField.setPromptText("留空使用默认Chromium，或指定本地浏览器路径");
        chromiumPathField.setPrefWidth(400);
        Button selectChromiumButton = new Button("浏览选择");
        chromiumPathBox.getChildren().addAll(new Label("浏览器路径:"), chromiumPathField, selectChromiumButton);

        // 常见路径快捷按钮
        HBox quickPathBox = new HBox(5);
        Label quickLabel = new Label("常见路径:");
        Button chromeButton = new Button("Chrome");
        Button edgeButton = new Button("Edge");
        Button clearButton = new Button("清空");
        
        chromeButton.setStyle("-fx-font-size: 10px;");
        edgeButton.setStyle("-fx-font-size: 10px;");
        clearButton.setStyle("-fx-font-size: 10px;");
        
        quickPathBox.getChildren().addAll(quickLabel, chromeButton, edgeButton, clearButton);

        selectChromiumButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择浏览器可执行文件");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("可执行文件", "*.exe", "*.app", "*"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
            );
            
            // 设置初始目录为常见的浏览器安装目录
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                File programFiles = new File("C:\\Program Files\\Google\\Chrome\\Application");
                if (programFiles.exists()) {
                    fileChooser.setInitialDirectory(programFiles);
                }
            }
            
            File selectedFile = fileChooser.showOpenDialog(layout.getScene().getWindow());
            if (selectedFile != null) {
                chromiumPathField.setText(selectedFile.getAbsolutePath());
            }
        });

        // 快捷路径按钮事件
        chromeButton.setOnAction(e -> {
            String os = System.getProperty("os.name").toLowerCase();
            String chromePath = "";
            if (os.contains("win")) {
                // 尝试多个可能的Chrome路径
                String[] chromePaths = {
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                    System.getProperty("user.home") + "\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe"
                };
                for (String path : chromePaths) {
                    if (new File(path).exists()) {
                        chromePath = path;
                        break;
                    }
                }
            } else if (os.contains("mac")) {
                chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
            } else {
                chromePath = "/usr/bin/google-chrome";
            }
            
            if (!chromePath.isEmpty() && new File(chromePath).exists()) {
                chromiumPathField.setText(chromePath);
            } else {
                usernameField.setText("未找到Chrome，请手动选择路径");
            }
        });

        edgeButton.setOnAction(e -> {
            String os = System.getProperty("os.name").toLowerCase();
            String edgePath = "";
            if (os.contains("win")) {
                String[] edgePaths = {
                    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
                };
                for (String path : edgePaths) {
                    if (new File(path).exists()) {
                        edgePath = path;
                        break;
                    }
                }
            } else if (os.contains("mac")) {
                edgePath = "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge";
            }
            
            if (!edgePath.isEmpty() && new File(edgePath).exists()) {
                chromiumPathField.setText(edgePath);
            } else {
                usernameField.setText("未找到Edge，请手动选择路径");
            }
        });

        clearButton.setOnAction(e -> {
            chromiumPathField.clear();
        });

        chromiumPathSection.getChildren().addAll(chromiumPathLabel, chromiumPathBox, quickPathBox);

        HBox loginButtons = new HBox(10);
        Button importButton = new Button("导入Cookie文件");
        Button loginButton = new Button("登录");
        loginButtons.getChildren().addAll(importButton, loginButton);

        loginButton.setOnAction(e -> {
            String chromiumPath = chromiumPathField.getText();
            
            // 显示登录中状态
            loginButton.setDisable(true);
            loginButton.setText("登录中...");
            usernameField.setText("正在启动浏览器...");
            
            LoginService loginService = new LoginService();
            if (chromiumPath != null && !chromiumPath.trim().isEmpty()) {
                loginService.setChromiumPath(chromiumPath);
                System.out.println("Debug: 使用自定义Chromium路径: " + chromiumPath);
            } else {
                System.out.println("Debug: 使用默认Chromium");
            }
            
            // This will block the UI thread. For a better user experience,
            // this should be run in a separate thread.
            Thread loginThread = new Thread(() -> {
                try {
                    System.out.println("Debug: 开始登录流程");
                    loginService.loginAndSaveCookies();
                    System.out.println("Debug: 登录完成，更新UI");
                    
                    // UI updates must be done on the JavaFX Application Thread
                    javafx.application.Platform.runLater(() -> {
                        loginButton.setDisable(false);
                        loginButton.setText("登录");
                        loadCookiesAndUpdateUI();
                    });
                } catch (Exception ex) {
                    System.err.println("Debug: 登录异常: " + ex.getMessage());
                    ex.printStackTrace();
                    
                    javafx.application.Platform.runLater(() -> {
                        loginButton.setDisable(false);
                        loginButton.setText("登录");
                        usernameField.setText("登录失败: " + ex.getMessage());
                    });
                }
            });
            loginThread.setDaemon(true);
            loginThread.start();
            
            System.out.println("Debug: 登录线程已启动");
        });

        importButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("选择Cookie文件");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json"));
            File selectedFile = fileChooser.showOpenDialog(layout.getScene().getWindow());
            if (selectedFile != null) {
                // 在后台线程中处理文件IO和网络请求
                new Thread(() -> {
                    try {
                        String fileContent = new String(Files.readAllBytes(selectedFile.toPath())).trim();
                        org.json.JSONArray cookies = null;
                        
                        // 兼容多种cookie格式
                        Object parsed = new org.json.JSONTokener(fileContent).nextValue();
                        if (parsed instanceof org.json.JSONArray) {
                            cookies = (org.json.JSONArray) parsed;
                        } else if (parsed instanceof org.json.JSONObject) {
                            org.json.JSONObject obj = (org.json.JSONObject) parsed;
                            if (obj.has("_default")) {
                                org.json.JSONObject def = obj.getJSONObject("_default");
                                for (String k : def.keySet()) {
                                    org.json.JSONObject inner = def.getJSONObject(k);
                                    if (inner.has("key") && "cookie".equals(inner.getString("key")) && inner.has("value")) {
                                        Object val = inner.get("value");
                                        if (val instanceof org.json.JSONArray) {
                                            cookies = (org.json.JSONArray) val;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (cookies == null && obj.has("cookies")) {
                                Object val = obj.get("cookies");
                                if (val instanceof org.json.JSONArray) {
                                    cookies = (org.json.JSONArray) val;
                                }
                            }
                        }

                        if (cookies == null) {
                            javafx.application.Platform.runLater(() -> usernameField.setText("配置文件中未找到cookies字段"));
                            return;
                        }
                        
                        CookieManager cookieManager = new CookieManager(cookies);
                        biliRequest = new BiliRequest(cookieManager.getRawCookies(), "none");
                        final String username = biliRequest.getUserNickname();
                        final String absolutePath = selectedFile.getAbsolutePath();
                        
                        // 将cookie保存到cookies.json
                        try (FileWriter file = new FileWriter("cookies.json")) {
                            file.write(cookies.toString(4));
                        }
                        
                        // 在UI线程上更新界面
                        javafx.application.Platform.runLater(() -> {
                            usernameField.setText(username);
                            cookieFileField.setText(absolutePath);
                        });
                        
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> usernameField.setText("导入失败: " + ex.getMessage()));
                        ex.printStackTrace();
                    }
                }).start();
            }
        });

        loginSection.getChildren().addAll(loginHelpText, loginGrid, chromiumPathSection, loginButtons);

        // 3. Phone number section
        TitledPane phonePane = new TitledPane("填写你的当前账号所绑定的手机号[可选]", new TextField());
        phonePane.setExpanded(false);
        phoneAccordion = new Accordion(phonePane);


        // 4. Ticket info section
        VBox ticketSection = new VBox(5);
        ticketSection.setPadding(new Insets(10));
        ticketSection.setStyle("-fx-border-color: #B0E0E6; -fx-border-width: 1px;");

        TextArea ticketInfoArea = new TextArea();
        ticketInfoArea.setEditable(false);
        ticketInfoArea.setVisible(false);

        HBox urlInputBox = new HBox(5);
        TextField ticketUrlField = new TextField();
        ticketUrlField.setPromptText("形如 https://show.bilibili.com/platform/detail.html?id=84096");
        Button getInfoButton = new Button("获取票信息");
        urlInputBox.getChildren().addAll(new Label("想要抢票的网址:"), ticketUrlField, getInfoButton);

        ticketDetailsPane = new VBox(10);
        ticketDetailsPane.setVisible(false);

        ticketChoice = new ChoiceBox<>();
        // TODO: Add DatePicker equivalent for Calendar
        contactChoice = new ChoiceBox<>();
        addressChoice = new ChoiceBox<>();
        idCheckboxes = new VBox(5); // For CheckboxGroup

        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(10);
        detailsGrid.setVgap(5);
        detailsGrid.add(new Label("选票:"), 0, 0);
        detailsGrid.add(ticketChoice, 1, 0);
        detailsGrid.add(new Label("选择日期:"), 0, 1);
        // detailsGrid.add(datePicker, 1, 1);
        detailsGrid.add(new Label("联系人:"), 0, 2);
        detailsGrid.add(contactChoice, 1, 2);
        detailsGrid.add(new Label("地址:"), 0, 3);
        detailsGrid.add(addressChoice, 1, 3);
        detailsGrid.add(new Label("身份证实名认证:"), 0, 4);
        detailsGrid.add(idCheckboxes, 1, 4);

        Button generateConfigButton = new Button("生成配置");
        TextArea configOutputArea = new TextArea();
        configOutputArea.setVisible(false);
        
        generateConfigButton.setOnAction(e -> {
            generateConfig(configOutputArea);
        });

        ticketDetailsPane.getChildren().addAll(detailsGrid, generateConfigButton, configOutputArea);

        ticketSection.getChildren().addAll(ticketInfoArea, urlInputBox, ticketDetailsPane);

        getInfoButton.setOnAction(e -> {
            String url = ticketUrlField.getText();
            if (url != null && !url.isEmpty()) {
                String projectId = extractIdFromUrl(url);
                if (projectId != null) {
                    getTicketInfo(projectId);
                }
            }
        });

        layout.getChildren().addAll(warningLabel, loginSection, phoneAccordion, ticketSection);

        // 过码测试部分
        VBox captchaTestSection = new VBox(10);
        captchaTestSection.setPadding(new Insets(10));
        captchaTestSection.setStyle("-fx-border-color: #DDA0DD; -fx-border-width: 1px;");
        
        Label captchaTestTitle = new Label("过码测试");
        captchaTestTitle.setStyle("-fx-font-weight: bold;");

        HBox testControls = new HBox(10);
        Label testCountLabel = new Label("测试次数:");
        TextField testCountField = new TextField("10");
        testCountField.setPrefWidth(50);
        Button startTestButton = new Button("开始测试");
        testControls.getChildren().addAll(testCountLabel, testCountField, startTestButton);

        TextArea testLogArea = new TextArea();
        testLogArea.setEditable(false);
        testLogArea.setPrefHeight(200);

        captchaTestSection.getChildren().addAll(captchaTestTitle, testControls, testLogArea);
        layout.getChildren().add(captchaTestSection);

        startTestButton.setOnAction(e -> {
            int n;
            try {
                n = Integer.parseInt(testCountField.getText());
                if (n <= 0) {
                    testLogArea.setText("测试次数必须为正整数。");
                    return;
                }
            } catch (NumberFormatException ex) {
                testLogArea.setText("请输入有效的测试次数。");
                return;
            }

            startTestButton.setDisable(true);
            testLogArea.clear();
            testLogArea.appendText("测试开始...\n");

            new Thread(() -> {
                System.out.println("Starting validation test for " + n + " iterations...");
                javafx.application.Platform.runLater(() -> testLogArea.appendText("Starting validation test for " + n + " iterations...\n"));
                
                int successCount = 0;
                double totalTime = 0;

                for (int i = 0; i < n; i++) {
                    final int currentTest = i + 1;
                    System.out.println("--- Test " + currentTest + " ---");
                    javafx.application.Platform.runLater(() -> testLogArea.appendText("--- Test " + currentTest + " ---\n"));
                    
                    try {
                        long startTime = System.currentTimeMillis();

                        // 1. Register and get gt/challenge
                        String gtChallengeJson = com.example.geetest.TripleValidator.registerTest();
                        if (gtChallengeJson == null || gtChallengeJson.isEmpty()) {
                            String failMsg = "Failed to get gt and challenge";
                            System.out.println(failMsg);
                            javafx.application.Platform.runLater(() -> testLogArea.appendText(failMsg + "\n"));
                            continue;
                        }
                        JSONObject jsonObj = new JSONObject(gtChallengeJson);
                        String gt = jsonObj.getString("gt");
                        String challenge = jsonObj.getString("challenge");
                        System.out.println("Successfully registered. gt: " + gt);
                        javafx.application.Platform.runLater(() -> testLogArea.appendText("Successfully registered. gt: " + gt + "\n"));

                        // 2. Validate
                        String validateResult = com.example.geetest.TripleValidator.simpleMatchRetry(gt, challenge);
                        
                        long endTime = System.currentTimeMillis();
                        double elapsedTime = (endTime - startTime) / 1000.0;
                        totalTime += elapsedTime;

                        if (validateResult != null && !validateResult.isEmpty()) {
                            successCount++;
                            String successMsg = String.format("Test %d: Result = %s, Time = %.4fs", currentTest, validateResult, elapsedTime);
                            System.out.println(successMsg);
                            javafx.application.Platform.runLater(() -> testLogArea.appendText(successMsg + "\n"));
                        } else {
                            String failMsg = String.format("Test %d: FAILED! Result = %s, Time = %.4fs", currentTest, validateResult, elapsedTime);
                            System.err.println(failMsg);
                            javafx.application.Platform.runLater(() -> testLogArea.appendText(failMsg + "\n"));
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        final String errorLog = String.format("Test %d: Exception: %s", currentTest, ex.getMessage());
                        System.err.println(errorLog);
                        javafx.application.Platform.runLater(() -> testLogArea.appendText(errorLog + "\n"));
                    }
                }

                double accuracy = (double) successCount / n * 100;
                double avgTime = totalTime / n;
                
                String summary = String.format("\n✅ Testing complete. Total iterations: %d\n✅ Accuracy: %.2f%%\n✅ Average Time: %.4fs\n", n, accuracy, avgTime);
                System.out.println(summary);
                
                javafx.application.Platform.runLater(() -> {
                    testLogArea.appendText(summary);
                    startTestButton.setDisable(false);
                });
            }).start();
        });

        loadCookiesAndUpdateUI();

        // 自动监听内容变化，动态调整窗口大小
        layout.heightProperty().addListener((obs, oldVal, newVal) -> {
            javafx.scene.Scene scene = layout.getScene();
            if (scene != null && scene.getWindow() instanceof javafx.stage.Stage) {
                javafx.stage.Stage stage = (javafx.stage.Stage) scene.getWindow();
                // 仅在窗口可调整时调用
                if (stage.isResizable()) {
                    stage.sizeToScene();
                }
            }
        });

        return layout;
    }

    private static void loadCookiesAndUpdateUI() {
        File cookieFile = new File("cookies.json");
        if (cookieFile.exists()) {
            // 在后台线程中处理文件IO和网络请求
            new Thread(() -> {
                try {
                    String content = new String(Files.readAllBytes(Paths.get(cookieFile.getAbsolutePath()))).trim();
                    if (content.isEmpty()) {
                        return;
                    }
                    org.json.JSONArray cookies = null;
                    
                    // 兼容多种cookie格式
                    Object parsed = new org.json.JSONTokener(content).nextValue();
                    if (parsed instanceof org.json.JSONArray) {
                        cookies = (org.json.JSONArray) parsed;
                    } else if (parsed instanceof org.json.JSONObject) {
                        org.json.JSONObject obj = (org.json.JSONObject) parsed;
                        if (obj.has("_default")) {
                            org.json.JSONObject def = obj.getJSONObject("_default");
                            for (String k : def.keySet()) {
                                org.json.JSONObject inner = def.getJSONObject(k);
                                if (inner.has("key") && "cookie".equals(inner.getString("key")) && inner.has("value")) {
                                    Object val = inner.get("value");
                                    if (val instanceof org.json.JSONArray) {
                                        cookies = (org.json.JSONArray) val;
                                        break;
                                    }
                                }
                            }
                        }
                        if (cookies == null && obj.has("cookies")) {
                            Object val = obj.get("cookies");
                            if (val instanceof org.json.JSONArray) {
                                cookies = (org.json.JSONArray) val;
                            }
                        }
                    }

                    if (cookies == null) {
                        javafx.application.Platform.runLater(() -> usernameField.setText("配置文件中未找到cookies字段"));
                        return;
                    }
                    
                    CookieManager cookieManager = new CookieManager(cookies);
                    biliRequest = new BiliRequest(cookieManager.getRawCookies(), "none");
                    final String username = biliRequest.getUserNickname();
                    final String absolutePath = cookieFile.getAbsolutePath();
                    
                    // 在UI线程上更新界面
                    javafx.application.Platform.runLater(() -> {
                        usernameField.setText(username);
                        cookieFileField.setText(absolutePath);
                    });
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    javafx.application.Platform.runLater(() -> usernameField.setText("加载Cookie失败: " + e.getMessage()));
                }
            }).start();
        }
    }

    private static String extractIdFromUrl(String url) {
        Pattern pattern = Pattern.compile("id=(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static void getTicketInfo(String projectId) {
        if (biliRequest == null) {
            // Handle not logged in
            return;
        }
        try {
            Response response = biliRequest.get("https://show.bilibili.com/api/ticket/project/getV2?version=134&id=" + projectId + "&project_id=" + projectId);
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                // 兼容不同的错误码字段名：errno 或 code
                int errorCode = json.has("errno") ? json.getInt("errno") : 
                               (json.has("code") ? json.getInt("code") : -1);
                if (errorCode == 0) {
                    JSONObject data = json.getJSONObject("data");
                    projectName = data.getString("name");

                    // Clear previous data
                    ticketValue.clear();
                    ticketChoice.getItems().clear();

                    JSONArray screenList = data.getJSONArray("screen_list");
                    for (int i = 0; i < screenList.length(); i++) {
                        JSONObject screen = screenList.getJSONObject(i);
                        JSONArray ticketList = screen.getJSONArray("ticket_list");
                        for (int j = 0; j < ticketList.length(); j++) {
                            JSONObject ticket = ticketList.getJSONObject(j);
                            ticket.put("screen_name", screen.getString("name"));
                            ticket.put("screen_id", screen.getInt("id"));
                            ticket.put("project_id", data.getInt("id"));
                            ticketValue.add(ticket);

                            // 仿照Python端，拼接票状态和起售时间
                            String ticket_can_buy = "";
                            if (ticket.has("sale_flag_number")) {
                                int flag = ticket.getInt("sale_flag_number");
                                switch (flag) {
                                    case 1: ticket_can_buy = "不可售"; break;
                                    case 2: ticket_can_buy = "预售"; break;
                                    case 3: ticket_can_buy = "停售"; break;
                                    case 4: ticket_can_buy = "售罄"; break;
                                    case 5: ticket_can_buy = "不可用"; break;
                                    case 6: ticket_can_buy = "库存紧张"; break;
                                    case 8: ticket_can_buy = "暂时售罄"; break;
                                    case 9: ticket_can_buy = "不在白名单"; break;
                                    case 101: ticket_can_buy = "未开始"; break;
                                    case 102: ticket_can_buy = "已结束"; break;
                                    case 103: ticket_can_buy = "未完成"; break;
                                    case 105: ticket_can_buy = "下架"; break;
                                    case 106: ticket_can_buy = "已取消"; break;
                                    default: ticket_can_buy = ""; break;
                                }
                            }
                            String saleStart = ticket.has("sale_start") ? ticket.get("sale_start").toString() : "";
                            String ticketStr = screen.getString("name") + " - " + ticket.getString("desc") + " - ￥" + (ticket.getDouble("price") / 100.0)
                                + (ticket_can_buy.isEmpty() ? "" : ("- " + ticket_can_buy))
                                + (saleStart.isEmpty() ? "" : (" - 【起售时间：" + saleStart + "】"));
                            ticketChoice.getItems().add(ticketStr);
                        }
                    }

                    // Fetch buyer and address info
                    getBuyerInfo(projectId);
                    getAddrInfo();

                    ticketDetailsPane.setVisible(true);
                    // 自动调整窗口大小以适应新增内容
                    javafx.application.Platform.runLater(() -> {
                        try {
                            javafx.stage.Stage stage = (javafx.stage.Stage) ticketDetailsPane.getScene().getWindow();
                            stage.sizeToScene();
                        } catch (Exception ex) {
                            // 忽略异常，防止无窗口时崩溃
                        }
                    });
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void getBuyerInfo(String projectId) throws IOException {
        Response response = biliRequest.get("https://show.bilibili.com/api/ticket/buyer/list?is_default&projectId=" + projectId);
        if (response.isSuccessful()) {
            JSONObject json = new JSONObject(response.body().string());
            // 兼容不同的错误码字段名：errno 或 code
            int errorCode = json.has("errno") ? json.getInt("errno") : 
                           (json.has("code") ? json.getInt("code") : -1);
            if (errorCode == 0) {
                buyerValue.clear();
                contactChoice.getItems().clear();
                idCheckboxes.getChildren().clear();
                JSONArray list = json.getJSONObject("data").getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject buyer = list.getJSONObject(i);
                    buyerValue.add(buyer);
                    String buyerStr = buyer.getString("name") + "-" + buyer.getString("personal_id");
                    contactChoice.getItems().add(buyerStr);
                    idCheckboxes.getChildren().add(new javafx.scene.control.CheckBox(buyerStr));
                }
            }
        }
    }

    private static void getAddrInfo() throws IOException {
        Response response = biliRequest.get("https://show.bilibili.com/api/ticket/addr/list");
        if (response.isSuccessful()) {
            JSONObject json = new JSONObject(response.body().string());
            // 兼容不同的错误码字段名：errno 或 code
            int errorCode = json.has("errno") ? json.getInt("errno") : 
                           (json.has("code") ? json.getInt("code") : -1);
            if (errorCode == 0) {
                addrValue.clear();
                addressChoice.getItems().clear();
                JSONArray list = json.getJSONObject("data").getJSONArray("addr_list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject addr = list.getJSONObject(i);
                    addrValue.add(addr);
                    addressChoice.getItems().add(addr.getString("addr") + "-" + addr.getString("name") + "-" + addr.getString("phone"));
                }
            }
        }
    }
    
    private static void generateConfig(TextArea configOutputArea) {
        int ticketIndex = ticketChoice.getSelectionModel().getSelectedIndex();
        int contactIndex = contactChoice.getSelectionModel().getSelectedIndex();
        int addressIndex = addressChoice.getSelectionModel().getSelectedIndex();

        List<Integer> selectedBuyerIndices = new ArrayList<>();
        for (int i = 0; i < idCheckboxes.getChildren().size(); i++) {
            if (((javafx.scene.control.CheckBox) idCheckboxes.getChildren().get(i)).isSelected()) {
                selectedBuyerIndices.add(i);
            }
        }

        if (ticketIndex < 0 || contactIndex < 0 || addressIndex < 0 || selectedBuyerIndices.isEmpty()) {
            // Handle error, nothing selected
            return;
        }

        JSONObject ticket = ticketValue.get(ticketIndex);
        JSONObject contact = buyerValue.get(contactIndex);
        JSONObject addr = addrValue.get(addressIndex);
        
        JSONArray selectedBuyers = new JSONArray();
        String detail = usernameField.getText() + "-" + projectName + "-" + ticketChoice.getValue();
        for (int index : selectedBuyerIndices) {
            JSONObject buyer = buyerValue.get(index);
            selectedBuyers.put(buyer);
            detail += "-" + buyer.getString("name");
        }

        JSONObject config = new JSONObject();
        config.put("username", usernameField.getText());
        config.put("detail", detail);
        config.put("count", selectedBuyers.length());
        config.put("screen_id", ticket.getInt("screen_id"));
        config.put("project_id", ticket.getInt("project_id"));
        config.put("sku_id", ticket.getInt("id"));
        config.put("order_type", 1);
        config.put("pay_money", ticket.getInt("price") * selectedBuyers.length());
        config.put("buyer_info", selectedBuyers);
        config.put("buyer", contact.getString("name"));
        config.put("tel", contact.getString("tel"));

        // 从phonePane获取电话号码
        TitledPane phonePane = (TitledPane) phoneAccordion.getPanes().get(0);
        String phone = ((TextField) phonePane.getContent()).getText();
        config.put("phone", phone != null ? phone : "");

        JSONObject deliverInfo = new JSONObject();
        deliverInfo.put("name", addr.getString("name"));
        deliverInfo.put("tel", addr.getString("phone"));
        deliverInfo.put("addr_id", addr.getLong("id"));
        deliverInfo.put("addr", addr.getString("prov") + addr.getString("city") + addr.getString("area") + addr.getString("addr"));
        config.put("deliver_info", deliverInfo);

        // 将cookies Map转换为JSONArray
        config.put("cookies", biliRequest.getCookiesAsJson());

        String configString = config.toString(4);
        configOutputArea.setText(configString);
        configOutputArea.setVisible(true);

        try {
            // 创建configs目录
            File configsDir = new File("configs");
            if (!configsDir.exists()) {
                configsDir.mkdirs();
            }
            
            String filename = detail.replaceAll("[/:*?\"<>|]", "") + ".json";
            File configFile = new File(configsDir, filename);
            
            try (FileWriter file = new FileWriter(configFile)) {
                file.write(configString);
            }
            
            // 执行系统调用打开目录
            try {
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    // Windows
                    Runtime.getRuntime().exec("explorer " + configsDir.getAbsolutePath());
                } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                    // macOS
                    Runtime.getRuntime().exec("open " + configsDir.getAbsolutePath());
                } else {
                    // Linux
                    Runtime.getRuntime().exec("xdg-open " + configsDir.getAbsolutePath());
                }
            } catch (IOException ex) {
                System.err.println("无法打开目录: " + ex.getMessage());
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
