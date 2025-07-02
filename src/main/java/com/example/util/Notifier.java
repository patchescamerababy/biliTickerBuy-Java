package com.example.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Notifier {
    private static final Logger logger = LoggerFactory.getLogger(Notifier.class);

    protected final String title;
    protected final String content;
    protected final long intervalSeconds;
    protected final long durationMinutes;
    private final AtomicBoolean stopEvent = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> future;

    public Notifier(String title, String content, long intervalSeconds, long durationMinutes) {
        this.title = title;
        this.content = content;
        this.intervalSeconds = intervalSeconds;
        this.durationMinutes = durationMinutes;
    }

    public void run() {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + TimeUnit.MINUTES.toMillis(durationMinutes);
        int count = 0;

        while (System.currentTimeMillis() < endTime && !stopEvent.get()) {
            try {
                long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(endTime - System.currentTimeMillis());
                long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(endTime - System.currentTimeMillis()) % 60;
                String message = String.format("%s [#%d, 剩余 %d分%d秒]", content, count, remainingMinutes, remainingSeconds);

                sendMessage(title, message);
                logger.info("通知发送成功");
                break; // Success, so exit loop
            } catch (Exception e) {
                logger.error("通知发送失败", e);
                try {
                    TimeUnit.SECONDS.sleep(intervalSeconds);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            count++;
        }
    }

    public void start() {
        if (future == null || future.isDone()) {
            stopEvent.set(false);
            future = executor.submit(this::run);
        }
    }

    public void stop() {
        stopEvent.set(true);
        if (future != null) {
            future.cancel(true);
        }
        executor.shutdownNow();
    }

    public abstract void sendMessage(String title, String message) throws Exception;

    public static class NotifierConfig {
        public String serverchanKey;
        public String pushplusToken;
        public String barkToken;
        public String ntfyUrl;
        public String ntfyUsername;
        public String ntfyPassword;
        public String audioPath;
    }

    public static class NotifierManager {
        private final Map<String, Notifier> notifierMap = new HashMap<>();

        public void registerNotifier(String name, Notifier notifier) {
            if (notifierMap.containsKey(name)) {
                logger.error("推送器添加失败: 已存在名为{}的推送器", name);
            } else {
                notifierMap.put(name, notifier);
                logger.info("成功添加推送器: {}", name);
            }
        }

        public void startAll() {
            notifierMap.values().forEach(Notifier::start);
        }

        public void stopAll() {
            notifierMap.values().forEach(Notifier::stop);
        }

        public static NotifierManager createFromConfig(NotifierConfig config, String title, String content,
                                                       long intervalSeconds, long durationMinutes) {
            NotifierManager manager = new NotifierManager();

            if (config.serverchanKey != null && !config.serverchanKey.isEmpty()) {
                manager.registerNotifier("ServerChan", new ServerChanUtil(config.serverchanKey, title, content, intervalSeconds, durationMinutes));
            }
            if (config.pushplusToken != null && !config.pushplusToken.isEmpty()) {
                manager.registerNotifier("PushPlus", new PushPlusUtil(config.pushplusToken, title, content, intervalSeconds, durationMinutes));
            }
            if (config.barkToken != null && !config.barkToken.isEmpty()) {
                manager.registerNotifier("Bark", new BarkUtil(config.barkToken, title, content, intervalSeconds, durationMinutes));
            }
            if (config.ntfyUrl != null && !config.ntfyUrl.isEmpty()) {
                manager.registerNotifier("Ntfy", new NtfyUtil(config.ntfyUrl, config.ntfyUsername, config.ntfyPassword, title, content, intervalSeconds, durationMinutes));
            }
            if (config.audioPath != null && !config.audioPath.isEmpty()) {
                manager.registerNotifier("Audio", new AudioUtil(config.audioPath, title, content, intervalSeconds, durationMinutes));
            }

            return manager;
        }
    }
}
