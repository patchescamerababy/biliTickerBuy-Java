package com.example.service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;

public class LoginService {

    private String chromiumPath;

    public LoginService() {
        this.chromiumPath = null;
    }

    public LoginService(String chromiumPath) {
        this.chromiumPath = chromiumPath;
    }

    public void setChromiumPath(String chromiumPath) {
        this.chromiumPath = chromiumPath;
    }

    public void loginAndSaveCookies() {
        System.out.println("=== 开始浏览器登录流程 ===");
        System.out.println("提示：启动浏览器中，请在浏览器中手动登录");


        System.out.println("✅ Playwright环境初始化成功");

        Browser browser = null;

        // 参考Python版本的简化启动逻辑
        try {
            com.microsoft.playwright.BrowserType.LaunchOptions options =
                    new com.microsoft.playwright.BrowserType.LaunchOptions()
                            .setHeadless(false);

            // 如果设置了自定义路径，使用自定义路径
            if (chromiumPath != null && !chromiumPath.trim().isEmpty()) {
                Path executablePath = Paths.get(chromiumPath);
                if (!executablePath.toFile().exists()) {
                    System.err.println("❌ 指定的浏览器路径不存在: " + chromiumPath);
                    System.out.println("🔄 回退到自动查找本地浏览器...");
                    chromiumPath = findLocalBrowser();
                }

                if (chromiumPath != null) {
                    options.setExecutablePath(Paths.get(chromiumPath));
                    System.out.println("⏳ 使用指定浏览器: " + chromiumPath);
                }
            } else {
                // 尝试查找本地浏览器
                String localBrowserPath = findLocalBrowser();
                if (localBrowserPath != null) {
                    options.setExecutablePath(Paths.get(localBrowserPath));
                    System.out.println("⏳ 使用发现的本地浏览器: " + localBrowserPath);
                } else {
                    System.out.println("⏳ 使用默认Playwright Chromium（可能需要下载）...");
                }
            }
            try (Playwright playwright = Playwright.create()) {
                browser = playwright.chromium().launch(options);
                System.out.println("✅ 浏览器启动成功");

            } catch (Exception e) {
                System.err.println("❌ 浏览器启动失败: " + e.getMessage());
                throw new RuntimeException("浏览器启动失败，请尝试以下解决方案：\n" +
                        "1. 手动指定Chrome/Edge可执行文件路径\n" +
                        "2. 确保网络连接正常（首次使用需下载Playwright浏览器）\n" +
                        "3. 以管理员身份运行程序\n" +
                        "4. 检查防火墙设置", e);
            }

            try {
                BrowserContext context = browser.newContext();
                Page page = context.newPage();

                System.out.println("🌐 导航到登录页面...");
                page.navigate("https://show.bilibili.com/platform/home.html");

                System.out.println("🔍 尝试点击登录按钮...");
                // 参考Python版本的选择器
                try {
                    page.click(".nav-header-register");
                    System.out.println("✅ 登录按钮点击成功");
                } catch (Exception e) {
                    System.out.println("⚠️  未找到登录按钮，请手动点击登录");
                }

                System.out.println("\n==================================================");
                System.out.println("📋 请在浏览器中完成登录：");
                System.out.println("1. 如果未自动点击，请手动点击登录按钮");
                System.out.println("2. 输入账号密码或扫描二维码完成登录");
                System.out.println("3. 登录成功后程序将自动检测并获取登录信息");
                System.out.println("==================================================");

                // 参考Python版本的等待逻辑
                System.out.println("⏳ 等待登录完成...");
                try {
                    page.waitForSelector(".user-center-link", new Page.WaitForSelectorOptions().setTimeout(0));
                    System.out.println("✅ 检测到登录成功");
                } catch (Exception e) {
                    throw new RuntimeException("❌ 登录超时或失败，请确保已成功登录");
                }

                // 获取cookies
                System.out.println("📥 正在获取登录信息...");
                List<Cookie> cookies = context.cookies();

                if (cookies.isEmpty()) {
                    throw new RuntimeException("❌ 未获取到登录信息，请重试");
                }

                System.out.println("✅ 成功获取到 " + cookies.size() + " 个登录凭证");

                // 保存cookies
                saveCookiesAsJson(cookies);

                browser.close();
                System.out.println("🎉 登录流程完成，浏览器已关闭");

            } catch (Exception e) {
                try {
                    browser.close();
                } catch (Exception closeEx) {
                    // 忽略关闭异常
                }
                throw e;
            }

        } catch (Exception e) {
            String fullErrorMsg = "登录过程失败: " + e.getMessage();
            System.err.println("❌ " + fullErrorMsg);
            throw new RuntimeException(fullErrorMsg, e);
        }
    }

