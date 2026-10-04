package com.clientFX;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class CtrlResults implements Initializable {

    @FXML
    private VBox rankingBox;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    // Muestra a los jugadores ordenados por puntos, alineados a la derecha
    public void showRanking(JSONObject scores) {
        rankingBox.getChildren().clear();
        if (scores == null) {
            return;
        }
        List<String> names = new ArrayList<>(scores.keySet());
        names.sort((a, b) -> Integer.compare(scores.optInt(b, 0), scores.optInt(a, 0)));
        for (String name : names) {
            Label l = new Label(name + " - Puntos: " + scores.optInt(name, 0));
            l.setMaxWidth(Double.MAX_VALUE);
            l.setAlignment(Pos.CENTER_RIGHT);
            rankingBox.getChildren().add(l);
        }
    }

    // Pide una partida nueva solo para este jugador y vuelve al tablero
    @FXML
    private void playAgain() {
        JSONObject obj = new JSONObject();
        obj.put("type", "restart");
        Main.wsClient.safeSend(obj.toString());
        Main.ctrlSockets.resetView();
        UtilsViews.setViewAnimating("ViewSockets");
    }
}
