package com.example.util;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ServerChanUtil {
    private static final Logger logger = LoggerFactory.getLogger(ServerChanUtil.class);
    private static final OkHttpClient client = new OkHttpClient();

    public static void sendMessage(String token, String title, String message) {
        String url = "https://sctapi.ftqq.com/" + token + ".send";
        try {
            JSONObject json = new JSONObject();
            json.put("title", title);
            json.put("desp", message);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("ServerChan notification failed: " + response);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send ServerChan notification", e);
        }
    }
}
