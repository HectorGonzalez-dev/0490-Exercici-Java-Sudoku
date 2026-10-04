package com.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

// Estado de la partida: tablero, solución y puntos de los jugadores.
// Cada partida genera un sudoku nuevo y fácil a partir de una solución válida.
public class SudokuGame {

    // Casillas vacías por partida (pocas para que sea fácil de probar)
    private static final int HOLES = 10;

    // Solución válida de base: se mezcla para crear partidas nuevas
    private static final int[][] BASE = {
        {5, 3, 4, 6, 7, 8, 9, 1, 2},
        {6, 7, 2, 1, 9, 5, 3, 4, 8},
        {1, 9, 8, 3, 4, 2, 5, 6, 7},
        {8, 5, 9, 7, 6, 1, 4, 2, 3},
        {4, 2, 6, 8, 5, 3, 7, 9, 1},
        {7, 1, 3, 9, 2, 4, 8, 5, 6},
        {9, 6, 1, 5, 3, 7, 2, 8, 4},
        {2, 8, 7, 4, 1, 9, 6, 3, 5},
        {3, 4, 5, 2, 8, 6, 1, 7, 9}
    };

    private final Random random = new Random();
    private final int[][] solution = new int[9][9];
    private final int[][] initial = new int[9][9];
    private final int[][] board = new int[9][9];
    private final boolean[][] locked = new boolean[9][9];
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    public SudokuGame() {
        newPuzzle();
    }

    // Crea una partida nueva mezclando la base (siempre válida)
    // y vaciando unas pocas casillas al azar
    private void newPuzzle() {
        // Cada número se cambia por otro al azar
        List<Integer> digits = nums(1, 9);
        Collections.shuffle(digits, random);
        int[] perm = new int[10];
        for (int i = 0; i < 9; i++) {
            perm[i + 1] = digits.get(i);
        }
        // Nuevo orden de filas y columnas (respeta los bloques de 3,
        // por eso el resultado sigue siendo válido)
        int[] rows = shuffledLines();
        int[] cols = shuffledLines();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                solution[r][c] = perm[BASE[rows[r]][cols[c]]];
            }
        }
        // A veces se transpone, también sigue siendo válido
        if (random.nextBoolean()) {
            transpose(solution);
        }
        // Copia la solución y vacía unas pocas casillas al azar
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                initial[r][c] = solution[r][c];
            }
        }
        List<Integer> cells = nums(0, 80);
        Collections.shuffle(cells, random);
        for (int i = 0; i < HOLES; i++) {
            int pos = cells.get(i);
            initial[pos / 9][pos % 9] = 0;
        }
        resetBoard();
    }

    // Orden de líneas mezclado que respeta los bloques de 3
    // (vale tanto para filas como para columnas)
    private int[] shuffledLines() {
        List<Integer> bands = nums(0, 2);
        Collections.shuffle(bands, random);
        int[] order = new int[9];
        int k = 0;
        for (int b : bands) {
            List<Integer> lines = nums(b * 3, b * 3 + 2);
            Collections.shuffle(lines, random);
            for (int l : lines) {
                order[k++] = l;
            }
        }
        return order;
    }

    // Lista de enteros desde min hasta max (ambos incluidos)
    private List<Integer> nums(int min, int max) {
        List<Integer> list = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            list.add(i);
        }
        return list;
    }

    // Transpone una matriz de 9x9
    private void transpose(int[][] m) {
        for (int r = 0; r < 9; r++) {
            for (int c = r + 1; c < 9; c++) {
                int tmp = m[r][c];
                m[r][c] = m[c][r];
                m[c][r] = tmp;
            }
        }
    }

    // Copia las casillas iniciales al tablero y bloquea las que ya vienen dadas
    private void resetBoard() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board[r][c] = initial[r][c];
                locked[r][c] = initial[r][c] != 0;
            }
        }
    }

    // Empieza una partida nueva (sudoku distinto) y pone todos los puntos a 0
    public void restart(Set<String> players) {
        newPuzzle();
        scores.clear();
        if (players != null) {
            for (String p : players) {
                scores.put(p, 0);
            }
        }
    }

    // Apunta al jugador con 0 puntos si aún no existe
    public void ensurePlayer(String name) {
        scores.putIfAbsent(name, 0);
    }

    // Aplica una jugada: +2 si acierta (y bloquea la casilla), -1 si falla
    public void play(String name, int row, int col, int value) {
        if (row < 0 || row > 8 || col < 0 || col > 8 || value < 1 || value > 9) {
            return;
        }
        ensurePlayer(name);
        if (locked[row][col]) {
            return;
        }
        if (solution[row][col] == value) {
            board[row][col] = value;
            locked[row][col] = true;
            scores.put(name, scores.getOrDefault(name, 0) + 2);
        } else {
            scores.put(name, scores.getOrDefault(name, 0) - 1);
        }
    }

    // La partida acaba cuando no queda ninguna casilla libre
    public boolean isFinished() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (!locked[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    // Mensaje "state" con tablero, bloqueos, iniciales y puntos
    public JSONObject toStateMessage() {
        JSONArray arrBoard = new JSONArray();
        JSONArray arrLocked = new JSONArray();
        JSONArray arrGivens = new JSONArray();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                arrBoard.put(board[r][c]);
                arrLocked.put(locked[r][c]);
                arrGivens.put(initial[r][c] != 0);
            }
        }
        JSONObject objScores = new JSONObject();
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            objScores.put(e.getKey(), e.getValue());
        }
        JSONObject out = new JSONObject();
        out.put("type", "state");
        out.put("board", arrBoard);
        out.put("locked", arrLocked);
        out.put("givens", arrGivens);
        out.put("scores", objScores);
        out.put("finished", isFinished());
        return out;
    }
}
