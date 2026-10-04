package com.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * Servidor WebSocket amb routing simple de missatges, sense REPL.
 *
 * El servidor arrenca, registra un shutdown hook i es queda a l'espera
 * fins que el procés rep un senyal de terminació (SIGINT, SIGTERM).
 *
 * Missatges suportats:
 *  - bounce: eco del missatge a l’emissor
 *  - broadcast: envia a tots excepte l’emissor
 *  - private: envia a un destinatari pel seu nom
 *  - clients: llista de clients connectats
 *  - error / confirmation: missatges de control
 */
public class Main extends WebSocketServer {

    /** Port per defecte on escolta el servidor. */
    public static final int DEFAULT_PORT = 3000;

    /** Llista de noms disponibles per als clients connectats. */
    private static final List<String> CHARACTER_NAMES = Arrays.asList(
        "Mario", "Luigi", "Peach", "Toad", "Bowser", "Wario", "Zelda", "Link"
    );

    // Claus JSON
    private static final String K_TYPE = "type";
    private static final String K_MESSAGE = "message";
    private static final String K_ORIGIN = "origin";
    private static final String K_DESTINATION = "destination";
    private static final String K_ID = "id";
    private static final String K_LIST = "list";

    // Tipus de missatge
    private static final String T_BOUNCE = "bounce";
    private static final String T_BROADCAST = "broadcast";
    private static final String T_PRIVATE = "private";
    private static final String T_CLIENTS = "clients";
    private static final String T_ERROR = "error";
    private static final String T_CONFIRMATION = "confirmation";

    /** Registre de clients i assignació de noms (pool integrat). */
    private final ClientRegistry clients;

    /**
     * Crea un servidor WebSocket que escolta a l'adreça indicada.
     *
     * @param address adreça i port d'escolta del servidor
     */
    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(CHARACTER_NAMES);
    }

    // ----------------- Helpers JSON -----------------

    /**
     * Crea un objecte JSON amb el camp type inicialitzat.
     *
     * @param type valor per a type
     * @return instància de JSONObject amb el tipus establert
     */
    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    /**
     * Afegeix clau-valor al JSONObject si el valor no és null.
     *
     * @param o objecte JSON destí
     * @param k clau
     * @param v valor (ignorat si és null)
     */
    private static void put(JSONObject o, String k, Object v) {
        if (v != null) o.put(k, v);
    }

    /**
     * Envia de forma segura un payload i, si el socket no està connectat,
     * el neteja del registre.
     *
     * @param to socket destinatari
     * @param payload cadena JSON a enviar
     */
    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            System.out.println("Client desconnectat durant send: " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Envia un missatge a tots els clients excepte l’emissor.
     *
     * @param sender  socket emissor
     * @param payload cadena JSON a enviar
     */
    private void broadcastExcept(WebSocket sender, String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            if (!Objects.equals(conn, sender)) sendSafe(conn, payload);
        }
    }

    /**
     * Envia la llista actualitzada de clients a tots els clients connectats.
     */
    private void sendClientsListToAll() {
        JSONArray list = clients.currentNames();
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            JSONObject rst = msg(T_CLIENTS);
            put(rst, K_ID, e.getValue());
            put(rst, K_LIST, list);
            sendSafe(e.getKey(), rst.toString());
        }
    }

    // ----------------- WebSocketServer overrides -----------------

    /** Assigna un nom al client i notifica la llista actualitzada. */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String name = clients.add(conn);
        System.out.println("Client connectat: " + name);
        sendClientsListToAll();
    }

    /** Elimina el client del registre i notifica la llista actualitzada. */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        System.out.println("Client desconnectat: " + name);
        sendClientsListToAll();
    }

    /** Processa el missatge rebut i el ruteja segons el seu type. */
    @Override
    public void onMessage(WebSocket conn, String message) {
        String origin = clients.nameBySocket(conn);
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "JSON invàlid").toString());
            return;
        }

        String type = obj.optString(K_TYPE, "");
        switch (type) {
            case T_BOUNCE -> {
                String txt = obj.optString(K_MESSAGE, "");
                sendSafe(conn, msg(T_BOUNCE).put(K_MESSAGE, txt).toString());
            }
            case T_BROADCAST -> {
                String txt = obj.optString(K_MESSAGE, "");
                JSONObject rst = msg(T_BROADCAST).put(K_ORIGIN, origin).put(K_MESSAGE, txt);
                broadcastExcept(conn, rst.toString());
            }
            case T_PRIVATE -> {
                String destName = obj.optString(K_DESTINATION, "");
                if (destName.isBlank()) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Falta 'destination'").toString());
                    return;
                }
                WebSocket dest = clients.socketByName(destName);
                if (dest == null) {
                    sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Client " + destName + " no disponible.").toString());
                    return;
                }
                String txt = obj.optString(K_MESSAGE, "");
                sendSafe(dest, msg(T_PRIVATE)
                        .put(K_ORIGIN, origin)
                        .put(K_DESTINATION, destName)
                        .put(K_MESSAGE, txt)
                        .toString());
                sendSafe(conn, msg(T_CONFIRMATION).put(K_MESSAGE, "Missatge enviat a " + destName).toString());
            }
            default -> {
                sendSafe(conn, msg(T_ERROR).put(K_MESSAGE, "Tipus desconegut: " + type).toString());
            }
        }
    }

    /** Log d'error global o de socket concret. */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    /** Arrencada: log i configuració del timeout de connexió perduda. */
    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket engegat al port: " + getPort());
        setConnectionLostTimeout(100);
    }

    // ----------------- Lifecycle util -----------------

    /**
     * Registra un shutdown hook per aturar netament el servidor en finalitzar el procés.
     *
     * @param server instància a aturar
     */
    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Aturant servidor (shutdown hook)...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servidor aturat.");
        }));
    }

    /**
     * Bloqueja el fil principal indefinidament fins que sigui interromput.
     * Útil per mantenir viu el procés sense REPL.
     */
    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            // CountDownLatch permet que el fil principal 
            // s'esperi indefinidament amb latch.await()
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Punt d'entrada: arrenca el servidor al port per defecte i espera senyals.
     *
     * @param args arguments de línia d'ordres (no utilitzats)
     */
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);

        System.out.println("Servidor WebSocket en execució al port " + DEFAULT_PORT + ". Prem Ctrl+C per aturar-lo.");
        awaitForever();
    }
}
