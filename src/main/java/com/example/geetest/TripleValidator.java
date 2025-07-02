package com.example.geetest;

import com.example.nativeutils.NativeLoader;
import org.json.JSONObject;

import java.io.IOException;

public class TripleValidator {
    static {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            // Windows
            try {
                NativeLoader.loadLibrariesFromJar("/native/win/x86_64",
                        "bili_ticket_gt_java.dll"
                );
                System.out.println("✅ Windows native library loaded successfully.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            // macOS判断aarch64或 intel x64?
            if(System.getProperty("os.arch").toLowerCase().contains("aarch64")) {
                // macOS aarch64
                try {
                    NativeLoader.loadLibrariesFromJar("native/macos/aarch64",
                            "libbili_ticket_gt_java.dylib"
                    );
                    System.out.println("✅ macOS aarch64 native library loaded successfully.");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (System.getProperty("os.arch").toLowerCase().contains("x86_64")) {
                // macOS x86_64
                try {
                    NativeLoader.loadLibrariesFromJar("native/macos/x86_64",
                            "libbili_ticket_gt_java.dylib"
                    );
                    System.out.println("✅ macOS x86_64 native library loaded successfully.");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            // Linux
            try {
                NativeLoader.loadLibrariesFromJar("native/linux/x86_64",
                        "libbili_ticket_gt_java.so"
                );
                System.out.println("✅ Linux native library loaded successfully.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
    public static native String simpleMatchRetry(String gt, String challenge);

    public static native String registerTest();

}
