package com.example.util;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class NtfyUtil extends Notifier {
    private static final Logger logger = LoggerFactory.getLogger(NtfyUtil.class);
    private static final OkHttpClient client = new OkHttpClient();
    private final String ntfyUrl;
    private final String username;
    private final String password;

    public NtfyUtil(String ntfyUrl, String username, String password, String title, String content, long intervalSeconds, long durationMinutes) {
        super(title, content, intervalSeconds, durationMinutes);
        this.ntfyUrl = ntfyUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void sendMessage(String title, String message) throws Exception {
        Request.Builder requestBuilder = new Request.Builder()
                .url(ntfyUrl)
                .post(RequestBody.create(message.getBytes(StandardCharsets.UTF_8)))
                .header("Title", title);

        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            String credential = Credentials.basic(username, password);
            requestBuilder.header("Authorization", credential);
        }

        try (okhttp3.Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Ntfy notification failed: " + response);
            }
        }
    }
}
