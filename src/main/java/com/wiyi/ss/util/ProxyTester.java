package com.wiyi.ss.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * HTTP代理连通性测试工具
 * 用于测试代理服务器的可用性和响应时间
 */
public class ProxyTester {
    private static final Logger logger = LoggerFactory.getLogger(ProxyTester.class);
    
    private final int timeout;
    private final String testUrl;
    private final Headers defaultHeaders;
    
    // 代理格式验证正则表达式
    private static final Pattern PROXY_PATTERN = Pattern.compile(
        "^(https?|socks[45])://[^:]+:\\d+$"
    );
    
    /**
     * 代理测试结果
     */
    public static class ProxyTestResult {
        private String proxy;
        private String status;
        private Long responseTime;
        private String error;
        private String ipInfo;
        
        public ProxyTestResult(String proxy) {
            this.proxy = proxy;
            this.status = "failed";
        }
        
        // Getters and Setters
        public String getProxy() { return proxy; }
        public void setProxy(String proxy) { this.proxy = proxy; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Long getResponseTime() { return responseTime; }
        public void setResponseTime(Long responseTime) { this.responseTime = responseTime; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public String getIpInfo() { return ipInfo; }
        public void setIpInfo(String ipInfo) { this.ipInfo = ipInfo; }
        
        public boolean isSuccess() {
            return "success".equals(status);
        }
        
        public boolean isPartial() {
            return "partial".equals(status);
        }
    }
    
    /**
     * 构造函数
     * @param timeout 超时时间（秒）
     */
    public ProxyTester(int timeout) {
        this.timeout = timeout;
        this.testUrl = "https://api.bilibili.com/x/web-interface/nav";
        this.defaultHeaders = new Headers.Builder()
                .add("accept", "*/*")
                .add("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6,zh-TW;q=0.5,ja;q=0.4")
                .add("referer", "https://show.bilibili.com/")
                .add("priority", "u=1, i")
                .add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0")
                .build();
    }
    
    /**
     * 构造函数，使用默认超时时间10秒
     */
    public ProxyTester() {
        this(10);
    }
    
    /**
     * 测试单个代理的连通性
     * @param proxyStr 代理字符串
     * @return 测试结果
     */
    public ProxyTestResult testSingleProxy(String proxyStr) {
        ProxyTestResult result = new ProxyTestResult(proxyStr);
        
        try {
            OkHttpClient client = buildClient(proxyStr);
            if (client == null) {
                result.setError("代理格式无效");
                return result;
            }
            
            // 如果是直连
            if ("none".equalsIgnoreCase(proxyStr) || "direct".equalsIgnoreCase(proxyStr)) {
                result.setProxy("直连");
            }
            
            Request request = new Request.Builder()
                    .url(testUrl)
                    .headers(defaultHeaders)
                    .build();
            
            long startTime = System.currentTimeMillis();
            try (Response response = client.newCall(request).execute()) {
                long endTime = System.currentTimeMillis();
                long responseTime = endTime - startTime;
                
                result.setResponseTime(responseTime);
                
                if (response.isSuccessful()) {
                    result.setStatus("success");
                    try {
                        String responseBody = response.body().string();
                        // 只要HTTP 200就算连通，显示部分响应内容
                        String preview = responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
                        result.setIpInfo("响应内容预览: " + preview.replaceAll("[\\r\\n]+", " "));
                    } catch (Exception e) {
                        result.setIpInfo("响应内容读取失败");
                    }
                } else {
                    result.setError("B站连接失败: HTTP " + response.code());
                    result.setStatus("partial");
                }
            }
            
        } catch (java.net.SocketTimeoutException e) {
            result.setError("连接超时 (>" + timeout + "s)");
        } catch (java.net.ConnectException e) {
            if (e.getMessage().contains("proxy") || e.getMessage().contains("Proxy")) {
                result.setError("代理服务器错误或无法连接");
            } else {
                result.setError("网络连接失败");
            }
        } catch (IOException e) {
            if (e.getMessage().contains("proxy") || e.getMessage().contains("Proxy")) {
                result.setError("代理连接失败");
            } else {
                result.setError("网络连接失败: " + e.getMessage());
            }
        } catch (Exception e) {
            result.setError("未知错误: " + e.getMessage());
            logger.error("代理测试异常", e);
        }
        
        return result;
    }
    
    /**
     * 构建HTTP客户端
     * @param proxyStr 代理字符串
     * @return OkHttpClient实例，如果代理格式无效则返回null
     */
    private OkHttpClient buildClient(String proxyStr) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS);
        
