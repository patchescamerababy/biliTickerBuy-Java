package com.example.task;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.example.util.BiliRequest;
import com.example.util.PushPlusUtil;
import com.example.util.QRCodeUtil;
import com.example.util.ServerChanUtil;
import com.example.util.TimeUtil;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class BuyTask implements Runnable {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BuyTask.class);

    // 静态变量：保存最近一次加载的cookie信息（字符串或JSONArray）
    private static String lastLoadedCookies = "";

    public static void setLastLoadedCookies(String cookies) {
        lastLoadedCookies = cookies;
    }

    public static String getLastLoadedCookies() {
        return lastLoadedCookies;
    }

    // GUI模式参数
    private File configFile;
    private String targetTimeStr;
    private int interval;
    private String mode;
    private int totalAttempts;
    private TextArea logArea;

    // 定时等待工具
    private ScheduledExecutorService retryScheduler;

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
    private String proxyString;
    private String cliNtfyUrl;
    private String cliNtfyUsername;
    private String cliNtfyPassword;
    private String cliAudioPath;
    private boolean isCliMode = false;
    private volatile boolean running = true;
    private BiliRequest biliRequest;

    // GUI构造方法
    public BuyTask(File configFile, String targetTimeStr, int interval, String mode, int totalAttempts, TextArea logArea, String serverChanToken, String pushPlusToken, String proxyString) {
        this.configFile = configFile;
        this.targetTimeStr = targetTimeStr;
        this.interval = interval;
        this.mode = mode;
        this.totalAttempts = totalAttempts;
        this.logArea = logArea;
        this.isCliMode = false;
        this.serverChanToken = serverChanToken;
        this.pushPlusToken = pushPlusToken;
        this.proxyString = proxyString;
        this.retryScheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
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

    public void stop() {
        this.running = false;
        if (this.biliRequest != null) {
            this.biliRequest.cancelCurrentRequest();
        }
    }

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

            updateLog("启动配置: " + configFile.getName());

            // 重构Cookie和Request处理，与Python版本保持一致
            org.json.JSONArray cookies = config.getJSONArray("cookies");
            this.biliRequest = new BiliRequest(cookies, this.proxyString);

            if (targetTimeStr != null && !targetTimeStr.isEmpty()) {
                TimeUtil timeUtil = new TimeUtil("ntp.aliyun.com");
                String offsetStr = timeUtil.computeTimeOffset();
                timeUtil.setTimeOffset(offsetStr);
                double offset = timeUtil.getTimeOffset();

                LocalDateTime targetTime = LocalDateTime.parse(targetTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                updateLog("等待目标时间: " + targetTimeStr);

                LocalDateTime currentTime = LocalDateTime.now().plusSeconds((long) offset);
                if (currentTime.isBefore(targetTime)) {
                    long delayMillis = java.time.Duration.between(currentTime, targetTime).toMillis();
                    if (delayMillis > 0) {
                        CountDownLatch latch = new CountDownLatch(1);
                        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

                        scheduler.schedule(() -> latch.countDown(), delayMillis, TimeUnit.MILLISECONDS);

                        try {
                            boolean completed = latch.await(delayMillis + 1000, TimeUnit.MILLISECONDS);
                            if (!completed) {
                                logger.error("等待目标时间超时，可能定时任务未被调度或线程池耗尽。");
                                scheduler.shutdown();
                                return;
                            }
                        } catch (InterruptedException ex) {
                            Platform.runLater(() -> logArea.appendText("任务被中断\n"));
                            scheduler.shutdown();
                            return;
                        }
                        scheduler.shutdown();
                    }
                }
            }

            updateLog("开始抢票...");

            JSONObject payload = new JSONObject(config, JSONObject.getNames(config)); // Deep copy
            payload.remove("cookies"); // Remove cookies from payload
            // 根据Python代码，将 buyer_info (JSONArray) 和 deliver_info (JSONObject) 转换为字符串
            payload.put("buyer_info", payload.getJSONArray("buyer_info").toString());
            payload.put("deliver_info", payload.getJSONObject("deliver_info").toString());

            String projectId = String.valueOf(payload.getInt("project_id"));

            boolean isSuccess = false;
            int attempts = 0;

            while (running && !isSuccess && ("无限".equals(mode) || attempts < totalAttempts)) {
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

                    try (okhttp3.Response prepareResponse = biliRequest.prepareOrder(projectId, tokenPayload.toString())) {
                        // 检查412错误
                        if (prepareResponse.code() == 412) {
                            if (handle412Error(biliRequest, "订单准备")) {
                                return; // 被中断，退出
                            }
                            continue; // 继续重试
                        }

                        String contentType = prepareResponse.header("Content-Type");
                        prepareBody = safeReadResponseBody(prepareResponse, "订单准备");

                        if (contentType == null || !contentType.contains("application/json")) {
                            updateLog(String.format("订单准备响应非JSON (%s)，跳过. 响应体: %s", contentType, prepareBody));
                            if (waitForInterval()) return;
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
                            if (waitForInterval()) return;
                            continue;
                        }
                        token = prepareResult.getJSONObject("data").getString("token");
                    }
                } catch (Exception e) {
                    String errorLog = String.format("订单准备时发生异常: %s. 响应体: %s", e.getMessage(), prepareBody);
                    updateLog(errorLog);
                    logger.error("订单准备异常", e);
                    if (waitForInterval()) return;
                    continue;
                }

                payload.put("again", 1);
                payload.put("token", token);
                updateLog("订单准备成功, Token: " + token);

                // 2. Create Order
                updateLog("2) 创建订单");
                for (int i = 0; i < 60; i++) { // Retry up to 60 times for createOrder
                    if (!running || Thread.currentThread().isInterrupted()) {
                        updateLog("任务被中断");
                        return;
                    }

                    String createBody = "";
                    try {
                        // 与Python保持一致: int(time.time()) * 100
                        payload.put("timestamp", (int) (System.currentTimeMillis() / 1000) * 100);
                        try (okhttp3.Response createResponse = biliRequest.createOrder(projectId, payload.toString())) {
                            // 检查412错误
                            if (createResponse.code() == 412) {
                                if (handle412Error(biliRequest, "创建订单")) {
                                    return; // 被中断，退出
                                }
                                continue; // 继续重试
                            }

                            String contentType = createResponse.header("Content-Type");
                            createBody = safeReadResponseBody(createResponse, "创建订单");

                            if (contentType != null && contentType.contains("application/json")) {
                                JSONObject createResult = new JSONObject(createBody);
                                int createErrno = createResult.optInt("errno", createResult.optInt("code"));
                                String msg = createResult.optString("msg", createResult.optString("message", ""));
                                //                            updateLog(String.format("[尝试 %d/60] [%d](%s) | %s", i + 1, createErrno, msg, createBody));
                                updateLog(String.format("[尝试 %d/%d] [%d](%s) | %s", (i + 1) + (attempts - 1) * 60, attempts * 60, createErrno, msg, createBody));

                                if (createErrno == 0 || createErrno == 100048 || createErrno == 100079) {
                                    updateLog("3) 抢票成功，获取付款二维码");
                                    String orderId = String.valueOf(createResult.getJSONObject("data").get("orderId"));

                                    try (okhttp3.Response payParamResponse = biliRequest.getPayParam(orderId)) {
                                        // 检查获取支付参数的412错误
                                        if (payParamResponse.code() == 412) {
                                            if (handle412Error(biliRequest, "获取支付参数")) {
                                                return; // 被中断，退出
                                            }
                                            continue; // 继续重试
                                        }

                                        String payParamBody = safeReadResponseBody(payParamResponse, "获取支付参数");
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
                                    }
                                    break; // Break create order loop on success
                                } else if (createErrno == 100051 || createErrno == 100003) {
                                    updateLog("Token过期或已存在订单，需要重新准备订单");
                                    break; // Break create order loop to re-prepare
                                }
                            } else {
                                int httpCode = createResponse.code();
                                updateLog(String.format("[尝试 %d/%d] [%d]", (i + 1) + (attempts - 1) * 60, attempts * 60, httpCode));
                            }
                        }
                    } catch (Exception e) {
                        String errorLog = String.format("[尝试 %d/%d] 创建订单时发生异常: %s. 响应体: %s", (i + 1) + (attempts - 1) * 60, attempts * 60, e.getMessage(), createBody);
                        updateLog(errorLog);
                        logger.error("创建订单异常", e);
                    }

                    // 只在非最后一次尝试时等待间隔，避免不必要的等待
                    if (i < 59) { // 0-58 需要等待，第59次（最后一次）不等待
                        if (waitForInterval()) return;
                    }
                }

                if (isSuccess) {
                    break; // Break main while loop
                }
            }

            updateLog("任务结束: " + configFile.getName());

        } catch (Exception e) {
            updateLog("任务失败: " + e.getMessage());
            logger.error("任务失败", e);
        } finally {
            if (retryScheduler != null && !retryScheduler.isShutdown()) {
                retryScheduler.shutdown();
            }
        }
    }

    private void updateLog(String message) {
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        Platform.runLater(() -> logArea.appendText("[" + timestamp + "] " + configFile.getName() + ": " + message + "\n"));
    }

    // 安全读取HTTP响应体，避免读取卡死和连接泄漏
    private String safeReadResponseBody(okhttp3.Response response, String operationName) {
        try {
            java.util.concurrent.CompletableFuture<String> future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return response.body().string();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            // 最多等待15秒，超时则抛出异常
            return future.get(15, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("{} 响应体读取超时（15秒），可能网络异常或响应过大", operationName);
            throw new RuntimeException(operationName + " 响应体读取超时", e);
        } catch (Exception e) {
            logger.error("{} 响应体读取异常: {}", operationName, e.getMessage());
            throw new RuntimeException(operationName + " 响应体读取异常", e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception ex) {
                    logger.warn("{} 响应体关闭异常: {}", operationName, ex.getMessage());
                }
            }
        }
    }

    // 处理412风控错误
    private boolean handle412Error(BiliRequest biliRequest, String operationName) {
        biliRequest.increment412Count();
        int count = biliRequest.get412Count();

        // 检查是否所有代理都已使用过
        if (biliRequest.allProxiesUsed()) {
            // 所有代理都试过了，显示详细状态并休眠后重置
            updateLog(String.format("⚠️ %s遭遇412风控，已轮换所有代理 (%s)",
                    operationName, biliRequest.getProxyUsageStatus()));
            updateLog("详细代理状态:");
            updateLog(biliRequest.getDetailedProxyStatus());
            updateLog("休眠30秒后重新开始轮换...");

            try {
                Thread.sleep(30000); // 休眠30秒
            } catch (InterruptedException ex) {
                updateLog("休眠被中断");
                Thread.currentThread().interrupt();
                return true; // 被中断，需要退出
            }

            // 重置代理使用状态，开始新一轮
            biliRequest.resetProxyUsageStatus();
            updateLog("开始新一轮代理轮换");
            return false; // 继续尝试
        } else {
            // 切换到下一个代理
            String switchInfo = biliRequest.switchToNextProxy();
            updateLog(String.format("🔄 %s遭遇412风控(第%d次)，切换代理: %s",
                    operationName, count, switchInfo));
            return false; // 继续尝试
        }
    }

    // 简化等待工具方法，避免频繁创建线程池，提升健壮性
    private boolean waitForInterval() {
        if (interval <= 0) return false;
        try {
            Thread.sleep(interval);
            return false;
        } catch (InterruptedException ex) {
            updateLog("任务被中断");
            Thread.currentThread().interrupt();
            return true;
        }
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
                LocalDateTime currentTime = LocalDateTime.now();
                if (currentTime.isBefore(targetTime)) {
                    long delayMillis = java.time.Duration.between(currentTime, targetTime).toMillis();
                    if (delayMillis > 0) {
                        CountDownLatch latch = new CountDownLatch(1);
                        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());//系统所有线程
                        scheduler.schedule(latch::countDown, delayMillis, TimeUnit.MILLISECONDS);

                        try {
                            boolean completed = latch.await(delayMillis + 1000, TimeUnit.MILLISECONDS);
                            if (!completed) {
                                logger.error("命令行等待目标时间超时，可能定时任务未被调度或线程池耗尽。");
                                scheduler.shutdown();
                                return;
                            }
                        } catch (InterruptedException ex) {
                            scheduler.shutdown();
                            return;
                        }
                        scheduler.shutdown();
                    }
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

                // 命令行模式下的等待优化
                if (cliInterval > 0) {
                    CountDownLatch latch = new CountDownLatch(1);
                    ScheduledExecutorService cliScheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

                    cliScheduler.schedule(latch::countDown, cliInterval, TimeUnit.MILLISECONDS);

                    try {
                        boolean completed = latch.await(cliInterval + 1000, TimeUnit.MILLISECONDS);
                        if (!completed) {
                            logger.error("命令行等待间隔超时，可能定时任务未被调度或线程池耗尽。");
                            cliScheduler.shutdown();
                            return;
                        }
                    } catch (InterruptedException ex) {
                        cliScheduler.shutdown();
                        return;
                    }
                    cliScheduler.shutdown();
                }
            }
            System.out.println("命令行抢票任务结束。");
        } catch (Exception e) {
            System.err.println("命令行抢票任务失败: " + e.getMessage());
            logger.error("命令行任务失败", e);
        }
    }
}
