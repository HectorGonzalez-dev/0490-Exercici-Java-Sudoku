package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;

// Servidor WebSocket del Sudoku multijugador.
//
// Mensajes aceptados:
//  - register: registra al jugador y envía el estado a todos
//  - play: valida una jugada {row, col, value} y envía el estado a todos
//  - restart: si la partida terminó la reinicia con un sudoku nuevo,
//    y responde solo al jugador que lo pide (los demás siguen en resultados)
// Mensajes enviados:
//  - state: tablero + bloqueos + iniciales + puntos
//  - error: avisa de un problema
public class Main extends WebSocketServer {

    // Puerto donde escucha el servidor
    public static final int DEFAULT_PORT = 3000;
    public static final int MAX_CLIENTS = 4;

    private static final String K_TYPE = "type";
    private static final String K_MESSAGE = "message";
    private static final String K_NAME = "name";

    private static final String T_REGISTER = "register";
    private static final String T_PLAY = "play";
    private static final String T_RESTART = "restart";
    private static final String T_ERROR = "error";

    // Jugadores conectados
    private final ClientRegistry clients;

    // Partida actual
    private final SudokuGame game;

    // Crea un servidor que escucha en la dirección indicada
    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry();
        this.game = new SudokuGame();
    }

    // Crea un JSON solo con el campo type
    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    // Envía un mensaje sin fallar: si el socket está caído lo quita del registro
    private void sendSafe(WebSocket to, String payload) {
        if (to == null) {
            return;
        }
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            System.out.println("Cliente desconectado durante el envío: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Envía el estado actual a todos los clientes
    private void sendStateToAll() {
        String payload = game.toStateMessage().toString();
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            sendSafe(e.getKey(), payload);
        }
    }

    // Al abrir solo espera el mensaje register
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Socket abierto, esperando register...");
    }

    // Quita al cliente del registro (sus puntos se conservan)
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        if (name == null) {
            System.out.println("Socket no registrado desconectado");
            return;
        }
        System.out.println("Cliente desconectado: " + name);
        sendStateToAll();
    }

    // Lee el mensaje recibido y lo atiende según su type
    @Override
    public void onMessage(WebSocket conn, String message) {
        String origin = clients.nameBySocket(conn);
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "JSON inválido").toString());
            return;
        }

        String type = obj.optString(K_TYPE, "");

        if (origin == null && !T_REGISTER.equals(type)) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Not registered yet").toString());
            return;
        }

        switch (type) {
            case T_REGISTER -> {
                String name = obj.optString(K_NAME, "").trim();
                if (name.isEmpty()) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Invalid name").toString());
                    conn.close(1000, "Invalid name");
                    return;
                }
                String refused = clients.tryRegister(conn, name, MAX_CLIENTS);
                if (refused != null) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, refused).toString());
                    conn.close(1000, refused);
                    return;
                }
                game.ensurePlayer(name);
                sendStateToAll();
            }
            case T_PLAY -> {
                int row = obj.optInt("row", -1);
                int col = obj.optInt("col", -1);
                int value = obj.optInt("value", -1);
                game.play(origin, row, col, value);
                sendStateToAll();
            }
            case T_RESTART -> {
                // Solo reinicia si la partida terminó (con sudoku nuevo).
                // Si ya hay una partida en curso, devuelve el estado actual.
                if (game.isFinished()) {
                    Set<String> players = new HashSet<>();
                    for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
                        players.add(e.getValue());
                    }
                    game.restart(players);
                }
                // Solo responde al jugador que lo pide
                sendSafe(conn, game.toStateMessage().toString());
            }
            default -> {
                sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Tipo desconocido: " + type).toString());
            }
        }
    }

    // Muestra los errores del servidor
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    // Al arrancar muestra el puerto y configura el tiempo de espera
    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket encendido en el puerto: " + getPort());
        setConnectionLostTimeout(100);
    }

    // Apaga el servidor de forma limpia al cerrar el proceso
    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Apagando servidor...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor apagado.");
        }));
    }

    // Espera para siempre hasta que se interrumpa el proceso
    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // Arranca el servidor en el puerto por defecto y espera
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Servidor WebSocket en ejecución en el puerto " + DEFAULT_PORT + ". Pulsa Ctrl+C para pararlo.");
        awaitForever();
    }
}
