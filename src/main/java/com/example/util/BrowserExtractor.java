package com.example.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class BrowserExtractor {
    
    private static final String TEMP_DIR_PREFIX = "biliTickerBuy_browser_";
    
    /**
     * 从jar中提取浏览器文件到临时目录
     * @return 提取后的chrome.exe路径，如果失败返回null
     */
    public static String extractBrowserFromJar() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String browserPath = null;
            
            if (os.contains("win")) {
                browserPath = extractWindowsBrowser();
            } else if (os.contains("mac")) {
                browserPath = extractMacBrowser();
            } else {
                browserPath = extractLinuxBrowser();
            }
            
            if (browserPath != null) {
                System.out.println("✅ 浏览器提取成功: " + browserPath);
            } else {
                System.out.println("⚠️  未找到jar内打包的浏览器文件");
            }
            
            return browserPath;
            
        } catch (Exception e) {
            System.err.println("❌ 浏览器提取失败: " + e.getMessage());
            return null;
        }
    }
    
    private static String extractWindowsBrowser() throws IOException {
        // 优先提取chrome-win
        String chromePath = extractResourceFolder("ms-playwright/chromium-1091/chrome-win/chrome-win/", "chrome.exe");
        if (chromePath != null) {
            return chromePath;
        }
        
        // 备选webkit-1944
        String webkitPath = extractResourceFolder("/webkit-1944/", "Playwright.exe");
        return webkitPath;
    }
    
    private static String extractMacBrowser() throws IOException {
        return extractResourceFolder("/chrome-mac/", "Chromium.app/Contents/MacOS/Chromium");
    }
    
    private static String extractLinuxBrowser() throws IOException {
        return extractResourceFolder("/chrome-linux/", "chrome");
    }
    
    /**
     * 提取指定资源文件夹到临时目录
     * @param resourcePath jar内资源路径，如"/chrome-win/"
     * @param executableFile 可执行文件相对路径，如"chrome.exe"
     * @return 提取后的可执行文件完整路径
     */
    private static String extractResourceFolder(String resourcePath, String executableFile) throws IOException {
        // 创建临时目录
        Path tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
        tempDir.toFile().deleteOnExit();
        
        // 获取当前jar文件路径
        String jarPath = BrowserExtractor.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
        
        if (jarPath.endsWith(".jar")) {
            // 从jar文件中提取
            return extractFromJarFile(jarPath, resourcePath, tempDir, executableFile);
        } else {
            // 开发环境，直接从文件系统读取
            return extractFromFileSystem(resourcePath, tempDir, executableFile);
        }
    }
    
    private static String extractFromJarFile(String jarPath, String resourcePath, 
                                           Path tempDir, String executableFile) throws IOException {
        boolean found = false;
        
        try (JarInputStream jarStream = new JarInputStream(new FileInputStream(jarPath))) {
            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                if (entry.getName().startsWith(resourcePath.substring(1))) { // 去掉开头的"/"
                    String relativePath = entry.getName().substring(resourcePath.length() - 1);
                    Path targetPath = tempDir.resolve(relativePath);
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        try (OutputStream out = Files.newOutputStream(targetPath)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = jarStream.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                        // 设置可执行权限
                        targetPath.toFile().setExecutable(true);
                        found = true;
                    }
                }
            }
        }
        
        if (found) {
            Path executablePath = tempDir.resolve(executableFile);
            if (Files.exists(executablePath)) {
                return executablePath.toAbsolutePath().toString();
            }
        }
        
        return null;
    }
    
    private static String extractFromFileSystem(String resourcePath, Path tempDir, 
                                              String executableFile) throws IOException {
        // 开发环境，从src/main/resources读取
        String basePath = System.getProperty("user.dir") + "/src/main/resources";
        Path sourcePath = Paths.get(basePath + resourcePath);
        
        if (!Files.exists(sourcePath)) {
            return null;
        }
        
        // 复制整个文件夹
        copyDirectory(sourcePath, tempDir.resolve(resourcePath.substring(1)));
        
        Path executablePath = tempDir.resolve(resourcePath.substring(1)).resolve(executableFile);
        if (Files.exists(executablePath)) {
            executablePath.toFile().setExecutable(true);
            return executablePath.toAbsolutePath().toString();
        }
        
        return null;
    }
    
    private static void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                Path targetEntry = target.resolve(entry.getFileName());
                if (Files.isDirectory(entry)) {
                    copyDirectory(entry, targetEntry);
                } else {
                    Files.copy(entry, targetEntry, StandardCopyOption.REPLACE_EXISTING);
                    targetEntry.toFile().setExecutable(true);
                }
            }
        }
    }
    
    /**
     * 清理临时文件
     */
    public static void cleanup() {
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir, TEMP_DIR_PREFIX + "*")) {
                for (Path path : stream) {
                    deleteDirectory(path);
                }
            }
        } catch (Exception e) {
            // 忽略清理错误
        }
    }
    
    private static void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteDirectory(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
