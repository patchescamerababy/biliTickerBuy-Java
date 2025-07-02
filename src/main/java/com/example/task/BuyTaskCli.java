package com.example.task;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BuyTaskCli implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(BuyTaskCli.class);

    private String ticketsInfoStr;
    private String cliTimeStart;
    private int cliInterval;
    private int cliMode;
    private int cliTotalAttempts;
    private String cliHttpsProxys;
    private String cliPushplusToken;
    private String cliServerchanKey;
    private String cliNtfyUrl;
    private String cliNtfyUsername;
    private String cliNtfyPassword;
    private String cliAudioPath;

    // 命令行构造方法
    public BuyTaskCli(String ticketsInfoStr, String timeStart, int interval, int mode, int totalAttempts,
                   String httpsProxys, String pushplusToken, String serverchanKey, String ntfyUrl,
                   String ntfyUsername, String ntfyPassword, String audioPath) {
        this.ticketsInfoStr = ticketsInfoStr;
        this.cliTimeStart = timeStart;
        this.cliInterval = interval;
        this.cliMode = mode;
        this.cliTotalAttempts = totalAttempts;
        this.cliHttpsProxys = httpsProxys;
        this.cliPushplusToken = pushplusToken;
        this.cliServerchanKey = serverchanKey;
        this.cliNtfyUrl = ntfyUrl;
        this.cliNtfyUsername = ntfyUsername;
        this.cliNtfyPassword = ntfyPassword;
        this.cliAudioPath = audioPath;

    }

    public void run() {runCli();
    }
    // 命令行模式逻辑
    private void runCli() {
        try {
            System.out.println("启动命令行抢票任务...");
            JSONObject config = new JSONObject(ticketsInfoStr);

            // 这里可根据 timeStart 字符串格式进一步完善
            if (cliTimeStart != null && !cliTimeStart.isEmpty()) {
                System.out.println("等待目标时间: " + cliTimeStart);
                LocalDateTime targetTime = LocalDateTime.parse(cliTimeStart, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                LocalDateTime currentTime = LocalDateTime.now();
                if (currentTime.isBefore(targetTime)) {
                    long delayMillis = java.time.Duration.between(currentTime, targetTime).toMillis();
                    if (delayMillis > 0) {
                        CountDownLatch latch = new CountDownLatch(1);
                        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());//系统所有线程
                        scheduler.schedule(latch::countDown, delayMillis, TimeUnit.MILLISECONDS);

                        try {
                            boolean completed = latch.await(delayMillis + 1000, TimeUnit.MILLISECONDS);
                            if (!completed) {
                                logger.error("命令行等待目标时间超时，可能定时任务未被调度或线程池耗尽。");
                                scheduler.shutdown();
                                return;
                            }
                        } catch (InterruptedException ex) {
                            scheduler.shutdown();
                            return;
                        }
                        scheduler.shutdown();
                    }
                }
            }

            System.out.println("开始抢票...");
            for (int i = 0; cliMode == 0 || i < cliTotalAttempts; i++) {
                int currentAttempt = i + 1;
                System.out.println("尝试第 " + currentAttempt + " 次");

                // 实际抢票逻辑应在此实现
                if (Math.random() < 0.1) {
                    System.out.println("抢票成功!");
                    break;
                }

                // 命令行模式下的等待优化
                if (cliInterval > 0) {
                    CountDownLatch latch = new CountDownLatch(1);
                    ScheduledExecutorService cliScheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

                    cliScheduler.schedule(latch::countDown, cliInterval, TimeUnit.MILLISECONDS);

                    try {
                        boolean completed = latch.await(cliInterval + 1000, TimeUnit.MILLISECONDS);
                        if (!completed) {
                            logger.error("命令行等待间隔超时，可能定时任务未被调度或线程池耗尽。");
                            cliScheduler.shutdown();
                            return;
                        }
                    } catch (InterruptedException ex) {
                        cliScheduler.shutdown();
                        return;
                    }
                    cliScheduler.shutdown();
                }
            }
            System.out.println("命令行抢票任务结束。");
        } catch (Exception e) {
            System.err.println("命令行抢票任务失败: " + e.getMessage());
            logger.error("命令行任务失败", e);
        }
    }
}
