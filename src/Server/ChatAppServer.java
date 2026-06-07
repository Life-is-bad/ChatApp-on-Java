// ChatAppServer.java
package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatAppServer {
    static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        System.out.println("Starting Server...");

        try (ServerSocket serverSocket = new ServerSocket(9000)) {
            System.out.println("Server started, listening on port 9000...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(30000);
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Server error:  " + e);
        }
    }

    static void addClient(ClientHandler handler) {
        clients.add(handler);
    }

    // Broadcast raw encrypted bundle to all clients except sender
    // Server never decrypts — just forwards
    static void broadcast(String message, ClientHandler sender) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendMessage(message);
                }
            }
        }
    }

    static void broadcastAll(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
    }

    static ClientHandler getClient(String username, ClientHandler exclude) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != exclude && client.username.equalsIgnoreCase(username)) {
                    return client;
                }
            }
        }
        return null;
    }
}