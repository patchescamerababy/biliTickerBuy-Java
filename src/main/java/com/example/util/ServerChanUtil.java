package com.example.util;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ServerChanUtil extends Notifier {
    private static final Logger logger = LoggerFactory.getLogger(ServerChanUtil.class);
    private static final OkHttpClient client = new OkHttpClient();
    private final String token;

    public ServerChanUtil(String token, String title, String content, long intervalSeconds, long durationMinutes) {
        super(title, content, intervalSeconds, durationMinutes);
        this.token = token;
    }

    @Override
    public void sendMessage(String title, String message) throws Exception {
        String url = "https://sctapi.ftqq.com/" + token + ".send";
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
                throw new Exception("ServerChan notification failed: " + response);
            }
        }
    }

}