        // 处理代理设置
        if (proxyStr != null && !"none".equalsIgnoreCase(proxyStr) && !"direct".equalsIgnoreCase(proxyStr)) {
            if (!validateProxyFormat(proxyStr)) {
                return null;
            }
            
            try {
                // 解析代理地址
                String[] parts = proxyStr.split("://");
                if (parts.length != 2) {
                    return null;
                }
                
                String protocol = parts[0].toLowerCase();
                String[] hostPort = parts[1].split(":");
                if (hostPort.length != 2) {
                    return null;
                }
                
                String host = hostPort[0];
                int port = Integer.parseInt(hostPort[1]);
                
                Proxy.Type proxyType;
                switch (protocol) {
                    case "http":
                    case "https":
                        proxyType = Proxy.Type.HTTP;
                        break;
                    case "socks4":
                    case "socks5":
                        proxyType = Proxy.Type.SOCKS;
                        break;
                    default:
                        return null;
                }
                
                Proxy proxy = new Proxy(proxyType, new InetSocketAddress(host, port));
                builder.proxy(proxy);
                
            } catch (Exception e) {
                logger.error("解析代理地址失败: " + proxyStr, e);
                return null;
            }
        }
        
        return builder.build();
    }
    
    /**
     * 验证代理格式是否正确
     * @param proxyStr 代理字符串
     * @return 是否有效
     */
    private boolean validateProxyFormat(String proxyStr) {
        if (proxyStr == null || proxyStr.trim().isEmpty()) {
            return false;
        }
        
        if ("none".equalsIgnoreCase(proxyStr) || "direct".equalsIgnoreCase(proxyStr)) {
            return true;
        }
        
        return PROXY_PATTERN.matcher(proxyStr.trim()).matches();
    }
    
