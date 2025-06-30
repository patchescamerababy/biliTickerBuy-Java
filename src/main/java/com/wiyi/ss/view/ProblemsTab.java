package com.wiyi.ss.view;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class ProblemsTab {

    public static VBox create() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        Label experienceLabel = new Label(
            "> 分享一下经验\n" +
            "- 抢票前，不要去提前抢还没有发售的票，会被b站封掉一段时间导致错过抢票的\n" +
            "- 使用不同的多个账号抢票\n" +
            "- 程序能保证用最快的速度发送订单请求，但是不保证这一次订单请求能够成功。所以不要完全依靠程序\n" +
            "- 现在各个平台抢票和秒杀机制都是进抽签池抽签，网速快发请求多快在拥挤的时候基本上没有效果\n" +
            "此时就要看你有没有足够的设备和账号来提高中签率\n" +
            "- 欢迎前往 [discussions](https://github.com/mikumifa/biliTickerBuy/discussions) 分享你的经验"
        );

        Label linksLabel = new Label(
            "- 项目地址：[mikumifa/biliTickerBuy](https://github.com/mikumifa/biliTickerBuy)\n" +
            "- 讨论区：[Discussions](https://github.com/mikumifa/biliTickerBuy/discussions)\n" +
            "- 问题反馈：[Issues](https://github.com/mikumifa/biliTickerBuy/issues)\n" +
            "  - [漏洞反馈](https://github.com/mikumifa/biliTickerBuy/issues/new?assignees=&labels=bug%3F&projects=&template=bug-report.yml&title=%5BBug%5D%3A+)\n" +
            "  - [需求建议](https://github.com/mikumifa/biliTickerBuy/issues/new?assignees=&labels=enhancement&projects=&template=feature-request.yml&title=%5BFeature%5D%3A+)\n" +
            "- 文档：[Wiki](https://github.com/mikumifa/biliTickerBuy/wiki)\n" +
            "  - [抢票说明书](https://github.com/mikumifa/biliTickerBuy/wiki/%E6%8A%A2%E7%A5%A8%E8%AF%B4%E6%98%8E)"
        );
        
        // For clickable links, one would typically use Hyperlink controls or a WebView
        // For simplicity, we are using Labels here.

        layout.getChildren().addAll(experienceLabel, linksLabel);

        return layout;
    }
}
