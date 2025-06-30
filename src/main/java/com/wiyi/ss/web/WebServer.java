package com.wiyi.ss.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.wiyi.ss.task.BuyTask;
import com.wiyi.ss.util.BiliRequest;

import okhttp3.Response;

public class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
    private static BiliRequest biliRequest;
    private static final Object loginMonitor = new Object();
    private static String newUsername = null;

    public void start(int port, String gradioUrl) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new FileHandler("/web/index.html"));
            server.createContext("/api/ticket-info", new TicketInfoHandler());
            server.createContext("/api/buyers", new BuyerListHandler());
            server.createContext("/api/addresses", new AddressListHandler());
            server.createContext("/api/generate-config", new GenerateConfigHandler());
            server.createContext("/api/start-buying", new StartBuyingHandler());
            server.createContext("/api/test-captcha", new TestCaptchaHandler());
            server.createContext("/api/logs", new LogsHandler());
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/check-login-status", new CheckLoginStatusHandler());
            // Add more handlers for other static files (css, js) if needed

            server.setExecutor(null); // creates a default executor
            server.start();
            logger.info("Server started on port {}", port);
        } catch (IOException e) {
            logger.error("Failed to start web server", e);
        }
    }

    static class FileHandler implements HttpHandler {
        private final String filePath;

        public FileHandler(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                URL resource = getClass().getResource(filePath);
                if (resource == null) {
                    String response = "404 (Not Found)";
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }

                File file = new File(resource.toURI());
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            } catch (Exception e) {
                logger.error("Error handling file request", e);
                String response = "500 (Internal Server Error)";
                exchange.sendResponseHeaders(500, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    static class TicketInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    // Read the request body to get the URL
                    String body;
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                        body = br.lines().collect(Collectors.joining(System.lineSeparator()));
                    }
                    
                    JSONObject requestJson = new JSONObject(body);
                    String url = requestJson.getString("url");

                    if (url.isEmpty()) {
                        sendResponse(exchange, 400, "URL is missing");
                        return;
                    }

                    // Extract project ID from URL
                    String projectId = url.substring(url.indexOf("id=") + 3);

                    if (biliRequest == null) {
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("status", "error");
                        errorResponse.put("message", "Not logged in.");
                        sendResponse(exchange, 401, errorResponse.toString());
                        return;
                    }

                    String apiUrl = "https://show.bilibili.com/api/ticket/project/getV2?version=134&id=" + projectId + "&project_id=" + projectId;
                    Response response = biliRequest.get(apiUrl);
                    String responseBody = response.body().string();
                    
                    sendResponse(exchange, 200, responseBody);

                } catch (Exception e) {
                    logger.error("Error handling ticket info request", e);
                    sendResponse(exchange, 500, "Internal Server Error");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static class BuyerListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    if (biliRequest == null) {
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("status", "error");
                        errorResponse.put("message", "Not logged in.");
                        sendResponse(exchange, 401, errorResponse.toString());
                        return;
                    }
                    String projectId = "0"; // This might need to be passed as a parameter
                    String apiUrl = "https://show.bilibili.com/api/ticket/buyer/list?is_default&projectId=" + projectId;
                    Response response = biliRequest.get(apiUrl);
                    String responseBody = response.body().string();
                    sendResponse(exchange, 200, responseBody);
                } catch (Exception e) {
                    logger.error("Error handling buyer list request", e);
                    sendResponse(exchange, 500, "Internal Server Error");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static class AddressListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    if (biliRequest == null) {
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("status", "error");
                        errorResponse.put("message", "Not logged in.");
                        sendResponse(exchange, 401, errorResponse.toString());
                        return;
                    }
                    String apiUrl = "https://show.bilibili.com/api/ticket/addr/list";
                    Response response = biliRequest.get(apiUrl);
                    String responseBody = response.body().string();
                    sendResponse(exchange, 200, responseBody);
                } catch (Exception e) {
                    logger.error("Error handling address list request", e);
                    sendResponse(exchange, 500, "Internal Server Error");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static class GenerateConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (biliRequest == null) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Not logged in.");
                sendResponse(exchange, 401, errorResponse.toString());
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body;
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                        body = br.lines().collect(Collectors.joining(System.lineSeparator()));
                    }
                    JSONObject requestJson = new JSONObject(body);

                    // Fetch all necessary data again to ensure it's up-to-date
                    // This is a simplified example. A real implementation would cache this data.
                    Response ticketInfoResponse = biliRequest.get("https://show.bilibili.com/api/ticket/project/getV2?version=134&id=" + requestJson.getString("projectId") + "&project_id=" + requestJson.getString("projectId"));
                    JSONObject ticketInfo = new JSONObject(ticketInfoResponse.body().string()).getJSONObject("data");

                    Response buyerListResponse = biliRequest.get("https://show.bilibili.com/api/ticket/buyer/list?is_default&projectId=" + requestJson.getString("projectId"));
                    JSONObject buyerList = new JSONObject(buyerListResponse.body().string()).getJSONObject("data");

                    Response addressListResponse = biliRequest.get("https://show.bilibili.com/api/ticket/addr/list");
                    JSONObject addressList = new JSONObject(addressListResponse.body().string()).getJSONObject("data");
                    
                    // Find the selected items
                    JSONObject selectedTicket = null;
                    for (Object screenObj : ticketInfo.getJSONArray("screen_list")) {
                        JSONObject screen = (JSONObject) screenObj;
                        for (Object ticketObj : screen.getJSONArray("ticket_list")) {
                            JSONObject ticket = (JSONObject) ticketObj;
                            if (ticket.getInt("id") == requestJson.getInt("ticketId")) {
                                selectedTicket = ticket;
                                break;
                            }
                        }
                        if (selectedTicket != null) break;
                    }

                    JSONObject selectedBuyer = null;
                    for (Object buyerObj : buyerList.getJSONArray("list")) {
                        JSONObject buyer = (JSONObject) buyerObj;
                        if (buyer.getInt("id") == requestJson.getInt("buyerId")) {
                            selectedBuyer = buyer;
                            break;
                        }
                    }

                    JSONObject selectedAddress = null;
                    for (Object addrObj : addressList.getJSONArray("addr_list")) {
                        JSONObject addr = (JSONObject) addrObj;
                        if (addr.getInt("id") == requestJson.getInt("addressId")) {
                            selectedAddress = addr;
                            break;
                        }
                    }
                    
                    // Construct the final config JSON
                    JSONObject config = new JSONObject();
                    config.put("username", biliRequest.getUserNickname());
                    config.put("count", requestJson.getJSONArray("people").length());
                    config.put("screen_id", selectedTicket.getInt("screen_id"));
                    config.put("project_id", ticketInfo.getInt("id"));
                    config.put("sku_id", selectedTicket.getInt("id"));
                    config.put("order_type", 1);
                    config.put("pay_money", selectedTicket.getInt("price") * requestJson.getJSONArray("people").length());
                    config.put("buyer_info", new org.json.JSONArray(requestJson.getJSONArray("people").toString()));
                    config.put("buyer", selectedBuyer.getString("name"));
                    config.put("tel", selectedBuyer.getString("tel"));
                    
                    JSONObject deliverInfo = new JSONObject();
                    deliverInfo.put("name", selectedAddress.getString("name"));
                    deliverInfo.put("tel", selectedAddress.getString("phone"));
                    deliverInfo.put("addr_id", selectedAddress.getInt("id"));
                    deliverInfo.put("addr", selectedAddress.getString("prov") + selectedAddress.getString("city") + selectedAddress.getString("area") + selectedAddress.getString("addr"));
                    config.put("deliver_info", deliverInfo);

                    // Convert cookies to JSONArray format like Python
                    org.json.JSONArray cookiesArray = new org.json.JSONArray();
                    java.util.Map<String, String> cookiesMap = biliRequest.getCookies();
                    for (java.util.Map.Entry<String, String> entry : cookiesMap.entrySet()) {
                        org.json.JSONObject cookieObj = new org.json.JSONObject();
                        cookieObj.put("name", entry.getKey());
                        cookieObj.put("value", entry.getValue());
                        cookiesArray.put(cookieObj);
                    }
                    config.put("cookies", cookiesArray);

                    sendResponse(exchange, 200, config.toString(4));

                } catch (Exception e) {
                    logger.error("Error handling generate config request", e);
                    sendResponse(exchange, 500, "Internal Server Error");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static class StartBuyingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body;
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                        body = br.lines().collect(Collectors.joining(System.lineSeparator()));
                    }
                    JSONObject requestJson = new JSONObject(body);

                    String startTime = requestJson.getString("startTime");
                    int interval = requestJson.getInt("interval");
                    int mode = "infinite".equals(requestJson.getString("mode")) ? 1 : 0;
                    
                    org.json.JSONArray configs = requestJson.getJSONArray("configs");
                    for (int i = 0; i < configs.length(); i++) {
                        String configStr = configs.getString(i);
                        // Assuming default values for totalAttempts, httpsProxys, pushplusToken, serverchanKey, ntfyUrl, ntfyUsername, ntfyPassword, audioPath
                        BuyTask task = new BuyTask(
                            configStr, startTime, interval, mode, 10,
                            "none", "", "", "", "", "", ""
                        );
                        new Thread(task).start();
                    }

                    JSONObject successResponse = new JSONObject();
                    successResponse.put("status", "success");
                    successResponse.put("message", configs.length() + " buying tasks started.");
                    sendResponse(exchange, 200, successResponse.toString());

                } catch (Exception e) {
                    logger.error("Error handling start buying request", e);
                    sendResponse(exchange, 500, "Internal Server Error");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static class TestCaptchaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (biliRequest == null) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Not logged in.");
                sendResponse(exchange, 401, errorResponse.toString());
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    // 1. Get gt and challenge
                    String registerUrl = "https://api.bilibili.com/x/gaia-vgate/v1/register?_="+System.currentTimeMillis();
                    Response registerResponse = biliRequest.get(registerUrl);
                    JSONObject registerResult = new JSONObject(registerResponse.body().string());
                    
                    String gt = registerResult.getJSONObject("data").getJSONObject("geetest").getString("gt");
                    String challenge = registerResult.getJSONObject("data").getJSONObject("geetest").getString("challenge");
                    String token = registerResult.getJSONObject("data").getString("token");

                    // 2. Validate
                    com.wiyi.ss.geetest.TripleValidator validator = new com.wiyi.ss.geetest.TripleValidator("geetest/model/triple.onnx");
                    String validate = validator.validate(gt, challenge);
                    String seccode = validate + "|jordan";

                    // 3. Send validation to Bilibili
                    String validateUrl = "https://api.bilibili.com/x/gaia-vgate/v1/validate";
                    okhttp3.FormBody.Builder formBodyBuilder = new okhttp3.FormBody.Builder();
                    formBodyBuilder.add("challenge", challenge);
                    formBodyBuilder.add("token", token);
                    formBodyBuilder.add("seccode", seccode);
                    formBodyBuilder.add("csrf", biliRequest.getCsrf());
                    formBodyBuilder.add("validate", validate);
                    okhttp3.RequestBody validateBody = formBodyBuilder.build();
                    
                    Response validateResponse = biliRequest.post(validateUrl, validateBody);
                    String validateResultStr = validateResponse.body().string();
                    
                    sendResponse(exchange, 200, validateResultStr);

                } catch (Exception e) {
                    logger.error("Error handling captcha test request", e);
                    sendResponse(exchange, 500, "Internal Server Error");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    File logFile = new File("logs/app.log");
                    String logContent;
                    if (logFile.exists()) {
                        // Read last 1000 lines
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                            String[] lines = reader.lines().toArray(String[]::new);
                            int start = Math.max(0, lines.length - 1000);
                            StringBuilder sb = new StringBuilder();
                            for (int i = start; i < lines.length; i++) {
                                sb.append(lines[i]).append("\n");
                            }
                            logContent = sb.toString();
                        }
                    } else {
                        logContent = "No logs found.";
                    }
                    sendResponse(exchange, 200, logContent, "text/plain");
                } catch (Exception e) {
                    logger.error("Error handling logs request", e);
                    sendResponse(exchange, 500, "Internal Server Error", "text/plain");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                    String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                    
                    // Read the request body
                    InputStream in = exchange.getRequestBody();
                    byte[] body = new byte[in.available()];
                    in.read(body);
                    in.close();
                    
                    // Find the start of the file content
                    String bodyStr = new String(body, StandardCharsets.UTF_8);
                    String fileHeader = "Content-Disposition: form-data; name=\"cookieFile\"; filename=\"cookies.json\"";
                    int fileStart = bodyStr.indexOf(fileHeader);
                    if (fileStart == -1) {
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("status", "error");
                        errorResponse.put("message", "No cookie file uploaded.");
                        sendResponse(exchange, 400, errorResponse.toString());
                        return;
                    }
                    
                    // Find the end of the file content
                    int fileEnd = bodyStr.indexOf("--" + boundary, fileStart);
                    
                    // Extract the file content
                    String fileContent = bodyStr.substring(bodyStr.indexOf("\r\n\r\n", fileStart) + 4, fileEnd - 2);

                    // Save the cookie file
                    String cookiePath = "cookies.json";
                    try (FileOutputStream out = new FileOutputStream(cookiePath)) {
                        out.write(fileContent.getBytes(StandardCharsets.UTF_8));
                    }

                    // Initialize BiliRequest and get username
                    biliRequest = new BiliRequest(new org.json.JSONArray(cookiePath), "none");
                    String username = biliRequest.getUserNickname();

                    JSONObject jsonResponse = new JSONObject();
                    jsonResponse.put("status", "success");
                    jsonResponse.put("username", username);
                    sendResponse(exchange, 200, jsonResponse.toString());

                    // Notify waiting long-poll requests
                    synchronized (loginMonitor) {
                        newUsername = username;
                        loginMonitor.notifyAll();
                    }

                } catch (Exception e) {
                    logger.error("Error handling login request", e);
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("status", "error");
                    errorResponse.put("message", "Internal Server Error");
                    sendResponse(exchange, 500, errorResponse.toString());
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static class CheckLoginStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                synchronized (loginMonitor) {
                    // Wait for a login event, with a 30-second timeout
                    loginMonitor.wait(30000);
                }

                if (newUsername != null) {
                    JSONObject jsonResponse = new JSONObject();
                    jsonResponse.put("status", "updated");
                    jsonResponse.put("username", newUsername);
                    sendResponse(exchange, 200, jsonResponse.toString());
                    newUsername = null; // Reset for the next login
                } else {
                    // Timeout occurred, no new login
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(204, -1); // No Content, contentLen must be -1
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("CheckLoginStatusHandler interrupted", e);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Internal Server Error");
                sendResponse(exchange, 500, errorResponse.toString());
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
