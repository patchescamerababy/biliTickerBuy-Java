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
        nowProxyIdx = (nowProxyIdx + 1) % proxyList.size();
        this.client = buildClient();
        logger.warn("412风控，切换代理到 {}", proxyList.get(nowProxyIdx));
    }

    private void countAndSleep(int threshold, int sleepTime) {
        this.requestCount++;
        if (this.requestCount % threshold == 0) {
            logger.info("达到 {} 次请求 412，休眠 {} 秒", threshold, sleepTime);
            try {
                TimeUnit.SECONDS.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Sleep interrupted", e);
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
}
