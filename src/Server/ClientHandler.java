// ClientHandler.java
package Server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final String clientAddr;
    private PrintWriter out;
    String username;
    String publicKey; // RSA public key as Base64 string
    private boolean joined = false;
    private boolean disconnected = false;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.clientAddr = socket.getInetAddress().toString();
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            out = new PrintWriter(socket.getOutputStream(), true);

            // Step 1: receive username
            out.println("ENTER_USERNAME");
            username = in.readLine();

            if (username == null || username.isBlank()) {
                sendMessage("ERROR Invalid username. Disconnecting.");
                return;
            }

            if (ChatAppServer.getClient(username, this) != null) {
                sendMessage("ERROR Username '" + username + "' is already taken. Disconnecting.");
                return;
            }

            // Step 2: receive client's RSA public key
            out.println("SEND_PUBLIC_KEY");
            publicKey = in.readLine();

            // Step 3: send all existing public keys to the new client
            // Format: PUBLIC_KEYS username1:key1,username2:key2,...
            StringBuilder keyBundle = new StringBuilder("PUBLIC_KEYS ");
            synchronized (ChatAppServer.clients) {
                for (ClientHandler client : ChatAppServer.clients) {
                    keyBundle.append(client.username).append(":").append(client.publicKey).append(",");
                }
            }
            sendMessage(keyBundle.toString().replaceAll(",$", ""));

            // Step 4: announce new client's public key to existing clients
            // Format: NEW_KEY username:publicKey
            ChatAppServer.broadcast("NEW_KEY " + username + ":" + publicKey, this);

            joined = true;
            ChatAppServer.addClient(this);
            System.out.println(username + " joined the chat.");
            ChatAppServer.broadcastAll("SERVER " + username + " joined the chat!");
            sendMessage("SERVER Welcome, " + username + "! Type /help for a list of commands.");

            String msg;
            while ((msg = in.readLine()) != null) {
                if (msg.startsWith("/")) {
                    handleCommand(msg);
                } else if (!msg.isBlank()) {
                    // Server just forwards encrypted bundle — can't read it
                    ChatAppServer.broadcast("MSG " + username + " " + msg, this);
                }
            }
        } catch (IOException e) {
            System.err.println("Client error (" + clientAddr + "): " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleCommand(String input) {
        String[] parts = input.split(" ", 3);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "/msg" -> {
                if (parts.length < 3) {
                    sendMessage("SERVER Usage: /msg <username> <message>");
                    return;
                }
                String targetName = parts[1];
                String encryptedBundle = parts[2];
                ClientHandler target = ChatAppServer.getClient(targetName, this);
                if (target == null) {
                    sendMessage("SERVER User '" + targetName + "' not found.");
                } else if (target == this) {
                    sendMessage("SERVER You can't message yourself.");
                } else {
                    // Forward encrypted PM — server can't read it
                    target.sendMessage("PM " + username + " " + encryptedBundle);
                    sendMessage("SERVER [PM to " + targetName + "] (encrypted)");
                }
            }

            case "/list" -> {
                StringBuilder sb = new StringBuilder("SERVER Online users: ");
                synchronized (ChatAppServer.clients) {
                    for (ClientHandler client : ChatAppServer.clients) {
                        sb.append(client.username);
                        if (client == this) sb.append(" (you)");
                        sb.append(", ");
                    }
                }
                sendMessage(sb.toString().replaceAll(", $", ""));
            }

            case "/nick" -> {
                if (parts.length < 2) {
                    sendMessage("SERVER Usage: /nick <newUsername>");
                    return;
                }
                String newName = parts[1];
                if (newName.isBlank()) {
                    sendMessage("SERVER Invalid username.");
                } else if (ChatAppServer.getClient(newName, this) != null) {
                    sendMessage("SERVER Username '" + newName + "' is already taken.");
                } else {
                    String oldName = username;
                    username = newName;
                    ChatAppServer.broadcastAll("SERVER " + oldName + " is now known as " + username + ".");
                }
            }

            case "/shout" -> {
                if (parts.length < 2) {
                    sendMessage("SERVER Usage: /shout <message>");
                    return;
                }
                // Shout is plaintext — visible to server by design
                String shout = input.substring("/shout ".length()).toUpperCase();
                ChatAppServer.broadcastAll("SHOUT FROM" + username + ": " + shout);
            }

            case "/quit" -> {
                sendMessage("SERVER Goodbye, " + username + "!");
                disconnect();
            }

            case "/help" -> {
                sendMessage("""
                    SERVER Available commands:
                      /msg <username> <message>  - Send a private message
                      /list                      - Show online users
                      /nick <newUsername>        - Change your username
                      /shout <message>           - Send unencrypted message to everyone
                      /quit                      - Leave the chat
                      /help                      - Show this message""");
            }

            default -> sendMessage("SERVER Unknown command: " + command + ". Type /help for help.");
        }
    }

    private void disconnect() {
        if (disconnected) return;
        disconnected = true;

        if (joined) {
            ChatAppServer.clients.remove(this);
            ChatAppServer.broadcast("SERVER " + username + " left the chat.", this);
            System.out.println(username + " disconnected.");
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    void sendMessage(String msg) {
        out.println(msg);
    }
}