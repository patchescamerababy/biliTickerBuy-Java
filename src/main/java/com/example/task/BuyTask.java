package com.example.task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.example.util.BiliRequest;
import com.example.util.CTokenUtil;
import com.example.util.Notifier;
import com.example.util.QRCodeUtil;
import com.example.util.TimeUtil;
import com.example.util.TokenUtil;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import okhttp3.Response;

public class BuyTask implements Runnable {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BuyTask.class);
    
    // 日志清理相关常量
    private static final int LOG_CLEANUP_THRESHOLD = 1000; // 日志条数达到此值时清理
    private static final String LOG_DIRECTORY = "logs"; // 日志文件存储目录

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
    
    // 日志管理相关变量
    private StringBuilder logBuffer = new StringBuilder();
    private int logEntryCount = 0;
    private String taskCreationTimestamp;
    private String taskUUID;

    // 定时等待工具
    private ScheduledExecutorService retryScheduler;

    private String serverChanToken;
    private String pushPlusToken;
    private String proxyString;

    private volatile boolean running = true;
    private BiliRequest biliRequest;
    private CTokenUtil ctokenGenerator;


    // GUI构造方法（支持所有推送类型）
    public BuyTask(File configFile, String targetTimeStr, int interval, String mode, int totalAttempts, TextArea logArea, 
                   String serverChanToken, String pushPlusToken, String barkToken, String ntfyUrl, 
                   String ntfyUsername, String ntfyPassword, String proxyString) {
        this.configFile = configFile;
        this.targetTimeStr = targetTimeStr;
        this.interval = interval;
        this.mode = mode;
        this.totalAttempts = totalAttempts;
        this.logArea = logArea;
        this.serverChanToken = serverChanToken;
        this.pushPlusToken = pushPlusToken;
        this.proxyString = proxyString;
        this.retryScheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        
        // 初始化任务创建时间戳和UUID
        this.taskCreationTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.taskUUID = UUID.randomUUID().toString().substring(0, 8); // 使用短UUID
    }


    public void stop() {
        this.running = false;
        if (this.biliRequest != null) {
            this.biliRequest.cancelCurrentRequest();
        }
        // 停止时保存所有剩余日志
        saveAllLogsToFile("STOPPED");
    }

    public void run() {
        runGui();
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

                        scheduler.schedule(latch::countDown, delayMillis, TimeUnit.MILLISECONDS);

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
            
            // 按照Python版本逻辑，初始化CToken生成器（当is_hot_project为true时）
            if (payload.optBoolean("is_hot_project", false)) {
                TimeUtil timeUtil = new TimeUtil("ntp.aliyun.com");
                String offsetStr = timeUtil.computeTimeOffset();
                timeUtil.setTimeOffset(offsetStr);
                double timeOffset = timeUtil.getTimeOffset();
                
                long currentTime = System.currentTimeMillis() / 1000;
                long stayTime = 2000 + (long)(Math.random() * 8000); // 2000-10000随机数
                this.ctokenGenerator = new CTokenUtil(currentTime, timeOffset, stayTime);
                updateLog("已初始化CToken生成器，用于热门项目");
            }

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
                    
                    // 使用TokenUtil生成token
                    String generatedToken = TokenUtil.generateToken(
                        payload.getInt("project_id"),
                        payload.getInt("screen_id"),
                        1, // order_type
                        payload.getInt("count"),
                        payload.getInt("sku_id")
                    );
                    tokenPayload.put("token", generatedToken);
                    tokenPayload.put("newRisk", true);
                    
                    // 按照Python版本逻辑，为热门项目添加prepare阶段的CToken
                    if (payload.optBoolean("is_hot_project", false) && ctokenGenerator != null) {
                        String prepareToken = ctokenGenerator.generateCToken("prepare");
                        tokenPayload.put("token", prepareToken);
                        updateLog("已生成prepare阶段CToken");
                    }

                    try (Response prepareResponse = biliRequest.prepareOrder(projectId, tokenPayload.toString())) {
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
                            if (prepareErrno == 10050) {
                                updateLog("errno=10050，直接重新准备订单...");
                                if (waitForInterval()) return;
                                continue;
                            }
                            if (prepareErrno == -401) {
                                updateLog("遇到验证码，开始处理验证码...");
                                JSONObject newPrepareResult = handleCaptcha(prepareResult, tokenPayload.toString(), projectId);
                                if (newPrepareResult != null) {
                                    updateLog("验证码处理成功，使用新结果继续");
                                    prepareResult = newPrepareResult; // Overwrite with the new result
                                    // Re-check errno and extract token from the new result
                                    if (prepareResult.optInt("errno", prepareResult.optInt("code")) == 0) {
                                        token = prepareResult.getJSONObject("data").getString("token");
                                    } else {
                                        updateLog("验证码后重新准备订单失败: " + prepareResult.toString());
                                        if (waitForInterval()) return;
                                        continue;
                                    }
                                } else {
                                    updateLog("验证码处理失败，重新开始");
                                    if (waitForInterval()) return;
                                    continue;
                                }
                            } else {
                                if (waitForInterval()) return;
                                continue;
                            }
                        }
                        token = prepareResult.getJSONObject("data").getString("token");
                        
                        // 按照Python版本逻辑，为热门项目获取ptoken
                        if (payload.optBoolean("is_hot_project", false)) {
                            String ptoken = prepareResult.getJSONObject("data").getString("ptoken");
                            payload.put("ptoken", ptoken);
                            updateLog("已获取热门项目ptoken");
                        }
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
                updateLog("订单已准备");

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
                        
                        // 按照Python版本逻辑，为热门项目生成createV2阶段的CToken
                        String createUrl = "https://show.bilibili.com/api/ticket/order/createV2?project_id=" + projectId;
                        if (payload.optBoolean("is_hot_project", false) && ctokenGenerator != null) {
                            String ctoken = ctokenGenerator.generateCToken("createV2");
                            payload.put("ctoken", ctoken);
                            String ptoken = payload.getString("ptoken");
                            createUrl += "&ptoken=" + ptoken;
                            updateLog("已生成createV2阶段CToken");
                        }
                        
                        try (okhttp3.Response createResponse = biliRequest.createOrderWithUrl(createUrl, payload.toString())) {
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

                                            // Send notifications using Notifier framework
                                            String successTitle = "抢票成功";
                                            String successMessage = "订单号: " + orderId;
                                            sendNotifications(successTitle, successMessage);
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
            // 任务结束时保存所有剩余日志
            saveAllLogsToFile("COMPLETED");
        }
    }

    private void updateLog(String message) {
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String logEntry = "[" + timestamp + "] " + configFile.getName() + ": " + message + "\n";
        
        // 添加日志到缓存
        logBuffer.append(logEntry);
        
        // 增加日志计数
        logEntryCount++;
        
        // 检查是否需要清理日志
        if (logEntryCount >= LOG_CLEANUP_THRESHOLD) {
            saveLogsToFile();
        }
        
        // 更新UI
        Platform.runLater(() -> logArea.appendText(logEntry));
    }
    
    /**
     * 将当前日志保存到文件并清空显示区域
     */
    private void saveLogsToFile() {
        // 确保日志目录存在
        try {
            Files.createDirectories(Paths.get(LOG_DIRECTORY));
        } catch (IOException e) {
            logger.error("创建日志目录失败", e);
        }
        
        // 生成日志文件名（基于任务创建时间戳和UUID）
        String fileName = LOG_DIRECTORY + File.separator + 
                          taskCreationTimestamp + "_" + taskUUID + "_auto.log";
        
        // 保存日志到文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(logBuffer.toString());
            logger.info("日志已保存到文件: " + fileName);
            
            // 在UI线程中更新日志区域并通知用户
            Platform.runLater(() -> {
                // 保存当前的日志到文件
                logArea.clear();
                logArea.appendText("[系统] 已达到" + LOG_CLEANUP_THRESHOLD + "条日志，之前的日志已保存至: " + fileName + "\n");
            });
            
            // 重置日志缓存和计数
            logBuffer = new StringBuilder();
            logEntryCount = 0;
            
        } catch (IOException e) {
            logger.error("保存日志到文件失败", e);
            // 在UI线程中通知用户保存失败
            Platform.runLater(() -> {
                logArea.appendText("[系统错误] 保存日志到文件失败: " + e.getMessage() + "\n");
            });
        }
    }
    
    /**
     * 保存所有日志到文件（任务停止或完成时调用）
     */
    private void saveAllLogsToFile(String status) {
        if (logBuffer.length() == 0) {
            return; // 没有日志内容，无需保存
        }
        
        // 确保日志目录存在
        try {
            Files.createDirectories(Paths.get(LOG_DIRECTORY));
        } catch (IOException e) {
            logger.error("创建日志目录失败", e);
            return;
        }
        
        // 生成最终日志文件名（包含状态信息）
        String fileName = LOG_DIRECTORY + File.separator + 
                          taskCreationTimestamp + "_" + taskUUID + "_" + status + ".log";
        
        // 保存日志到文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(logBuffer.toString());
            logger.info("任务结束，日志已保存到文件: " + fileName);
            
            // 在UI线程中通知用户
            Platform.runLater(() -> {
                logArea.appendText("[系统] 任务结束，所有日志已保存至: " + fileName + "\n");
            });
            
        } catch (IOException e) {
            logger.error("保存最终日志到文件失败", e);
            Platform.runLater(() -> {
                logArea.appendText("[系统错误] 保存最终日志到文件失败: " + e.getMessage() + "\n");
            });
        }
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

    // 处理验证码
    private JSONObject handleCaptcha(JSONObject prepareResult, String tokenPayload, String projectId) {
        try {
            updateLog("开始处理验证码...");
            
            // 获取验证码相关数据
            JSONObject data = prepareResult.getJSONObject("data");
            JSONObject gaData = data.getJSONObject("ga_data");
            JSONObject riskParams = gaData.getJSONObject("riskParams");
            
            // 构建请求URL编码的参数
            StringBuilder paramBuilder = new StringBuilder();
            for (String key : riskParams.keySet()) {
                if (paramBuilder.length() > 0) {
                    paramBuilder.append("&");
                }
                paramBuilder.append(key).append("=").append(java.net.URLEncoder.encode(riskParams.getString(key), "UTF-8"));
            }
            String encodedParams = paramBuilder.toString();
            
            // 1. 注册验证码
            String registerUrl = "https://api.bilibili.com/x/gaia-vgate/v1/register";
            updateLog("验证码注册请求: " + registerUrl);
            
            try (okhttp3.Response registerResponse = biliRequest.postFormData(registerUrl, encodedParams)) {
                String registerBody = safeReadResponseBody(registerResponse, "验证码注册");
                updateLog("验证码请求: " + registerBody);
                
                JSONObject registerResult = new JSONObject(registerBody);
                int registerErrno = registerResult.optInt("errno", registerResult.optInt("code"));
                
                if (registerErrno != 0) {
                    updateLog("验证码注册失败: " + registerBody);
                    return null;
                }
                
                JSONObject registerData = registerResult.getJSONObject("data");
                String csrf = biliRequest.getCookieValue("bili_jct");
                String token = registerData.getString("token");
                String type = registerData.getString("type");
                
                updateLog("验证码类型: " + type);
                
                // 2. 根据验证码类型进行处理
                if ("geetest".equals(type)) {
                    // 处理geetest验证码
                    JSONObject geetestData = registerData.getJSONObject("geetest");
                    String gt = geetestData.getString("gt");
                    String challenge = geetestData.getString("challenge");
                    
                    updateLog("开始处理geetest验证码，gt: " + gt);
                    
                    // 使用TripleValidator处理验证码
                    String geetestValidate = com.example.geetest.TripleValidator.simpleMatchRetry(gt, challenge);
                    if (geetestValidate == null || geetestValidate.isEmpty()) {
                        updateLog("geetest验证码处理失败");
                        return null;
                    }
                    
                    String geetestSeccode = geetestValidate + "|jordan";
                    updateLog("geetest_validate: " + geetestValidate + ", geetest_seccode: " + geetestSeccode);
                    
                    // 3. 验证验证码
                    String validateUrl = "https://api.bilibili.com/x/gaia-vgate/v1/validate";
                    StringBuilder validateParams = new StringBuilder();
                    validateParams.append("challenge=").append(java.net.URLEncoder.encode(challenge, "UTF-8"));
                    validateParams.append("&token=").append(java.net.URLEncoder.encode(token, "UTF-8"));
                    validateParams.append("&seccode=").append(java.net.URLEncoder.encode(geetestSeccode, "UTF-8"));
                    validateParams.append("&csrf=").append(java.net.URLEncoder.encode(csrf, "UTF-8"));
                    validateParams.append("&validate=").append(java.net.URLEncoder.encode(geetestValidate, "UTF-8"));
                    
                    try (okhttp3.Response validateResponse = biliRequest.postFormData(validateUrl, validateParams.toString())) {
                        String validateBody = safeReadResponseBody(validateResponse, "验证码验证");
                        updateLog("validate: " + validateBody);
                        
                        JSONObject validateResult = new JSONObject(validateBody);
                        int validateErrno = validateResult.optInt("errno", validateResult.optInt("code"));
                        
                        if (validateErrno == 0) {
                            updateLog("验证码成功，重新请求prepare...");
                            // Re-request prepare order and return the new result
                            try (okhttp3.Response finalPrepareResponse = biliRequest.prepareOrder(projectId, tokenPayload)) {
                                String finalPrepareBody = safeReadResponseBody(finalPrepareResponse, "最终订单准备");
                                updateLog("prepare: " + finalPrepareBody);
                                return new JSONObject(finalPrepareBody);
                            }
                        } else {
                            updateLog("验证码失败: " + validateBody);
                            return null;
                        }
                    }
                    
                } else if ("phone".equals(type)) {
                    // 处理手机验证码
                    // 从配置中获取手机号
                    String phone = "";
                    try {
                        String configContent = new String(Files.readAllBytes(configFile.toPath()));
                        JSONObject config = new JSONObject(configContent);
                        phone = config.optString("phone", "");
                    } catch (Exception e) {
                        updateLog("无法获取手机号配置: " + e.getMessage());
                        return null;
                    }
                    
                    if (phone.isEmpty()) {
                        updateLog("未配置手机号，无法处理手机验证码");
                        return null;
                    }
                    
                    // 验证手机号
                    String validateUrl = "https://api.bilibili.com/x/gaia-vgate/v1/validate";
                    StringBuilder validateParams = new StringBuilder();
                    validateParams.append("code=").append(java.net.URLEncoder.encode(phone, "UTF-8"));
                    validateParams.append("&csrf=").append(java.net.URLEncoder.encode(csrf, "UTF-8"));
                    validateParams.append("&token=").append(java.net.URLEncoder.encode(token, "UTF-8"));
                    
                    try (okhttp3.Response validateResponse = biliRequest.postFormData(validateUrl, validateParams.toString())) {
                        String validateBody = safeReadResponseBody(validateResponse, "手机验证码验证");
                        updateLog("validate: " + validateBody);
                        
                        JSONObject validateResult = new JSONObject(validateBody);
                        int validateErrno = validateResult.optInt("errno", validateResult.optInt("code"));
                        
                        if (validateErrno == 0) {
                            updateLog("手机验证码成功，重新请求prepare...");
                             // Re-request prepare order and return the new result
                            try (okhttp3.Response finalPrepareResponse = biliRequest.prepareOrder(projectId, tokenPayload)) {
                                String finalPrepareBody = safeReadResponseBody(finalPrepareResponse, "最终订单准备");
                                updateLog("prepare: " + finalPrepareBody);
                                return new JSONObject(finalPrepareBody);
                            }
                        } else {
                            updateLog("手机验证码失败: " + validateBody);
                            return null;
                        }
                    }
                    
                } else {
                    updateLog("这是一个程序无法应对的验证码类型: " + type + "，脚本无法处理");
                    return null;
                }
            }
            
        } catch (Exception e) {
            updateLog("处理验证码时发生异常: " + e.getMessage());
            logger.error("验证码处理异常", e);
            return null;
        }
    }

    // 发送推送通知
    private void sendNotifications(String title, String message) {
        // 创建推送配置
        Notifier.NotifierConfig config = new Notifier.NotifierConfig();
        config.serverchanKey = serverChanToken;
        config.pushplusToken = pushPlusToken;


        // 使用NotifierManager创建和管理推送器
        Notifier.NotifierManager manager = Notifier.NotifierManager.createFromConfig(
            config, title, message, 10, 10);
        
        // 启动所有推送器
        manager.startAll();
        
        updateLog("已发送推送通知: " + title);
    }


}
