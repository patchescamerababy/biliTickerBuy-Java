package com.example.config;

import java.io.File;
import java.nio.file.Paths;

public class PlaywrightConfig {
    
    private static boolean initialized = false;
    
    public static void init() {
        if (initialized) {
            return;
        }
        
        // 设置 Playwright 驱动路径
        String driverPath = Paths.get("drivers", "Playwright.exe").toString();
        String browsersPath = Paths.get("browsers").toString();
        
        // 检查驱动文件是否存在
        if (new File(driverPath).exists()) {
            System.setProperty("PLAYWRIGHT_DRIVER_PATH", driverPath);
            System.out.println("✅ 设置 Playwright 驱动路径: " + driverPath);
        } else {
            System.out.println("⚠️ Playwright 驱动文件不存在: " + driverPath);
        }
        
        // 检查浏览器目录是否存在
        if (new File(browsersPath).exists()) {
            System.setProperty("PLAYWRIGHT_BROWSERS_PATH", browsersPath);
            System.out.println("✅ 设置 Playwright 浏览器路径: " + browsersPath);
        } else {
            System.out.println("⚠️ Playwright 浏览器目录不存在: " + browsersPath);
        }
        
        // 设置其他 Playwright 相关环境变量
        System.setProperty("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "true");
        System.setProperty("PLAYWRIGHT_DOWNLOAD_HOST", "");
        
        // 禁用 Playwright 的自动下载和安装
        System.setProperty("playwright.cli.skip-browser-install", "true");
        
        System.out.println("✅ Playwright 配置初始化完成");
        initialized = true;
    }
}
