package com.clientFX;

import org.json.JSONObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static UtilsWS wsClient;

    public static CtrlConfig ctrlConfig;
    public static CtrlSudoku ctrlSockets;
    public static CtrlResults ctrlResults;

    private static int connectAttempt = 0;
    private static final String CONNECTING_TEXT = "Connecting ...";

    public static void main(String[] args) {
        // Inicia la aplicación JavaFX
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 700;
        final int windowHeight = 650;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewSockets", "/assets/viewSockets.fxml");
        UtilsViews.addView(getClass(), "ViewResults", "/assets/viewResults.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlSockets = (CtrlSudoku) UtilsViews.getController("ViewSockets");
        ctrlResults = (CtrlResults) UtilsViews.getController("ViewResults");

        Scene scene = new Scene(UtilsViews.parentContainer);

        stage.setScene(scene);
        stage.setTitle("Sudoku");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Añade el icono solo si no es Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }
    }


    @Override
    public void stop() {
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(0); // Mata todos los servicios en segundo plano
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText(CONNECTING_TEXT);

        String name = ctrlConfig.txtName.getText().trim();
        // El nombre no puede estar vacío
        if (name.isEmpty()) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText("Enter a name");
            return;
        }
        ctrlSockets.setPlayerName(name);

        final int attempt = ++connectAttempt;

        pauseDuring(1500, () -> { // Espera un poco para mostrar el mensaje ...
            if (attempt != connectAttempt) {
                return; // Un intento más nuevo dejó obsoleto a este
            }

            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            String url = protocol + "://" + host + ":" + port;
            wsClient = UtilsWS.getSharedInstance(url);

            wsClient.onOpen((msg) -> {
                JSONObject o = new JSONObject();
                o.put("type", "register");
                o.put("name", name);
                wsClient.safeSend(o.toString());
            });

            // Platform.runLater ejecuta el código en el hilo de la interfaz,
            // para evitar problemas de concurrencia con JavaFX
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onClose((response) -> { Platform.runLater(() -> { wsClose(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });

            wsClient.freshConnect();

            pauseDuring(5000, () -> {
                if (attempt != connectAttempt) {
                    return;
                }
                if (!"ViewSockets".equals(UtilsViews.getActiveView())
                        && !"ViewResults".equals(UtilsViews.getActiveView())
                        && CONNECTING_TEXT.equals(ctrlConfig.txtMessage.getText())
                        && (wsClient == null || !wsClient.isOpen())) {
                    ctrlConfig.txtMessage.setTextFill(Color.RED);
                    ctrlConfig.txtMessage.setText("Connection closed, try again");
                }
            });
        });
    }

    private static void wsMessage(String response) {
        JSONObject msgObj = new JSONObject(response);
        String type = msgObj.optString("type", "");

        if (type.equals("error")) {
            // Si aún no entró al juego, muestra el error en la configuración
            if (!"ViewSockets".equals(UtilsViews.getActiveView())
                    && !"ViewResults".equals(UtilsViews.getActiveView())) {
                String err = msgObj.optString("message", "Error");
                ctrlConfig.txtMessage.setTextFill(Color.RED);
                ctrlConfig.txtMessage.setText(err);
                return;
            }
            ctrlSockets.receiveMessage(msgObj);
            return;
        }

        if (type.equals("state")) {
            boolean finished = msgObj.optBoolean("finished", false);
            if (finished) {
                ctrlSockets.receiveMessage(msgObj);
                ctrlResults.showRanking(msgObj.optJSONObject("scores"));
                if (!"ViewResults".equals(UtilsViews.getActiveView())) {
                    UtilsViews.setViewAnimating("ViewResults");
                }
            } else {
                String active = UtilsViews.getActiveView();
                if ("ViewResults".equals(active)) {
                    // Se queda en resultados hasta que el jugador pulse Play Again
                    return;
                }
                if (!"ViewSockets".equals(active)) {
                    UtilsViews.setViewAnimating("ViewSockets");
                }
                ctrlSockets.receiveMessage(msgObj);
            }
        }
    }

    private static void wsError(String response) {

        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }

    private static void wsClose(String response) {
        if (!"ViewSockets".equals(UtilsViews.getActiveView())
                && !"ViewResults".equals(UtilsViews.getActiveView())
                && CONNECTING_TEXT.equals(ctrlConfig.txtMessage.getText())) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText("Connection closed, try again");
        }
    }
}
