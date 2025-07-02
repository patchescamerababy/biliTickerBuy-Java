package com.example.util;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class AudioUtil extends Notifier {

    private final String audioPath;

    public AudioUtil(String audioPath, String title, String content, long intervalSeconds, long durationMinutes) {
        super(title, content, intervalSeconds, durationMinutes);
        this.audioPath = audioPath;
    }

    @Override
    public void sendMessage(String title, String message) throws Exception {
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            throw new IOException("音频文件不存在: " + audioPath);
        }

        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile)) {
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
            // 等待音频播放完成
            Thread.sleep(clip.getMicrosecondLength() / 1000);
            clip.close();
        } catch (UnsupportedAudioFileException | LineUnavailableException | InterruptedException e) {
            throw new Exception("音频播放失败", e);
        }
    }
}
