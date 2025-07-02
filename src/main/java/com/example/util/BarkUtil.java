package com.example.util;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BarkUtil extends Notifier {

    private final String token;

    public BarkUtil(String token, String title, String content, long intervalSeconds, long durationMinutes) {
        super(title, content, intervalSeconds, durationMinutes);
        this.token = token;
    }

    @Override
    public void sendMessage(String title, String message) throws Exception {
        String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString());
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
        
        // 构建请求数据
        JSONObject data = new JSONObject();
        data.put("icon", "https://raw.githubusercontent.com/mikumifa/biliTickerBuy/refs/heads/main/assets/icon.ico");
        data.put("group", "biliTickerBuy");
        data.put("url", "https://mall.bilibili.com/neul/index.html?page=box_me&noTitleBar=1");
        data.put("sound", "telegraph");
        data.put("level", "critical");
        data.put("volume", "10");

        String requestUrl;
        
        // 判断token是否为完整URL还是普通token
        if (isValidUrl(token)) {
            requestUrl = String.format("%s/%s/%s", token.replaceAll("/$", ""), encodedTitle, encodedMessage);
        } else {
            requestUrl = String.format("https://api.day.app/%s/%s/%s", token, encodedTitle, encodedMessage);
        }

        OkHttpClient client = new OkHttpClient();
        
        // 使用POST方法发送JSON数据
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(data.toString(), JSON);
        
        Request request = new Request.Builder()
                .url(requestUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Bark通知发送失败: " + response.body().string());
            }
        }
    }
    
    /**
     * 判断字符串是否为有效的URL
     */
    private boolean isValidUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            String scheme = url.getProtocol();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (Exception e) {
            return false;
        }
    }
}
