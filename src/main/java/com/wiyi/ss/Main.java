package com.wiyi.ss;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption(null, "tickets_info_str", true, "Ticket information in string format.");
        options.addOption(null, "interval", true, "Interval time.");
        options.addOption(null, "mode", true, "Mode of operation.");
        options.addOption(null, "total_attempts", true, "Total number of attempts.");
        options.addOption(null, "endpoint_url", true, "endpoint_url.");
        options.addOption(null, "time_start", true, "Start time (optional)");
        options.addOption(null, "audio_path", true, "Path to audio file (optional).");
        options.addOption(null, "pushplusToken", true, "PushPlus token (optional).");
        options.addOption(null, "serverchanKey", true, "ServerChan key (optional).");
        options.addOption(null, "ntfy_url", true, "Ntfy server URL (optional). e.g., https://ntfy.sh/topic");
        options.addOption(null, "ntfy_username", true, "Ntfy username (optional). For authenticated ntfy servers.");
        options.addOption(null, "ntfy_password", true, "Ntfy password (optional). For authenticated ntfy servers.");
        options.addOption(null, "filename", true, "filename (optional).");
        options.addOption(null, "https_proxys", true, "like none,http://127.0.0.1:8080");
        options.addOption(null, "terminal_ui", true, "server name");
        options.addOption(null, "master", true, "master url, like http://127.0.0.1:7890");
        options.addOption(null, "self_ip", true, "the ip that master note can access, like 127.0.0.1");
        options.addOption(null, "port", true, "server port");
        options.addOption(null, "server_name", true, "server name");
        options.addOption(null, "share", false, "create a public link");

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            String command = cmd.getArgs().length > 0 ? cmd.getArgs()[0] : "";

            if ("buy".equals(command)) {
                logger.info("Starting in buy mode...");
                
                // Get tickets info either from string or file
                String ticketsInfoStr = null;
                if (cmd.hasOption("tickets_info_str")) {
                    ticketsInfoStr = cmd.getOptionValue("tickets_info_str");
                } else if (cmd.hasOption("filename")) {
                    String filename = cmd.getOptionValue("filename");
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filename));
                        ticketsInfoStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (java.io.IOException e) {
                        logger.error("Failed to read config file: {}", filename, e);
                        return;
                    }
                }
                
                if (ticketsInfoStr == null) {
                    logger.error("No ticket configuration provided. Use --tickets_info_str or --filename");
                    return;
                }
                
                String timeStart = cmd.getOptionValue("time_start", "");
                int interval = Integer.parseInt(cmd.getOptionValue("interval", "300"));
                int mode = Integer.parseInt(cmd.getOptionValue("mode", "1"));
                int totalAttempts = Integer.parseInt(cmd.getOptionValue("total_attempts", "100"));
                String httpsProxys = cmd.getOptionValue("https_proxys", "none");
                String pushplusToken = cmd.getOptionValue("pushplusToken", "");
                String serverchanKey = cmd.getOptionValue("serverchanKey", "");
                String ntfyUrl = cmd.getOptionValue("ntfy_url", "");
                String ntfyUsername = cmd.getOptionValue("ntfy_username", "");
                String ntfyPassword = cmd.getOptionValue("ntfy_password", "");
                String audioPath = cmd.getOptionValue("audio_path", "");

                logger.info("Buy task configuration:");
                logger.info("- Time start: {}", timeStart);
                logger.info("- Interval: {} ms", interval);
                logger.info("- Mode: {}", mode);
                logger.info("- Total attempts: {}", totalAttempts);
                logger.info("- HTTPS proxy: {}", httpsProxys);

                com.wiyi.ss.task.BuyTask buyTask = new com.wiyi.ss.task.BuyTask(
                    ticketsInfoStr, timeStart, interval, mode, totalAttempts, 
                    httpsProxys, pushplusToken, serverchanKey, ntfyUrl, 
                    ntfyUsername, ntfyPassword, audioPath
                );
                
                buyTask.run(); // Run directly in main thread for command line mode
            } else {
                logger.info("Starting in ticker mode...");
                int port = Integer.parseInt(cmd.getOptionValue("port", "7860"));
                String gradioUrl = cmd.getOptionValue("gradio_url", "http://127.0.0.1:7860"); // Default Gradio URL
                com.wiyi.ss.web.WebServer webServer = new com.wiyi.ss.web.WebServer();
                webServer.start(port, gradioUrl);
            }
        } catch (ParseException e) {
            logger.error("Failed to parse command line properties", e);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("biliTickerBuy [buy|worker]", options);
        }
    }
}
