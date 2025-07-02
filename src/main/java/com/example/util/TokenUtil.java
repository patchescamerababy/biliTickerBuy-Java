package com.example.util;

import java.nio.ByteBuffer;
import java.util.Base64;

public class TokenUtil {

    /**
     * 加密方法，对应Python中的encrypt函数
     * @param value a int value
     * @param encryptType 加密类型
     * @return 加密后的字符串
     */
    private static String encrypt(int value, String encryptType) {
        byte[] v1;
        String v2;

        switch (encryptType) {
            case "timestamp":
                v1 = ByteBuffer.allocate(5).putInt(1, value).array();
                v2 = Base64.getUrlEncoder().withoutPadding().encodeToString(v1);
                return v2.substring(1, 8);
            case "projectId":
                v1 = new byte[]{(byte) (value >> 16 & 0xFF), (byte) (value >> 8 & 0xFF), (byte) (value & 0xFF)};
                v2 = Base64.getUrlEncoder().withoutPadding().encodeToString(v1);
                return v2;
            case "screenId":
                v1 = ByteBuffer.allocate(4).putInt(value).array();
                v2 = Base64.getUrlEncoder().withoutPadding().encodeToString(v1);
                return v2.substring(1, 6);
            case "orderType":
                v1 = new byte[]{(byte) (value >> 8 & 0xFF), (byte) (value & 0xFF)};
                v2 = Base64.getUrlEncoder().withoutPadding().encodeToString(v1);
                return v2.substring(2, 3);
            case "count":
                v1 = new byte[]{(byte)value};
                v2 = Base64.getUrlEncoder().withoutPadding().encodeToString(v1);
                return v2;
            case "skuId":
                v1 = ByteBuffer.allocate(5).putInt(1, value).array();
                v2 = Base64.getUrlEncoder().withoutPadding().encodeToString(v1);
                return v2.substring(2, 7);
            default:
                return "";
        }
    }

    /**
     * 生成Token，对应Python中的generate_token函数
     */
    public static String generateToken(int projectId, int screenId, int orderType, int count, int skuId) {
        String p1 = "999999";
        String p2 = encrypt(projectId, "projectId");
        String p3 = encrypt(screenId, "screenId");
        String p4 = encrypt(orderType, "orderType");
        String p5 = encrypt(count, "count");
        String p6 = encrypt(skuId, "skuId");

        return "w" + p1 + "A" + p2 + "A" + p3 + p4 + "A" + p5 + p6 + ".";
    }
}
