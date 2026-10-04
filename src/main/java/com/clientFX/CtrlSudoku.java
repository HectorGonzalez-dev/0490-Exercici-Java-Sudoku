package com.clientFX;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class CtrlSudoku implements Initializable {

    @FXML
    private Label lblPlayer;

    @FXML
    private Label lblStatus;

    @FXML
    private GridPane sudokuGrid;

    @FXML
    private GridPane numberBox;

    @FXML
    private VBox playersBox;

    private int selectedNumber = 1;
    private final Button[] numButtons = new Button[9];
    private final Button[][] cells = new Button[9][9];

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Botones 1-9 arriba a la derecha, en cuadrícula de 3x3
        for (int n = 1; n <= 9; n++) {
            Button b = new Button(String.valueOf(n));
            b.setPrefSize(50, 50);
            final int value = n;
            b.setOnAction(e -> selectNumber(value));
            numButtons[n - 1] = b;
            numberBox.add(b, (n - 1) % 3, (n - 1) / 3);
        }
        selectNumber(1);

        // Cuadrícula 9x9 a la izquierda, celdas cuadradas sin esquinas redondas
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                Button b = new Button("");
                b.setPrefSize(45, 45);
                b.setMinSize(45, 45);
                b.setMaxSize(45, 45);
                b.setStyle(cellStyle("white"));
                final int row = r;
                final int col = c;
                b.setOnAction(e -> onCellClicked(row, col));
                cells[r][c] = b;
                sudokuGrid.add(b, c, r);
            }
        }
    }

    // Estilo cuadrado para las celdas
    private String cellStyle(String color) {
        return "-fx-font-size: 18; -fx-background-radius: 0; -fx-border-radius: 0;"
                + " -fx-border-color: #999999; -fx-border-width: 0.5;"
                + " -fx-background-color: " + color + ";";
    }

    public void setPlayerName(String name) {
        lblPlayer.setText("[TU] " + name);
    }

    // Marca el número elegido y desmarca los demás
    private void selectNumber(int n) {
        selectedNumber = n;
        for (int i = 0; i < 9; i++) {
            if (i == n - 1) {
                numButtons[i].setStyle("-fx-background-radius: 0; -fx-background-color: lightblue; -fx-font-weight: bold;");
            } else {
                numButtons[i].setStyle("-fx-background-radius: 0;");
            }
        }
    }

    // Envía la jugada al servidor con el número elegido
    private void onCellClicked(int row, int col) {
        JSONObject obj = new JSONObject();
        obj.put("type", "play");
        obj.put("row", row);
        obj.put("col", col);
        obj.put("value", selectedNumber);
        Main.wsClient.safeSend(obj.toString());
    }

    // Main llama a este método cuando llega un mensaje del servidor
    public void receiveMessage(JSONObject msg) {
        String type = msg.optString("type", "");
        if (type.equals("state")) {
            updateBoard(msg);
        } else if (type.equals("error")) {
            lblStatus.setText(msg.optString("message", "Error"));
        }
    }

    // Dibuja el tablero y el ranking con los datos del servidor
    private void updateBoard(JSONObject msg) {
        JSONArray arrBoard = msg.optJSONArray("board");
        JSONArray arrLocked = msg.optJSONArray("locked");
        JSONArray arrGivens = msg.optJSONArray("givens");
        JSONObject scores = msg.optJSONObject("scores");
        if (arrBoard == null || arrLocked == null) {
            return;
        }

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int i = r * 9 + c;
                int value = arrBoard.optInt(i, 0);
                boolean locked = arrLocked.optBoolean(i, false);
                boolean given;
                if (arrGivens != null && arrGivens.length() > i) {
                    given = arrGivens.optBoolean(i, false);
                } else {
                    given = locked && value != 0;
                }
                Button b = cells[r][c];
                b.setText(value == 0 ? "" : String.valueOf(value));
                if (given) {
                    // Casilla inicial: gris y bloqueada
                    b.setDisable(true);
                    b.setStyle(cellStyle("lightgray")
                            + " -fx-font-weight: bold; -fx-text-fill: black; -fx-opacity: 1;");
                } else if (locked) {
                    // Acierto (también de otros jugadores): verde y bloqueada
                    b.setDisable(true);
                    b.setStyle(cellStyle("lightgreen")
                            + " -fx-font-weight: bold; -fx-text-fill: black; -fx-opacity: 1;");
                } else {
                    b.setDisable(false);
                    b.setStyle(cellStyle("white"));
                }
            }
        }

        // Ranking debajo de los números, ordenado por puntos
        showRanking(scores);
        lblStatus.setText("");
    }

    // Muestra a los jugadores ordenados por puntos, alineados a la derecha
    private void showRanking(JSONObject scores) {
        playersBox.getChildren().clear();
        if (scores == null) {
            return;
        }
        List<String> names = new ArrayList<>(scores.keySet());
        names.sort((a, b) -> Integer.compare(scores.optInt(b, 0), scores.optInt(a, 0)));
        for (String name : names) {
            Label l = new Label(name + " - Puntos: " + scores.optInt(name, 0));
            l.setMaxWidth(Double.MAX_VALUE);
            l.setAlignment(Pos.CENTER_RIGHT);
            playersBox.getChildren().add(l);
        }
    }

    // Limpia el mensaje de estado
    public void resetView() {
        lblStatus.setText("");
    }
}
