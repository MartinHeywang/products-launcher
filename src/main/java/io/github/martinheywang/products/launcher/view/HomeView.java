package io.github.martinheywang.products.launcher.view;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class HomeView implements Initializable {

    @FXML
    private Label info;

    @FXML
    private Button btn;

    private static String appName = null, directoryName = null, classpathSeparator = null;

    private String urlReleases = "https://api.github.com/repos/MartinHeywang/PRODUCTS/releases";
    private JSONObject currentRelease, latestRelease;

    @Override
    public void initialize(URL arg0, ResourceBundle arg1) {
        // Depending on the OS, some values change
        if (SystemUtils.IS_OS_WINDOWS) {
            appName = "cmd.exe";
            directoryName = "/c";
            classpathSeparator = ";";
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            appName = "sh";
            directoryName = "-c";
            classpathSeparator = ":";
        } else if (SystemUtils.IS_OS_LINUX) {
            appName = "sh";
            directoryName = "-c";
            classpathSeparator = ":";
        } else {
            System.out.println("Votre système d'exploitation n'est pas supporté !");
            return;
        }
    }

    @FXML
    private void launch() {
        Task<Void> task = new Task<Void>() {
            public Void call() {
                try {
                    Platform.runLater(() -> {
                        info.setText("Recherche de mises à jour en cours...");
                        btn.setDisable(true);
                    });

                    Thread.sleep(200);
                    try {

                        currentRelease = getCurrentRelease();
                        latestRelease = getLatestRelease();

                        if (isNewVersionAvailable(currentRelease, latestRelease)) {
                            final String tagName = latestRelease.getString("tag_name");
                            Platform.runLater(() -> info.setText(
                                    "Une nouvelle version est disponible ! (" + tagName + ") Téléchargement... (1/2)"));
                            FileUtils.copyURLToFile(extractDownloadURLFromRelease(latestRelease),
                                    new File("./Products-" + latestRelease.getString("tag_name") + ".jar"));
                            Platform.runLater(() -> info.setText(
                                    "Une nouvelle version est disponible ! (" + tagName + ") Téléchargement... (2/2)"));
                            FileUtils.write(new File("./info.json"), latestRelease.toString(),
                                    Charset.defaultCharset());
                            currentRelease = latestRelease;
                            Platform.runLater(() -> {
                                info.setText("Le téléchargement est terminé ! Lancement...");
                            });
                            Thread.sleep(400);
                        }
                    } catch (IOException | JSONException e) {
                        Platform.runLater(() -> {
                            info.setText("Recherche de mises à jour échoué. Lancement...");
                        });
                        Thread.sleep(600);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                return null;
            }
        };

        Thread thread = new Thread(task);
        thread.start();

        task.setOnSucceeded(event -> {
            launchJar();
            ((Stage) btn.getScene().getWindow()).close();
        });

    }

    /**
     * Gets the current release. If none is found, download the latest info.json
     * available from internet.
     * 
     * @return
     */
    private JSONObject getCurrentRelease() throws IOException {
        JSONObject currentRelease = null;
        File info = new File("./info.json");
        FileInputStream input = null;
        try {
            if (!info.exists()) {
                // If the file doesn't exists, download it.
                FileUtils.copyURLToFile(new URL(urlReleases + "/latest"), new File("./info.json"));
            }

            // Get the file and parse it to a JSONObject
            // A Github release is a JSON Object.
            input = new FileInputStream(info);
            currentRelease = new JSONObject(new JSONTokener(input));
        } finally {
            try {
                if (input != null)
                    input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return currentRelease;
    }

    /**
     * Returns the latest release found in the GitHub API.
     * 
     * @return the latest release
     */
    private JSONObject getLatestRelease() throws IOException, JSONException {
        String stringRelease = getOutput(prepareProcessBuilder("curl " + urlReleases + "/latest").start());
        return new JSONObject(stringRelease);
    }

    /**
     * Prepares a process builder with the given command
     * 
     * @param command the command that will be executed
     * @return the builder, ready to execute the command
     */
    private ProcessBuilder prepareProcessBuilder(String command) {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command(appName, directoryName, command);
        return builder;
    }

    /**
     * Returns the output of the gitven process
     * 
     * @param process the process
     * @return the output of the process
     * @throws IOException if an error occurs reading the output.
     */
    private String getOutput(Process process) throws IOException {
        String output = "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line = "";
        while ((line = reader.readLine()) != null) {
            output += "\n" + line;
        }

        return output;
    }

    /**
     * Checks if a new version is available, using both the current and the latest
     * release.
     * 
     * @param currentRelease the downloaded release
     * @param latestRelease  the latest release available
     * @return true if the downloaded release is not the latest, true if there is no
     *         downloaded release, false if there is nothing to download.
     */
    private boolean isNewVersionAvailable(JSONObject currentRelease, JSONObject latestRelease) {
        // In most cases, if latestRelease is null, there is no internet connection, so
        // we can't check if a new version is available.
        if (latestRelease == null)
            return false;

        final LocalDateTime currentPublishTime = extractTimeFromRelease(currentRelease);
        final LocalDateTime latestPublishTime = extractTimeFromRelease(latestRelease);

        if (currentPublishTime.isBefore(latestPublishTime)) {
            // The downloaded release is not the newer
            return true;
        } else if (!new File("./Products-" + currentRelease.getString("tag_name") + ".jar").exists()) {
            // No release has been downloaded yet
            return true;
        } else {
            // Everything is in order !
            return false;
        }
    }

    /**
     * Extracts the publishid at value from the given release. The JSOn Object must
     * be a valid GitHub API release.
     * 
     * @param release a GitHub API release
     * @return the publishing time
     */
    private LocalDateTime extractTimeFromRelease(JSONObject release) {
        final String jsonValue = release.getString("published_at");
        return LocalDateTime.parse(jsonValue.substring(0, jsonValue.length() - 1));
    }

    /**
     * Extract the browser download url from the given release. The JSON Object must
     * be a valid GitHub API release.
     * 
     * @param release    a GitHub API release
     * @param assetIndex the index of the asset to download
     * @return the download url
     */
    private URL extractDownloadURLFromRelease(JSONObject release) {
        try {
            String jsonValue = "";
            int i = 0;
            while (!jsonValue.endsWith(".jar")) {
                i++;
                jsonValue = release.getJSONArray("assets").getJSONObject(i).getString("browser_download_url");
            }
            return new URL(jsonValue);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void launchJar() {
        final String fileName = "Products-" + currentRelease.getString("tag_name") + ".jar";
        try {
            if (new File(fileName).exists()) {
                // We can `java` because it exists (this program would not work)
                prepareProcessBuilder(
                        "java -cp " + fileName + classpathSeparator + "plugins/* io.github.martinheywang.products.Launcher")
                                .start();
            } else {
                Platform.runLater(() -> {
                    info.setText(
                            "Le jeu n'a pas été trouvé sur votre machine. Si vous lancez le jeu pour la première fois, assurez d'avoir accès à internet.");
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
