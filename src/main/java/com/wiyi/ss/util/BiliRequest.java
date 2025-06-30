package com.wiyi.ss.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public BiliRequest(JSONArray cookies, String proxy) {
        this.cookieManager = new CookieManager(cookies);
        this.proxyList = Arrays.asList(proxy.split(","));
        if (proxyList.isEmpty()) {
            throw new IllegalArgumentException("At least have none proxy");
        }

        this.headers = new Headers.Builder()
                .add("accept", "*/*")
                .add("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6,zh-TW;q=0.5,ja;q=0.4")
                .add("referer", "https://show.bilibili.com/")
                .add("priority", "u=1, i")
                .add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0")
                .build();
        
        this.client = buildClient();
    }

    private OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS);

        String currentProxy = proxyList.get(nowProxyIdx);
        if (!"none".equalsIgnoreCase(currentProxy)) {
            String[] proxyParts = currentProxy.replace("http://", "").replace("https://", "").split(":");
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyParts[0], Integer.parseInt(proxyParts[1]))));
        }
        
        return builder.build();
    }

    private void switchProxy() {
        int oldProxyIdx = nowProxyIdx;
        nowProxyIdx = (nowProxyIdx + 1) % proxyList.size();
        this.client = buildClient();
        logger.warn("遭遇412风控，立即切换代理：{} -> {}", proxyList.get(oldProxyIdx), proxyList.get(nowProxyIdx));
    }

    private void countAndSleep(int threshold, int sleepTime) {
        this.requestCount++;
        if (this.requestCount % threshold == 0) {
            logger.warn("连续遭遇 {} 次412风控，休眠 {} 秒避免频繁切换", threshold, sleepTime);
            try {
                TimeUnit.SECONDS.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("休眠被中断", e);
            }
        }
    }

    private void clearRequestCount() {
        this.requestCount = 0;
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
        Response response = client.newCall(request).execute();
        if (response.code() == 412) {
            countAndSleep(60, 60);
            switchProxy();
            return execute(request);
        }
        clearRequestCount();
        return response;
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
                String responseBody = response.body().string();
                org.json.JSONObject json = new org.json.JSONObject(responseBody);
                // 返回result["data"]["uname"]
                return json.getJSONObject("data").getString("uname");
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
        return ProxyTester.testProxyConnectivity(getCurrentProxy(), 10);
    }
    
    /**
     * 测试所有代理连通性
     * @return 测试结果
     */
    public String testAllProxies() {
        String proxyString = String.join(",", proxyList);
        return ProxyTester.testProxyConnectivity(proxyString, 10);
    }
}
