package com.example.view;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class ProblemsTab {

    public static VBox create() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10));

        Label experienceLabel = new Label(
        );

        Label linksLabel = new Label(
        );

        layout.getChildren().addAll(experienceLabel, linksLabel);

        return layout;
    }
}
