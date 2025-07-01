package com.example.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BiliRequest {
    private static final Logger logger = LoggerFactory.getLogger(BiliRequest.class);

    private OkHttpClient client;
    private final List<String> proxyList;
    private int nowProxyIdx = 0;
    private int requestCount = 0;
    private final CookieManager cookieManager;
    private final Headers headers;
    private long lastSwitchTime = 0;
    private static final long MIN_SWITCH_INTERVAL = 1000; // 最小切换间隔1秒
    private volatile Call currentCall;
    
    // 代理使用状态跟踪
    private final boolean[] proxyUsedStatus; // 标记每个代理是否已被使用过
    private int completedRounds = 0; // 完成的轮换轮数

    public BiliRequest(JSONArray cookies, String proxy) {
        this.cookieManager = new CookieManager(cookies);
        if (proxy == null || proxy.trim().isEmpty()) {
            proxy = "none";
        }
        this.proxyList = Arrays.asList(proxy.split(","));
        if (proxyList.isEmpty()) {
            throw new IllegalArgumentException("At least have none proxy");
        }

        // 初始化代理使用状态跟踪
        this.proxyUsedStatus = new boolean[proxyList.size()];
        
        this.headers = new Headers.Builder()
                .add("accept", "*/*")
                .add("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6,zh-TW;q=0.5,ja;q=0.4")
                .add("referer", "https://show.bilibili.com/")
                .add("priority", "u=1, i")
                .add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0")
                .build();
        
        // 标记初始代理为已使用
        this.proxyUsedStatus[nowProxyIdx] = true;
        this.client = buildClient();
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS);
    
        String currentProxy = proxyList.get(nowProxyIdx);
        if (!"none".equalsIgnoreCase(currentProxy) && !currentProxy.trim().isEmpty()) {
            String[] proxyParts = currentProxy.replace("http://", "").replace("https://", "").split(":");
            if (proxyParts.length >= 2) {
                String host = proxyParts[0];
                if (host.isEmpty()) {
                    logger.error("无效的代理格式，主机名不能为空: {}", currentProxy);
                } else {
                    try {
                        int port = Integer.parseInt(proxyParts[1]);
                        builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
                    } catch (NumberFormatException e) {
                        logger.error("无效的代理端口: {}", proxyParts[1], e);
                    } catch (Exception e) {
                        logger.error("设置代理时发生未知错误: {}", currentProxy, e);
                    }
                }
            } else {
                logger.error("无效的代理格式，应为 host:port: {}", currentProxy);
            }
        }
        
        return builder.build();
    }

    /**
     * 切换到下一个代理
     * @return 切换后的代理描述
     */
    public String switchToNextProxy() {
        int oldProxyIdx = nowProxyIdx;
        
        // 切换到下一个代理
        nowProxyIdx = (nowProxyIdx + 1) % proxyList.size();
        
        // 标记新代理为已使用
        proxyUsedStatus[nowProxyIdx] = true;
        
        // 检查是否完成了一轮轮换
        if (nowProxyIdx == 0 && oldProxyIdx == proxyList.size() - 1) {
            completedRounds++;
        }
        
        this.client = buildClient();
        
        String oldProxy = "none".equalsIgnoreCase(proxyList.get(oldProxyIdx)) ? "直连" : proxyList.get(oldProxyIdx);
        String newProxy = "none".equalsIgnoreCase(proxyList.get(nowProxyIdx)) ? "直连" : proxyList.get(nowProxyIdx);
        
        // 获取使用状态信息
        String usageInfo = getProxyUsageStatus();
        
        return String.format("%s -> %s (%s)", oldProxy, newProxy, usageInfo);
    }

    /**
     * 记录412错误次数
     */
    public void increment412Count() {
        this.requestCount++;
    }

    /**
     * 获取412错误次数
     */
    public int get412Count() {
        return this.requestCount;
    }

    /**
     * 清除412错误次数
     */
    public void clear412Count() {
        this.requestCount = 0;
    }

    /**
     * 检查是否已经轮换了所有代理
     */
    public boolean hasTriedAllProxies() {
        return this.requestCount >= proxyList.size();
    }

    public Response get(String url) throws IOException {
        return execute(new Request.Builder().url(url).headers(headers).addHeader("cookie", cookieManager.getCookiesStr()).build());
    }

    public Response post(String url, RequestBody body) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .headers(headers)
                .addHeader("content-type", "application/x-www-form-urlencoded")
                .addHeader("cookie", cookieManager.getCookiesStr())
                .build();
        return execute(request);
    }

    public Response postJson(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(json, okhttp3.MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .headers(headers)
                .addHeader("content-type", "application/json")
                .addHeader("cookie", cookieManager.getCookiesStr())
                .build();
        return execute(request);
    }

    private Response execute(Request request) throws IOException {
        this.currentCall = client.newCall(request);
        try {
            Response response = this.currentCall.execute();
            // 不再在这里处理412，交给调用方处理
            if (response.code() != 412) {
                clear412Count();
            }
            return response;
        } finally {
            this.currentCall = null;
        }
    }

    public void cancelCurrentRequest() {
        if (this.currentCall != null) {
            this.currentCall.cancel();
        }
    }

    public Response prepareOrder(String projectId, String payload) throws IOException {
        String url = "https://show.bilibili.com/api/ticket/order/prepare?project_id=" + projectId;
        return postJson(url, payload);
    }

    public Response createOrder(String projectId, String payload) throws IOException {
        String url = "https://show.bilibili.com/api/ticket/order/createV2?project_id=" + projectId;
        return postJson(url, payload);
    }

    public Response getPayParam(String orderId) throws IOException {
        String url = "https://show.bilibili.com/api/ticket/order/getPayParam?order_id=" + orderId;
        return get(url);
    }

    public String getCsrf() {
        return cookieManager.getCookies().get("bili_jct");
    }

    public java.util.Map<String, String> getCookies() {
        return cookieManager.getCookies();
    }

    public JSONArray getRawCookies() {
        return cookieManager.getRawCookies();
    }

    public JSONArray getCookiesAsJson() {
        return cookieManager.getRawCookies();
    }

    /**
     * 用cookie请求B站接口，获取用户昵称
     */
    public String getUserNickname() {
        try {
            // 检查是否有cookies
            if (cookieManager.getRawCookies() == null || cookieManager.getRawCookies().length() == 0) {
                logger.warn("获取用户名失败，请重新登录");
                return "未登录";
            }

            String url = "https://api.bilibili.com/x/web-interface/nav";
            Response response = get(url);
            if (response.isSuccessful()) {
                String responseBody = null;
                if (response.body() != null) {
                    responseBody = response.body().string();
                }
                org.json.JSONObject json = null;
                if (responseBody != null) {
                    json = new org.json.JSONObject(responseBody);
                }
                // 返回result["data"]["uname"]
                if (json != null) {
                    return json.getJSONObject("data").getString("uname");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get user nickname", e);
        }
        return "未登录";
    }
    
    /**
     * 获取当前使用的代理
     * @return 当前代理字符串
     */
    public String getCurrentProxy() {
        String currentProxy = proxyList.get(nowProxyIdx);
        return "none".equalsIgnoreCase(currentProxy) ? "直连" : currentProxy;
    }
    
    /**
     * 获取代理列表
     * @return 代理列表
     */
    public List<String> getProxyList() {
        return proxyList;
    }
    
    /**
     * 获取当前代理索引
     * @return 当前代理索引
     */
    public int getCurrentProxyIndex() {
        return nowProxyIdx;
    }
    
    /**
     * 手动切换到指定的代理
     * @param proxyIndex 代理索引
     */
    public void switchToProxy(int proxyIndex) {
        if (proxyIndex >= 0 && proxyIndex < proxyList.size()) {
            int oldProxyIdx = nowProxyIdx;
            nowProxyIdx = proxyIndex;
            this.client = buildClient();
            logger.info("手动切换代理：{} -> {}", proxyList.get(oldProxyIdx), proxyList.get(nowProxyIdx));
        } else {
            logger.warn("无效的代理索引: {}, 可用范围: 0-{}", proxyIndex, proxyList.size() - 1);
        }
    }
    
    /**
     * 测试当前代理连通性
     * @return 测试结果
     */
    public String testCurrentProxy() {
        return ProxyTester.testProxyConnectivity(getCurrentProxy(), 10, cookieManager.getCookiesStr());
    }
    
    /**
     * 测试所有代理连通性
     * @return 测试结果
     */
    public String testAllProxies() {
        String proxyString = String.join(",", proxyList);
        return ProxyTester.testProxyConnectivity(proxyString, 10, cookieManager.getCookiesStr());
    }
    
    /**
     * 获取代理使用状态信息
     * @return 状态描述字符串
     */
    public String getProxyUsageStatus() {
        int usedCount = 0;
        for (boolean used : proxyUsedStatus) {
            if (used) usedCount++;
        }
        
        if (completedRounds > 0) {
            return String.format("第%d轮 %d/%d已用", completedRounds + 1, usedCount, proxyList.size());
        } else {
            return String.format("%d/%d已用", usedCount, proxyList.size());
        }
    }
    
    /**
     * 重置代理使用状态，开始新一轮轮换
     */
    public void resetProxyUsageStatus() {
        Arrays.fill(proxyUsedStatus, false);
        proxyUsedStatus[nowProxyIdx] = true; // 标记当前代理为已使用
        completedRounds = 0;
        requestCount = 0;
    }
    
    /**
     * 获取完成的轮换轮数
     * @return 轮换轮数
     */
    public int getCompletedRounds() {
        return completedRounds;
    }
    
    /**
     * 检查是否所有代理都已被使用过
     * @return 如果所有代理都被使用过则返回true
     */
    public boolean allProxiesUsed() {
        for (boolean used : proxyUsedStatus) {
            if (!used) return false;
        }
        return true;
    }
    
    /**
     * 获取代理使用状态的详细信息
     * @return 包含每个代理使用状态的字符串
     */
    public String getDetailedProxyStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("代理使用状态 (完成轮数: ").append(completedRounds).append("):\n");
        
        for (int i = 0; i < proxyList.size(); i++) {
            String proxy = "none".equalsIgnoreCase(proxyList.get(i)) ? "直连" : proxyList.get(i);
            String status = proxyUsedStatus[i] ? "✓已用" : "未用";
            String current = (i == nowProxyIdx) ? " [当前]" : "";
            sb.append(String.format("  [%d] %s: %s%s\n", i, proxy, status, current));
        }
        
        return sb.toString();
    }
}
