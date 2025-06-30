package com.wiyi.ss.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class QRCodeUtil {
    private static final Logger logger = LoggerFactory.getLogger(QRCodeUtil.class);

    public static void showQRCode(String text) {
        try {
            int width = 300;
            int height = 300;
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            
            // 将二维码渲染到JavaFX Image
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
            Image qrImage = new Image(new ByteArrayInputStream(out.toByteArray()));

            // 在JavaFX线程中显示窗口
            Platform.runLater(() -> showInJavaFXWindow(qrImage));

        } catch (Exception e) {
            logger.error("生成或显示二维码失败", e);
        }
    }

    private static void showInJavaFXWindow(Image qrImage) {
        try {
            Stage stage = new Stage();
            stage.setTitle("付款二维码");
            
            ImageView imageView = new ImageView(qrImage);
            StackPane pane = new StackPane(imageView);
            Scene scene = new Scene(pane);
            
            stage.setScene(scene);
            stage.setResizable(false);
            stage.show();
            stage.toFront(); // 确保窗口在最前
            logger.info("二维码已在JavaFX窗口中显示");
        } catch (Exception e) {
            logger.error("无法在JavaFX窗口中显示二维码", e);
        }
    }
}
