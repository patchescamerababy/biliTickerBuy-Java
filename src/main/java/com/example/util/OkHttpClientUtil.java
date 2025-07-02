package com.example.util;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public class OkHttpClientUtil {

    public static OkHttpClient buildClient() {
        try {
            // 创建一个不验证证书的 TrustManager
            final X509TrustManager trustAllCertificates = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    // 不做任何检查
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    // 不做任何检查
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };

            // 创建 SSLContext，使用 TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllCertificates}, null);

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS);
            OkHttpClient client = new OkHttpClient.Builder()
                    // 设置 SSL
                    .sslSocketFactory(sslContext.getSocketFactory(), trustAllCertificates)
                    .hostnameVerifier((hostname, session) -> {
                        return true;  // 不验证主机名
                    })
                    .connectTimeout(60, TimeUnit.SECONDS)  // 连接超时
                    .readTimeout(60, TimeUnit.SECONDS)     // 读取超时
                    .writeTimeout(60, TimeUnit.SECONDS)    // 写入超时
                    .build();
            return client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static OkHttpClient buildClient(String currentProxy) {
        try {
            // 创建一个不验证证书的 TrustManager
            final X509TrustManager trustAllCertificates = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    // 不做任何检查
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    // 不做任何检查
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };

            // 创建 SSLContext，使用 TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllCertificates}, null);

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS);

//            String currentProxy = proxyList.get(nowProxyIdx);
            Proxy proxy = parseProxy(currentProxy);

            OkHttpClient client = new OkHttpClient.Builder()
                    // 设置 SSL
                    .sslSocketFactory(sslContext.getSocketFactory(), trustAllCertificates)
                    .hostnameVerifier((hostname, session) -> {
                        return true;  // 不验证主机名
                    })
                    .connectTimeout(60, TimeUnit.SECONDS)  // 连接超时
                    .readTimeout(60, TimeUnit.SECONDS)     // 读取超时
                    .writeTimeout(60, TimeUnit.SECONDS)    // 写入超时
                    .proxy(proxy)
                    .build();
            return client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 解析代理字符串，支持：
     *  - socks=host:port 或 socks5=host:port
     *  - http=host:port、https=host:port
     *  - 纯 host:port（默认 HTTP，端口若缺省则用 80）
     *
     * @param proxyStr  原始代理配置字符串
     * @return          java.net.Proxy 对象（Type 为 HTTP 或 SOCKS）
     */
    public static Proxy parseProxy(String proxyStr) {
        String s = proxyStr.trim();
        if ("none".equalsIgnoreCase(s)) {
            return Proxy.NO_PROXY;
        }

        // 去掉协议前缀（http://、https://、socks://、socks5://） 
        s = s.replaceFirst("(?i)^(http|https|socks5?)://", "");

        // 去掉用户认证信息
        int at = s.lastIndexOf('@');
        if (at >= 0) {
            s = s.substring(at + 1);
        }

        // 默认 HTTP
        Proxy.Type type = Proxy.Type.HTTP;

        // 检查多协议条目形式：socks=...;http=...;...
        if (s.contains("=") && s.contains(";")) {
            // 以分号拆分，优先找 socks= 或 socks5=
            for (String entry : s.split(";")) {
                String e = entry.trim().toLowerCase();
                if (e.startsWith("socks5=") || e.startsWith("socks=")) {
                    type = Proxy.Type.SOCKS;
                    s = entry.substring(entry.indexOf('=') + 1);
                    break;
                } else if (e.startsWith("http=")) {
                    // 后续若无 socks，才处理 http=
                    s = entry.substring(entry.indexOf('=') + 1);
                    type = Proxy.Type.HTTP;
                }
            }
        } else {
            // 单一条目且以 socks= 或 socks5= 开头
            String low = s.toLowerCase();
            if (low.startsWith("socks5=") || low.startsWith("socks=")) {
                type = Proxy.Type.SOCKS;
                s = s.substring(s.indexOf('=') + 1);
            }
        }

        // 拆分 host:port
        String host;
        int port = (type == Proxy.Type.SOCKS ? 1080 : 80);
        if (s.contains(":")) {
            String[] hp = s.split(":", 2);
            host = hp[0];
            try {
                port = Integer.parseInt(hp[1].replaceAll("/.*$", ""));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("无效的端口号: " + hp[1], ex);
            }
        } else {
            host = s;
        }

        return new Proxy(type, new InetSocketAddress(host, port));
    }
}
