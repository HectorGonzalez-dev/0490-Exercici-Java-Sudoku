package com.server;

import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Guarda qué nombre tiene cada cliente conectado.
// Usa dos mapas (socket -> nombre y nombre -> socket) con ConcurrentHashMap
// para poder usarse desde varios hilos a la vez.
final class ClientRegistry {

    // Socket de cada cliente -> su nombre
    private final Map<WebSocket, String> bySocket = new ConcurrentHashMap<>();

    // Nombre de cada cliente -> su socket
    private final Map<String, WebSocket> byName = new ConcurrentHashMap<>();

    ClientRegistry() {
    }

    // Registra un socket con un nombre.
    // Devuelve null si va bien, o el motivo del rechazo si va mal.
    String tryRegister(WebSocket socket, String name, int maxClients) {
        String prev = bySocket.get(socket);
        if (name.equals(prev)) {
            return null;
        }
        WebSocket existing = byName.get(name);
        if (existing != null && !existing.equals(socket)) {
            // Si el socket viejo ya está cerrado, libera el nombre
            boolean alive;
            try {
                alive = existing.isOpen();
            } catch (Exception e) {
                alive = false;
            }
            if (!alive) {
                String stale = bySocket.remove(existing);
                if (stale != null) {
                    byName.remove(stale);
                }
                existing = null;
            }
        }
        if (existing != null) {
            return "Name taken";
        }
        if (prev == null && bySocket.size() >= maxClients) {
            return "Room full";
        }
        if (prev != null) {
            byName.remove(prev);
        }
        bySocket.put(socket, name);
        byName.put(name, socket);
        return null;
    }

    // Quita un cliente y devuelve su nombre (o null si no estaba)
    String remove(WebSocket socket) {
        String name = bySocket.remove(socket);
        if (name != null) {
            byName.remove(name);
        }
        return name;
    }

    // Nombre de un socket (o null si no está registrado)
    String nameBySocket(WebSocket socket) {
        return bySocket.get(socket);
    }

    // Lo mismo que remove, para sockets caídos
    String cleanupDisconnected(WebSocket socket) {
        return remove(socket);
    }

    // Copia del mapa actual para recorrerlo sin problemas de concurrencia
    Map<WebSocket, String> snapshot() {
        return Map.copyOf(bySocket);
    }
}
