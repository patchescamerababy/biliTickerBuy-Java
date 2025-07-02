package com.example.nativeutils;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** 动态展开并清理 DLL（无需在 main 里额外代码） */
public final class NativeLoader {

    private static final Path NATIVE_ROOT;
    private static final List<Path> LOADED = new ArrayList<>();

    /* ---------- 目录初始化 ---------- */
    static {
        try {
            NATIVE_ROOT = Files.createDirectories(
                    Paths.get(System.getProperty("java.io.tmpdir"), "native"));
        } catch (IOException ex) {
            throw new ExceptionInInitializerError("无法创建临时目录: " + ex);
        }
    }

    private NativeLoader() {}

    /* ---------- 对外 API ---------- */

    /** 解压并加载单个 DLL */
    public static void loadLibraryFromJar(String resPath) throws IOException {
        Path dll = extract(resPath);
        LOADED.add(dll);
        System.load(dll.toString());              // 真正加载
    }

    /** 一次加载多 DLL */
    public static void loadLibrariesFromJar(String dir, String... libs) throws IOException {
        for (String n : libs)
            loadLibraryFromJar(dir.endsWith("/") ? dir + n : dir + "/" + n);
    }

    /* ---------- 内部实现 ---------- */

    /** 解压资源到固定目录，文件名保持不变 */
    private static Path extract(String resPath) throws IOException {
        String name = Paths.get(resPath).getFileName().toString();
        Path dst = NATIVE_ROOT.resolve(name);
        try (InputStream in = NativeLoader.class.getResourceAsStream(resPath)) {
            if (in == null) throw new FileNotFoundException(resPath);
            Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
        }
        dst.toFile().deleteOnExit();              // 兜底
        return dst;
    }
}
