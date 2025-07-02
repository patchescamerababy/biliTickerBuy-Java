package com.example.util;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * CToken生成器 - 完全按照Python版本CTokenUtil.py实现
 * 用于生成bili ticket所需的ctoken参数
 */
public class CTokenUtil {
    
    private int touchEvent = 0;
    private int visibilityChange = 0;  // Python中命名为isibility_change，但实际是visibility_change
    private int pageUnload = 0;
    private int timer = 0;
    private int timeDifference = 0;
    private int scrollX = 0;
    private int scrollY = 0;
    private int innerWidth = 0;
    private int innerHeight = 0;
    private int outerWidth = 0;
    private int outerHeight = 0;
    private int screenX = 0;
    private int screenY = 0;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private int screenAvailWidth = 0;
    
    private final long ticketCollectionT;
    private final double timeOffset;
    private final long stayTime;

    public CTokenUtil(long ticketCollectionT, double timeOffset, long stayTime) {
        this.ticketCollectionT = ticketCollectionT;
        this.timeOffset = timeOffset;
        this.stayTime = stayTime;
    }

    /**
     * 编码方法，完全按照Python版本实现
     */
    private String encode() {
        byte[] buffer = new byte[16];
        
        // 数据映射，完全按照Python版本
        Map<Integer, DataMapping> dataMapping = new HashMap<>();
        dataMapping.put(0, new DataMapping(touchEvent, 1));
        dataMapping.put(1, new DataMapping(scrollX, 1));
        dataMapping.put(2, new DataMapping(visibilityChange, 1));
        dataMapping.put(3, new DataMapping(scrollY, 1));
        dataMapping.put(4, new DataMapping(innerWidth, 1));
        dataMapping.put(5, new DataMapping(pageUnload, 1));
        dataMapping.put(6, new DataMapping(innerHeight, 1));
        dataMapping.put(7, new DataMapping(outerWidth, 1));
        dataMapping.put(8, new DataMapping(timer, 2));
        dataMapping.put(10, new DataMapping(timeDifference, 2));
        dataMapping.put(12, new DataMapping(outerHeight, 1));
        dataMapping.put(13, new DataMapping(screenX, 1));
        dataMapping.put(14, new DataMapping(screenY, 1));
        dataMapping.put(15, new DataMapping(screenWidth, 1));
        
        int i = 0;
        while (i < 16) {
            if (dataMapping.containsKey(i)) {
                DataMapping mapping = dataMapping.get(i);
                if (mapping.length == 1) {
                    int value = mapping.data > 0 ? Math.min(255, mapping.data) : mapping.data;
                    buffer[i] = (byte) (value & 0xFF);
                    i += 1;
                } else if (mapping.length == 2) {
                    int value = mapping.data > 0 ? Math.min(65535, mapping.data) : mapping.data;
                    buffer[i] = (byte) ((value >> 8) & 0xFF);
                    buffer[i + 1] = (byte) (value & 0xFF);
                    i += 2;
                }
            } else {
                // 条件值：根据screen_height的第3位(4 & screen_height)判断使用scrollY还是screenAvailWidth
                int conditionValue = ((4 & screenHeight) != 0) ? scrollY : screenAvailWidth;
                buffer[i] = (byte) (conditionValue & 0xFF);
                i += 1;
            }
        }
        
        return toBinary(buffer);
    }
    
    /**
     * 转换为二进制编码，完全按照Python版本实现
     */
    private String toBinary(byte[] buffer) {
        // 第一次转换：byte[]转为uint16等价物
        int[] uint16Data = new int[buffer.length];
        for (int i = 0; i < buffer.length; i++) {
            uint16Data[i] = buffer[i] & 0xFF; // 转为无符号
        }
        
        // 第二次转换：uint16转为uint8
        byte[] uint8Data = new byte[uint16Data.length * 2];
        for (int i = 0; i < uint16Data.length; i++) {
            uint8Data[i * 2] = (byte) (uint16Data[i] & 0xFF);
            uint8Data[i * 2 + 1] = (byte) ((uint16Data[i] >> 8) & 0xFF);
        }
        
        return Base64.getEncoder().encodeToString(uint8Data);
    }

    /**
     * 生成CToken，完全按照Python版本实现
     */
    public String generateCToken(String type) {
        this.touchEvent = 255;                              // 触摸事件数: 手机端抓包数据
        this.visibilityChange = 2;                          // 可见性变化数: 手机端抓包数据
        this.innerWidth = 255;                              // 窗口内部宽度: 手机端抓包数据
        this.innerHeight = 255;                             // 窗口内部高度: 手机端抓包数据
        this.outerWidth = 255;                              // 窗口外部宽度: 手机端抓包数据
        this.outerHeight = 255;                             // 窗口外部高度: 手机端抓包数据
        this.screenWidth = 255;                             // 屏幕宽度: 手机端抓包数据
        this.screenHeight = new Random().nextInt(2001) + 1000;  // 屏幕高度: 用于条件判断
        this.screenAvailWidth = new Random().nextInt(100) + 1;   // 屏幕可用宽度: 用于条件判断

        if ("createV2".equals(type)) {
            // createV2阶段
            this.timeDifference = (int) ((double) System.currentTimeMillis() / 1000 + timeOffset - ticketCollectionT);
            this.timer = (int) (this.timeDifference + stayTime);
            this.pageUnload = 25;  // 页面卸载数: 手机端抓包数据
        } else {
            // prepare阶段
            this.timeDifference = 0;
            this.timer = (int) stayTime;
            this.touchEvent = new Random().nextInt(8) + 3;
        }
        
        return encode();
    }
    
    /**
     * 无参数版本，默认为createV2类型
     */
    public String generateCToken() {
        return generateCToken("createV2");
    }
    
    /**
     * 数据映射内部类
     */
    private static class DataMapping {
        final int data;
        final int length;
        
        DataMapping(int data, int length) {
            this.data = data;
            this.length = length;
        }
    }
}
