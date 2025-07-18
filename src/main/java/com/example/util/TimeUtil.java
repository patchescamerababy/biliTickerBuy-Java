package com.example.util;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeUtil {
    private static final Logger logger = LoggerFactory.getLogger(TimeUtil.class);
    private final String ntpServer;
    private double timeOffset = 0;

    public TimeUtil(String ntpServer) {
        this.ntpServer = ntpServer;
    }

    public String computeTimeOffset() {
        NTPUDPClient client = new NTPUDPClient();
        client.setDefaultTimeout(10000);
        try {
            for (int i = 0; i < 3; i++) {
                try {
                    InetAddress hostAddr = InetAddress.getByName(ntpServer);
                    TimeInfo timeInfo = client.getTime(hostAddr);
                    timeInfo.computeDetails();
                    Long offsetValue = timeInfo.getOffset();
                    if (offsetValue != null) {
                        logger.info("时间同步成功, 将使用" + ntpServer + "时间");
                        return String.format("%.5f", -(offsetValue / 1000.0));
                    }
                } catch (Exception e) {
                    logger.warn("第" + (i + 1) + "次获取NTP时间失败, 尝试重新获取", e);
                    if (i == 2) {
                        return "error";
                    }
                    // 高效等待，避免忙等待
                    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
                    try {
                        CountDownLatch latch = new CountDownLatch(1);
                        scheduler.schedule(latch::countDown, 500, TimeUnit.MILLISECONDS);
                        latch.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    } finally {
                        scheduler.shutdown();
                    }
                }
            }
        } finally {
            client.close();
        }
        logger.error("无法获取NTP时间");
        return "error";
    }

    public void setTimeOffset(String timeOffsetStr) {
        if ("error".equals(timeOffsetStr)) {
            this.timeOffset = 0;
            logger.warn("NTP时间同步失败, 使用本地时间");
        } else {
            this.timeOffset = Double.parseDouble(timeOffsetStr);
        }
        logger.info("设置时间偏差为: " + this.timeOffset + "秒");
    }

    public double getTimeOffset() {
        return timeOffset;
    }
}