    /**
     * 测试代理列表的连通性
     * @param proxyString 代理字符串（逗号分隔）
     * @param maxWorkers 最大并发数
     * @return 测试结果列表
     */
    public List<ProxyTestResult> testProxyList(String proxyString, int maxWorkers) {
        List<String> proxyList = parseProxyString(proxyString);
        List<ProxyTestResult> results = new ArrayList<>();
        
        if (proxyList.isEmpty()) {
            return results;
        }
        
        // 使用线程池并发测试
        ExecutorService executor = Executors.newFixedThreadPool(maxWorkers);
        List<Future<ProxyTestResult>> futures = new ArrayList<>();
        
        for (String proxy : proxyList) {
            Future<ProxyTestResult> future = executor.submit(() -> {
                ProxyTestResult result = testSingleProxy(proxy);
                logger.info("代理测试完成: {} - {}", result.getProxy(), result.getStatus());
                return result;
            });
            futures.add(future);
        }
        
        // 收集结果
        for (Future<ProxyTestResult> future : futures) {
            try {
                ProxyTestResult result = future.get();
                results.add(result);
            } catch (Exception e) {
                logger.error("获取代理测试结果异常", e);
            }
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout * 2L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 按照原始顺序排序结果
        results.sort((a, b) -> {
            int indexA = getProxyIndex(proxyList, a.getProxy());
            int indexB = getProxyIndex(proxyList, b.getProxy());
            return Integer.compare(indexA, indexB);
        });
        
        return results;
    }
    
    /**
     * 使用默认并发数测试代理列表
     * @param proxyString 代理字符串
     * @return 测试结果列表
     */
    public List<ProxyTestResult> testProxyList(String proxyString) {
        return testProxyList(proxyString, 5);
    }
    
    /**
     * 解析代理字符串
     * @param proxyString 代理字符串
     * @return 代理列表
     */
    private List<String> parseProxyString(String proxyString) {
        List<String> proxyList = new ArrayList<>();
        
        if (proxyString == null || proxyString.trim().isEmpty()) {
            proxyList.add("none");
        } else {
            String[] proxies = proxyString.split(",");
            for (String proxy : proxies) {
                String trimmedProxy = proxy.trim();
                if (!trimmedProxy.isEmpty()) {
                    proxyList.add(trimmedProxy);
                }
            }
            
            if (proxyList.isEmpty()) {
                proxyList.add("none");
            } else {
                // 如果列表中没有直连选项，则添加到开头
                boolean hasDirectConnection = proxyList.stream()
                        .anyMatch(p -> "none".equalsIgnoreCase(p) || "direct".equalsIgnoreCase(p));
                if (!hasDirectConnection) {
                    proxyList.add(0, "none");
                }
            }
        }
        
        return proxyList;
    }
    
    /**
     * 获取代理在原始列表中的索引
     * @param proxyList 原始代理列表
     * @param proxy 代理字符串
     * @return 索引
     */
    private int getProxyIndex(List<String> proxyList, String proxy) {
        if ("直连".equals(proxy)) {
            for (int i = 0; i < proxyList.size(); i++) {
                if ("none".equalsIgnoreCase(proxyList.get(i)) || "direct".equalsIgnoreCase(proxyList.get(i))) {
                    return i;
                }
            }
        }
        
        for (int i = 0; i < proxyList.size(); i++) {
            if (proxyList.get(i).equals(proxy)) {
                return i;
            }
        }
        
        return Integer.MAX_VALUE;
    }
    
    /**
     * 格式化测试结果为可读文本
     * @param results 测试结果列表
     * @return 格式化后的文本
     */
    public String formatTestResults(List<ProxyTestResult> results) {
        if (results == null || results.isEmpty()) {
            return "无代理测试结果";
        }
        
        StringBuilder output = new StringBuilder();
        output.append("代理连通性测试结果:\n");
        output.append(repeatString("=", 50)).append("\n");
        
        int successCount = 0;
        for (int i = 0; i < results.size(); i++) {
            ProxyTestResult result = results.get(i);
            String proxy = result.getProxy();
            String status = result.getStatus();
            Long responseTime = result.getResponseTime();
            String error = result.getError();
            String ipInfo = result.getIpInfo();
            
            if (result.isSuccess()) {
                output.append(String.format("✅ [%d] %s\n", i + 1, proxy));
                output.append(String.format("    响应时间: %dms\n", responseTime));
                if (ipInfo != null) {
                    output.append(String.format("    状态: %s\n", ipInfo));
                }
                successCount++;
            } else if (result.isPartial()) {
                output.append(String.format("⚠️  [%d] %s\n", i + 1, proxy));
                if (responseTime != null) {
                    output.append(String.format("    响应时间: %dms\n", responseTime));
                }
                if (ipInfo != null) {
                    output.append(String.format("    状态: %s\n", ipInfo));
                }
                if (error != null) {
                    output.append(String.format("    警告: %s\n", error));
                }
            } else {
                output.append(String.format("❌ [%d] %s\n", i + 1, proxy));
                if (error != null) {
                    output.append(String.format("    错误: %s\n", error));
                }
            }
            
            output.append("\n");
        }
        
        output.append(repeatString("=", 50)).append("\n");
        output.append(String.format("测试统计: %d/%d 个代理可用", successCount, results.size()));
        
        return output.toString();
    }
    
    /**
     * 重复字符串指定次数（Java 8兼容）
     * @param str 要重复的字符串
     * @param count 重复次数
     * @return 重复后的字符串
     */
    private static String repeatString(String str, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    /**
     * 静态方法：测试代理连通性
     * @param proxyString 代理字符串
     * @param timeout 超时时间
     * @return 格式化的测试结果
     */
    public static String testProxyConnectivity(String proxyString, int timeout) {
        ProxyTester tester = new ProxyTester(timeout);
        List<ProxyTestResult> results = tester.testProxyList(proxyString);
        return tester.formatTestResults(results);
    }
    
    /**
     * 静态方法：使用默认超时时间测试代理连通性
     * @param proxyString 代理字符串
     * @return 格式化的测试结果
     */
    public static String testProxyConnectivity(String proxyString) {
        return testProxyConnectivity(proxyString, 10);
    }
}
