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

public class NtfyUtil {
    private static final Logger logger = LoggerFactory.getLogger(NtfyUtil.class);
    private static final OkHttpClient client = new OkHttpClient();

    public static void sendRepeatMessage(String ntfyUrl, String message, String title, String username, String password, int intervalSeconds, int durationMinutes) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        final long endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
        
        Runnable sendMessageTask = () -> {
            if (System.currentTimeMillis() > endTime) {
                logger.info("Ntfy repeat notification duration ended. Stopping.");
                scheduler.shutdown();
                return;
            }

            try {
                Request.Builder requestBuilder = new Request.Builder()
                    .url(ntfyUrl)
                    .post(RequestBody.create(message.getBytes(StandardCharsets.UTF_8)))
                    .header("Title", title);

                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    String credential = Credentials.basic(username, password);
                    requestBuilder.header("Authorization", credential);
                }

                client.newCall(requestBuilder.build()).execute();
                logger.info("Sent ntfy notification to {}", ntfyUrl);

            } catch (Exception e) {
                logger.error("Failed to send ntfy notification", e);
            }
        };

        scheduler.scheduleAtFixedRate(sendMessageTask, 0, intervalSeconds, TimeUnit.SECONDS);

        // Add a separate task to shut down the scheduler after the duration
        scheduler.schedule(() -> {
            if (!scheduler.isShutdown()) {
                scheduler.shutdown();
            }
        }, durationMinutes, TimeUnit.MINUTES);
    }
}
