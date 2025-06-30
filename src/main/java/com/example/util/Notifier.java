package com.example.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Notifier {
    private static final Logger logger = LoggerFactory.getLogger(Notifier.class);

    protected final String title;
    protected final String content;
    private final long intervalSeconds;
    private final long durationMinutes;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> future;

    public Notifier(String title, String content, long intervalSeconds, long durationMinutes) {
        this.title = title;
        this.content = content;
        this.intervalSeconds = intervalSeconds;
        this.durationMinutes = durationMinutes;
    }

    public void start() {
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + TimeUnit.MINUTES.toMillis(durationMinutes);
        
        future = scheduler.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() > endTime) {
                stop();
                return;
            }
            try {
                long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(endTime - System.currentTimeMillis());
                long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(endTime - System.currentTimeMillis()) % 60;
                String message = String.format("%s [剩余 %d分%d秒]", content, remainingMinutes, remainingSeconds);
                sendMessage(title, message);
            } catch (Exception e) {
                logger.error("通知发送失败", e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
        scheduler.shutdown();
    }

    public abstract void sendMessage(String title, String message) throws Exception;

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
    }
}