    /**
     * 自动查找本地安装的Chromium兼容浏览器
     *
     * @return 浏览器可执行文件路径，如果没找到返回null
     */
    private String findLocalBrowser() {
        // 第一优先级：尝试从jar中提取打包的浏览器
        String extractedBrowser = com.example.util.BrowserExtractor.extractBrowserFromJar();
        if (extractedBrowser != null) {
            return extractedBrowser;
        }

        // 第二优先级：查找开发环境下的打包浏览器文件
        String os = System.getProperty("os.name").toLowerCase();
        String baseDir = System.getProperty("user.dir");

        if (os.contains("win")) {
            // 优先使用打包的chrome-win
            String chromePackaged = baseDir + "/src/main/resources/chrome-win/chrome.exe";
            if (new java.io.File(chromePackaged).exists()) {
                System.out.println("🔍 使用开发环境打包的Chromium: " + chromePackaged);
                return chromePackaged;
            }
            // 可选：优先使用打包的webkit-1944
            String webkitPackaged = baseDir + "/src/main/resources/webkit-1944/Playwright.exe";
            if (new java.io.File(webkitPackaged).exists()) {
                System.out.println("🔍 使用开发环境打包的Webkit: " + webkitPackaged);
                return webkitPackaged;
            }
            // 第三优先级：查找系统已安装的Chrome/Edge
            String[] windowsPaths = {
                    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                    System.getProperty("user.home") + "\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe",
                    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                    System.getProperty("user.home") + "\\AppData\\Local\\Microsoft\\Edge\\Application\\msedge.exe"
            };
            for (String path : windowsPaths) {
                if (new java.io.File(path).exists()) {
                    System.out.println("🔍 找到系统Chromium兼容浏览器: " + path);
                    return path;
                }
            }
        } else if (os.contains("mac")) {
            // macOS平台 - 查找打包的chrome/mac
            String chromePackaged = baseDir + "/src/main/resources/chrome-mac/Chromium.app/Contents/MacOS/Chromium";
            if (new java.io.File(chromePackaged).exists()) {
                System.out.println("🔍 使用开发环境打包的Chromium: " + chromePackaged);
                return chromePackaged;
            }
            String[] macPaths = {
                    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
            };
            for (String path : macPaths) {
                if (new java.io.File(path).exists()) {
                    System.out.println("🔍 找到系统Chromium兼容浏览器: " + path);
                    return path;
                }
            }
        } else {
            // Linux平台 - 查找打包的chrome-linux
            String chromePackaged = baseDir + "/src/main/resources/chrome-linux/chrome";
            if (new java.io.File(chromePackaged).exists()) {
                System.out.println("🔍 使用开发环境打包的Chromium: " + chromePackaged);
                return chromePackaged;
            }
            String[] linuxPaths = {
                    "/usr/bin/google-chrome",
                    "/usr/bin/google-chrome-stable",
                    "/usr/bin/chromium",
                    "/usr/bin/chromium-browser",
                    "/snap/bin/chromium",
                    "/opt/google/chrome/chrome"
            };
            for (String path : linuxPaths) {
                if (new java.io.File(path).exists()) {
                    System.out.println("🔍 找到系统Chromium兼容浏览器: " + path);
                    return path;
                }
            }
        }

        System.out.println("⚠️  未找到打包或本地Chromium，将使用Playwright自动下载的Chromium");
        return null;
    }

    private void saveCookiesAsJson(List<Cookie> cookies) {
        JSONArray cookieArray = new JSONArray();

        for (Cookie cookie : cookies) {
            JSONObject cookieObj = new JSONObject();
            cookieObj.put("name", cookie.name);
            cookieObj.put("value", cookie.value);
            cookieObj.put("domain", cookie.domain);
            cookieObj.put("path", cookie.path);
            cookieObj.put("httpOnly", cookie.httpOnly);
            cookieObj.put("secure", cookie.secure);
            if (cookie.expires != null) {
                cookieObj.put("expires", cookie.expires);
            }
            cookieArray.put(cookieObj);
        }

        try (FileWriter file = new FileWriter("cookies.json")) {
            file.write(cookieArray.toString(4)); // pretty print JSON
            System.out.println("Successfully saved cookies.json");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
