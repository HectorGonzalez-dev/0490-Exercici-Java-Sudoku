package com.server;

import org.java_websocket.WebSocket;
import org.json.JSONArray;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre de clients connectats amb els seus propis noms.
 *
 * Manté dos mapes bidireccionals:
 * - WebSocket a nom de client
 * - Nom de client a WebSocket
 *
 * Aquesta classe és segura per a ús concurrent gràcies a l'ús de ConcurrentHashMap
 * i ConcurrentLinkedQueue. Els mètodes que modifiquen el pool utilitzen sincronització
 * per garantir la coherència durant reinicialitzacions.
 */
final class ClientRegistry {

    /** Mapa de sockets a noms de client. */
    private final Map<WebSocket, String> bySocket = new ConcurrentHashMap<>();

    /** Mapa de noms de client a sockets. */
    private final Map<String, WebSocket> byName = new ConcurrentHashMap<>();

    /**
     * Crea un nou registre.
     */
    ClientRegistry() {

    }

    /**
     * Afegeix un client nou i li assigna un nom lliure.
     *
     * @param socket socket del client connectat
     * @param name nom del client connectat
     * @return el nom assignat al client
     */
    String add(WebSocket socket, String name) {
        bySocket.put(socket, name);
        byName.put(name, socket);
        return name;
    }

    /**
     * Elimina un client del registre i retorna el nom al pool.
     *
     * @param socket socket del client a eliminar
     * @return el nom que estava assignat, o null si no existia
     */
    String remove(WebSocket socket) {
        String name = bySocket.remove(socket);
        if (name != null) {
            byName.remove(name);
        }
        return name;
    }

    /**
     * Obté el socket associat a un nom de client.
     *
     * @param name nom del client
     * @return socket associat o null si no existeix
     */
    WebSocket socketByName(String name) {
        return byName.get(name);
    }

    /**
     * Obté el nom associat a un socket.
     *
     * @param socket socket del client
     * @return nom del client o null si no existeix
     */
    String nameBySocket(WebSocket socket) {
        return bySocket.get(socket);
    }

    /**
     * Retorna la llista actual de noms de clients connectats en format JSONArray.
     *
     * @return JSONArray amb els noms dels clients actius
     */
    JSONArray currentNames() {
        JSONArray arr = new JSONArray();
        for (String n : byName.keySet()) {
            arr.put(n);
        }
        return arr;
    }

    /**
     * Neteja el registre per a un socket desconnectat.
     * Equivalent a remove(socket).
     *
     * @param socket socket desconnectat
     * @return nom del client eliminat o null si no existia
     */
    String cleanupDisconnected(WebSocket socket) {
        return remove(socket);
    }

    /**
     * Retorna una còpia immutable de l'estat actual del mapa socket a nom.
     * Útil per iteracions fora del lock intern sense risc de ConcurrentModification.
     *
     * @return mapa immutable de WebSocket a nom
     */
    Map<WebSocket, String> snapshot() {
        return Map.copyOf(bySocket);
    }

    int size() {
        return bySocket.size();
    }

    boolean contains(String name) {
        return byName.containsKey(name);
    }
}
