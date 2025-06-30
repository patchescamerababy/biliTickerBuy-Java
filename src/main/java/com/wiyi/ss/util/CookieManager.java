package com.wiyi.ss.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wiyi.ss.service.LoginService;

public class CookieManager {
    private static final Logger logger = LoggerFactory.getLogger(CookieManager.class);
    private String cookieStr;
    private JSONArray cookieArray;

    public CookieManager(JSONArray cookies) {
        this.cookieArray = cookies;
        this.cookieStr = parseJsonCookies(cookies);
    }

    public static CookieManager loadOrLoginIfNeeded(boolean force) {
        File cookieFile = new File("cookies.json");
        JSONArray cookies = null;
        if (!cookieFile.exists() || force) {
            // 调用登录服务获取cookie
            LoginService loginService = new LoginService();
            loginService.loginAndSaveCookies();
        }
        // 再次尝试加载
        try (FileReader reader = new FileReader(cookieFile)) {
            char[] buf = new char[(int) cookieFile.length()];
            int len = reader.read(buf);
            String jsonStr = new String(buf, 0, len);
            // 兼容嵌套cookie结构
            Object parsed = new org.json.JSONTokener(jsonStr).nextValue();
            if (parsed instanceof JSONArray) {
                cookies = (JSONArray) parsed;
            } else if (parsed instanceof JSONObject) {
                JSONObject obj = (JSONObject) parsed;
                // 兼容TinyDB格式: {"_default": {"1": {"key": "cookie", "value": [...]}}}
                if (obj.has("_default")) {
                    JSONObject def = obj.getJSONObject("_default");
                    for (String k : def.keySet()) {
                        JSONObject inner = def.getJSONObject(k);
                        if (inner.has("key") && "cookie".equals(inner.getString("key")) && inner.has("value")) {
                            Object val = inner.get("value");
                            if (val instanceof JSONArray) {
                                cookies = (JSONArray) val;
                                break;
                            }
                        }
                    }
                }
                // 兼容直接包含cookies字段的格式
                if (cookies == null && obj.has("cookies")) {
                    Object val = obj.get("cookies");
                    if (val instanceof JSONArray) {
                        cookies = (JSONArray) val;
                    }
                }
            }
            if (cookies == null) cookies = new JSONArray();
        } catch (IOException e) {
            logger.error("加载cookies.json失败: " + e.getMessage());
            cookies = new JSONArray();
        }
        return new CookieManager(cookies);
    }

    public boolean haveCookies() {
        return cookieArray != null && cookieArray.length() > 0;
    }

    private String parseJsonCookies(JSONArray cookieArray) {
        if (cookieArray == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cookieArray.length(); i++) {
            JSONObject cookieObj = cookieArray.getJSONObject(i);
            sb.append(cookieObj.getString("name"))
                    .append("=")
                    .append(cookieObj.optString("value")) // Use optString for safety
                    .append("; ");
        }
        return sb.toString();
    }

    public String getCookiesStr() {
        return cookieStr;
    }

    public String getCookiesValue(String name) {
        if (cookieArray == null) {
            return null;
        }
        for (int i = 0; i < cookieArray.length(); i++) {
            JSONObject cookieObj = cookieArray.getJSONObject(i);
            if (name.equals(cookieObj.optString("name"))) {
                return cookieObj.optString("value");
            }
        }
        return null;
    }

    public java.util.Map<String, String> getCookies() {
        java.util.Map<String, String> cookieMap = new java.util.HashMap<>();
        if (cookieArray == null) {
            return cookieMap;
        }
        for (int i = 0; i < cookieArray.length(); i++) {
            JSONObject cookieObj = cookieArray.getJSONObject(i);
            cookieMap.put(cookieObj.optString("name"), cookieObj.optString("value"));
        }
        return cookieMap;
    }

    public JSONArray getRawCookies() {
        return cookieArray;
    }

    // 简单的配置项存取，使用config.json文件
    public static Object getConfigValue(String name, Object defaultValue) {
        File configFile = new File("config.json");
        JSONObject config = new JSONObject();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                char[] buf = new char[(int) configFile.length()];
                int len = reader.read(buf);
                String jsonStr = new String(buf, 0, len);
                config = new JSONObject(jsonStr);
            } catch (IOException e) {
                logger.error("读取config.json失败: " + e.getMessage());
            }
        }
        return config.has(name) ? config.get(name) : defaultValue;
    }

    public static void setConfigValue(String name, Object value) {
        File configFile = new File("config.json");
        JSONObject config = new JSONObject();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                char[] buf = new char[(int) configFile.length()];
                int len = reader.read(buf);
                String jsonStr = new String(buf, 0, len);
                config = new JSONObject(jsonStr);
            } catch (IOException e) {
                logger.error("读取config.json失败: " + e.getMessage());
            }
        }
        config.put(name, value);
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(config.toString(4));
        } catch (IOException e) {
            logger.error("写入config.json失败: " + e.getMessage());
        }
    }
}
