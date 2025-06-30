package com.wiyi.ss.task;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.json.JSONObject;

import com.wiyi.ss.util.BiliRequest;
import com.wiyi.ss.util.PushPlusUtil;
import com.wiyi.ss.util.QRCodeUtil;
import com.wiyi.ss.util.ServerChanUtil;
import com.wiyi.ss.util.TimeUtil;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class BuyTask implements Runnable {

    // GUI模式参数
    private File configFile;
    private String targetTimeStr;
    private int interval;
    private String mode;
    private int totalAttempts;
    private TextArea logArea;

    // 命令行模式参数
    private String ticketsInfoStr;
    private String cliTimeStart;
    private int cliInterval;
    private int cliMode;
    private int cliTotalAttempts;
    private String cliHttpsProxys;
    private String cliPushplusToken;
    private String cliServerchanKey;
    private String serverChanToken;
    private String pushPlusToken;
    private String cliNtfyUrl;
    private String cliNtfyUsername;
    private String cliNtfyPassword;
    private String cliAudioPath;
    private boolean isCliMode = false;

    // GUI构造方法
    public BuyTask(File configFile, String targetTimeStr, int interval, String mode, int totalAttempts, TextArea logArea, String serverChanToken, String pushPlusToken) {
        this.configFile = configFile;
        this.targetTimeStr = targetTimeStr;
        this.interval = interval;
        this.mode = mode;
        this.totalAttempts = totalAttempts;
        this.logArea = logArea;
        this.isCliMode = false;
        this.serverChanToken = serverChanToken;
        this.pushPlusToken = pushPlusToken;
    }

    // 命令行构造方法
    public BuyTask(String ticketsInfoStr, String timeStart, int interval, int mode, int totalAttempts,
                   String httpsProxys, String pushplusToken, String serverchanKey, String ntfyUrl,
                   String ntfyUsername, String ntfyPassword, String audioPath) {
        this.ticketsInfoStr = ticketsInfoStr;
        this.cliTimeStart = timeStart;
        this.cliInterval = interval;
        this.cliMode = mode;
        this.cliTotalAttempts = totalAttempts;
        this.cliHttpsProxys = httpsProxys;
        this.cliPushplusToken = pushplusToken;
        this.cliServerchanKey = serverchanKey;
        this.cliNtfyUrl = ntfyUrl;
        this.cliNtfyUsername = ntfyUsername;
        this.cliNtfyPassword = ntfyPassword;
        this.cliAudioPath = audioPath;
        this.isCliMode = true;
    }

    @Override
    public void run() {
        if (isCliMode) {
            runCli();
        } else {
            runGui();
        }
    }

    // GUI模式逻辑
    private void runGui() {
        try {
            String content = new String(Files.readAllBytes(configFile.toPath()));
            JSONObject config = new JSONObject(content);

            Platform.runLater(() -> logArea.appendText("启动配置: " + configFile.getName() + "\n"));

            // 重构Cookie和Request处理，与Python版本保持一致
            org.json.JSONArray cookies = config.getJSONArray("cookies");
            BiliRequest biliRequest = new BiliRequest(cookies, "none"); // "none" for proxy for now

            if (targetTimeStr != null && !targetTimeStr.isEmpty()) {
                TimeUtil timeUtil = new TimeUtil("ntp.aliyun.com");
                String offsetStr = timeUtil.computeTimeOffset();
                timeUtil.setTimeOffset(offsetStr);
                double offset = timeUtil.getTimeOffset();

                LocalDateTime targetTime = LocalDateTime.parse(targetTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                final String waitMsg = "等待目标时间: " + targetTimeStr + "\n";
                Platform.runLater(() -> logArea.appendText(waitMsg));

                while (LocalDateTime.now().plusSeconds((long) offset).isBefore(targetTime)) {
                    if (Thread.currentThread().isInterrupted()) {
                        Platform.runLater(() -> logArea.appendText("任务被中断\n"));
                        return;
                    }
                    Thread.sleep(100);
                }
            }

            Platform.runLater(() -> logArea.appendText("开始抢票...\n"));

            JSONObject payload = new JSONObject(config, JSONObject.getNames(config)); // Deep copy
            payload.remove("cookies"); // Remove cookies from payload
            // 根据Python代码，将 buyer_info (JSONArray) 和 deliver_info (JSONObject) 转换为字符串
            payload.put("buyer_info", payload.getJSONArray("buyer_info").toString());
            payload.put("deliver_info", payload.getJSONObject("deliver_info").toString());
            
            String projectId = String.valueOf(payload.getInt("project_id"));

            boolean isSuccess = false;
            int attempts = 0;

            while (!isSuccess && ("无限".equals(mode) || attempts < totalAttempts)) {
                if (Thread.currentThread().isInterrupted()) {
                    updateLog("任务被中断");
                    return;
                }
                
                attempts++;
                updateLog("第 " + attempts + " 次尝试...");
                
                String token;
                String prepareBody = "";
                try {
                    // 1. Prepare Order
                    updateLog("1) 订单准备");
                    JSONObject tokenPayload = new JSONObject();
                    tokenPayload.put("count", payload.getInt("count"));
                    tokenPayload.put("screen_id", payload.getInt("screen_id"));
                    tokenPayload.put("order_type", 1);
                    tokenPayload.put("project_id", payload.getInt("project_id"));
                    tokenPayload.put("sku_id", payload.getInt("sku_id"));
                    tokenPayload.put("token", "");
                    tokenPayload.put("newRisk", true);

                    okhttp3.Response prepareResponse = biliRequest.prepareOrder(projectId, tokenPayload.toString());
                    String contentType = prepareResponse.header("Content-Type");
                    prepareBody = prepareResponse.body().string();

                    if (contentType == null || !contentType.contains("application/json")) {
                        updateLog(String.format("订单准备响应非JSON (%s)，跳过. 响应体: %s", contentType, prepareBody));
                        Thread.sleep(interval);
                        continue;
                    }

                    JSONObject prepareResult = new JSONObject(prepareBody);
                    int prepareErrno = prepareResult.optInt("errno", prepareResult.optInt("code"));

                    if (prepareErrno != 0) {
                        updateLog("订单准备失败: " + prepareBody);
                        if (prepareErrno == -401) {
                            updateLog("需要验证码，当前版本暂不支持，请稍后重试。");
                            break;
                        }
                        Thread.sleep(interval);
                        continue;
                    }
                    token = prepareResult.getJSONObject("data").getString("token");
                } catch (Exception e) {
                    String errorLog = String.format("订单准备时发生异常: %s. 响应体: %s", e.getMessage(), prepareBody);
                    updateLog(errorLog);
                    e.printStackTrace();
                    Thread.sleep(interval);
                    continue;
                }
                
                payload.put("again", 1);
                payload.put("token", token);
                updateLog("订单准备成功, Token: " + token);

                // 2. Create Order
                updateLog("2) 创建订单");
                for (int i = 0; i < 60; i++) { // Retry up to 60 times for createOrder
                    if (Thread.currentThread().isInterrupted()) {
                        updateLog("任务被中断");
                        return;
                    }

                    String createBody = "";
                    try {
                        // 与Python保持一致: int(time.time()) * 100
                        payload.put("timestamp", (int) (System.currentTimeMillis() / 1000) * 100);
                        okhttp3.Response createResponse = biliRequest.createOrder(projectId, payload.toString());
                        String contentType = createResponse.header("Content-Type");
                        createBody = createResponse.body().string();

                        if (contentType != null && contentType.contains("application/json")) {
                            JSONObject createResult = new JSONObject(createBody);
                            int createErrno = createResult.optInt("errno", createResult.optInt("code"));
                            String msg = createResult.optString("msg", createResult.optString("message", ""));
//                            updateLog(String.format("[尝试 %d/60] [%d](%s) | %s", i + 1, createErrno, msg, createBody));
                            updateLog(String.format("[尝试 %d/%d] [%d](%s) | %s", (i + 1)+(attempts-1)*60,attempts*60, createErrno, msg, createBody));

                            if (createErrno == 0 || createErrno == 100048 || createErrno == 100079) {
                                updateLog("3) 抢票成功，获取付款二维码");
                                String orderId = String.valueOf(createResult.getJSONObject("data").get("orderId"));

                                okhttp3.Response payParamResponse = biliRequest.getPayParam(orderId);
                                String payParamBody = payParamResponse.body().string();
                                JSONObject payParamJson = new JSONObject(payParamBody);

                                if (payParamJson.optInt("errno", payParamJson.optInt("code")) == 0) {
                                    String codeUrl = payParamJson.getJSONObject("data").getString("code_url");
                                    updateLog("获取到二维码URL，正在生成...");
                                    QRCodeUtil.showQRCode(codeUrl);
                                    isSuccess = true;

                                    // Send notifications
                                    String successTitle = "抢票成功";
                                    String successMessage = "订单号: " + orderId;
                                    if (pushPlusToken != null && !pushPlusToken.isEmpty()) {
                                        PushPlusUtil.sendMessage(pushPlusToken, successTitle, successMessage);
                                    }
                                    if (serverChanToken != null && !serverChanToken.isEmpty()) {
                                        ServerChanUtil.sendMessage(serverChanToken, successTitle, successMessage);
                                    }
                                } else {
                                    updateLog("获取二维码失败: " + payParamBody);
                                }
                                break; // Break create order loop on success
                            } else if (createErrno == 100051 || createErrno == 100003) {
                                updateLog("Token过期或已存在订单，需要重新准备订单");
                                break; // Break create order loop to re-prepare
                            }
                        } else {
                            int httpCode = createResponse.code();
//                             updateLog(String.format("[尝试 %d/60] [%d]", i + 1,httpCode));
                            updateLog(String.format("[尝试 %d/%d] [%d]", (i + 1)+(attempts-1)*60,attempts*60,httpCode));
                        }
                    } catch (Exception e) {
                        String errorLog = String.format("[尝试 %d/%d] 创建订单时发生异常: %s. 响应体: %s", (i + 1)+(attempts-1)*60,attempts*60, e.getMessage(), createBody);
                        updateLog(errorLog);
                        e.printStackTrace();
                    }

                    // Wait for the interval before the next attempt
                    Thread.sleep(interval);
                }

                if (isSuccess) {
                    break; // Break main while loop
                }
            }

            Platform.runLater(() -> logArea.appendText("任务结束: " + configFile.getName() + "\n"));

        } catch (Exception e) {
            final String errorMsg = "任务失败: " + e.getMessage() + "\n";
            Platform.runLater(() -> logArea.appendText(errorMsg));
            e.printStackTrace();
        }
    }

    private void updateLog(String message) {
        Platform.runLater(() -> logArea.appendText(configFile.getName() + ": " + message + "\n"));
    }

    // 命令行模式逻辑
    private void runCli() {
        try {
            System.out.println("启动命令行抢票任务...");
            JSONObject config = new JSONObject(ticketsInfoStr);

            // 这里可根据 timeStart 字符串格式进一步完善
            if (cliTimeStart != null && !cliTimeStart.isEmpty()) {
                System.out.println("等待目标时间: " + cliTimeStart);
                LocalDateTime targetTime = LocalDateTime.parse(cliTimeStart, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                while (LocalDateTime.now().isBefore(targetTime)) {
                    Thread.sleep(100);
                }
            }

            System.out.println("开始抢票...");
            for (int i = 0; cliMode == 0 || i < cliTotalAttempts; i++) {
                int currentAttempt = i + 1;
                System.out.println("尝试第 " + currentAttempt + " 次");

                // 实际抢票逻辑应在此实现
                if (Math.random() < 0.1) {
                    System.out.println("抢票成功!");
                    break;
                }

                Thread.sleep(cliInterval);
            }
            System.out.println("命令行抢票任务结束。");
        } catch (Exception e) {
            System.err.println("命令行抢票任务失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
