package io.github.martinheywang.products.launcher;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class Main extends Application {

    public static void main(String[] args) {
        Font.loadFont(Main.class.getResourceAsStream("/font/Products.ttf"), 12);
        launch(args);
    }

    private static Scene loadScene() throws IOException {
        final FXMLLoader loader = new FXMLLoader();
        loader.setLocation(Main.class.getResource("/fxml/Home.fxml"));

        final Parent root = loader.load();
        final Scene scene = new Scene(root);

        return scene;
    }

    @Override
    public void start(Stage stage) throws Exception {
        stage.setScene(loadScene());
        stage.setTitle("Lanceur de PRODUCTS.");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/Icon.png")));

        stage.show();
    }
}
