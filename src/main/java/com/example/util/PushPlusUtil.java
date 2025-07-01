package com.example.util;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PushPlusUtil {
    private static final Logger logger = LoggerFactory.getLogger(PushPlusUtil.class);
    private static final OkHttpClient client = new OkHttpClient();

    public static void sendMessage(String token, String title, String message) {
        String url = "http://www.pushplus.plus/send";
        try {
            JSONObject json = new JSONObject();
            json.put("token", token);
            json.put("title", title);
            json.put("content", message);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("PushPlus notification failed: " + response);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send PushPlus notification", e);
        }
    }
}
