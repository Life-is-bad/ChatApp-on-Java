// ChatAppClient.java
package Client;

import java.io.*;
import java.net.Socket;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKey;

public class ChatAppClient {
    private static final Map<String, PublicKey> publicKeys = new HashMap<>();
    private static PrivateKey privateKey;
    private static String username;
    private static PrintWriter out;

    public static void main(String[] args) {
        try (
            Socket socket = new Socket("localhost", 9000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner sc = new Scanner(System.in)
        ) {
            out = new PrintWriter(socket.getOutputStream(), true);

            // Generate RSA key pair
            KeyPair keyPair = EncryptionUtils.generateRSAKeyPair();
            privateKey = keyPair.getPrivate();
            PublicKey myPublicKey = keyPair.getPublic();

            // Step 1: enter username
            in.readLine(); // "ENTER_USERNAME"
            System.out.print("Enter your username: ");
            username = sc.nextLine();
            out.println(username);

            // Step 2: send public key
            in.readLine(); // "SEND_PUBLIC_KEY"
            out.println(EncryptionUtils.publicKeyToString(myPublicKey));

            // Step 3: receive existing public keys
            // Format: PUBLIC_KEYS username1:key1,username2:key2,...
            String keyBundleLine = in.readLine();
            if (keyBundleLine.startsWith("PUBLIC_KEYS ")) {
                String keyBundle = keyBundleLine.substring("PUBLIC_KEYS ".length());
                if (!keyBundle.isBlank()) {
                    for (String entry : keyBundle.split(",")) {
                        String[] kv = entry.split(":", 2);
                        if (kv.length == 2) {
                            publicKeys.put(kv[0], EncryptionUtils.stringToPublicKey(kv[1]));
                        }
                    }
                }
            }

            // Store own public key too (for sending to self in bundles if needed)
            publicKeys.put(username, myPublicKey);

            // Receiver thread
            Thread receiver = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        handleIncoming(line);
                    }
                } catch (IOException e) {
                    System.out.println("\nDisconnected from server.");
                }
            });
            receiver.setDaemon(true);
            receiver.start();

            // Sender
            while (true) {
                System.out.print("> ");
                String input = sc.nextLine();

                if (input.startsWith("/msg ")) {
                    sendPrivateMessage(input);
                } else if (input.startsWith("/")) {
                    // Other commands sent as-is
                    out.println(input);
                } else if (!input.isBlank()) {
                    sendGroupMessage(input);
                }
            }
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Encryption error: " + e.getMessage());
        }
    }

    private static void sendGroupMessage(String message) throws Exception {
        // Generate a fresh AES key for this message
        SecretKey aesKey = EncryptionUtils.generateAESKey();
        String encryptedMessage = EncryptionUtils.encryptMessage(message, aesKey);

        // Encrypt AES key for each recipient
        StringBuilder bundle = new StringBuilder();
        synchronized (publicKeys) {
            for (Map.Entry<String, PublicKey> entry : publicKeys.entrySet()) {
                if (!entry.getKey().equals(username)) {
                    String encryptedKey = EncryptionUtils.encryptAESKey(aesKey, entry.getValue());
                    bundle.append(entry.getKey()).append(":").append(encryptedKey).append("|");
                }
            }
        }

        // Format: encryptedMessage KEYS bundle
        out.println(encryptedMessage + " KEYS " + bundle.toString().replaceAll("\\|$", ""));
        System.out.print("\r\033[2K");
        System.out.println("You: " + message);
        System.out.print("> ");
    }

    private static void sendPrivateMessage(String input) throws Exception {
        String[] parts = input.split(" ", 3);
        if (parts.length < 3) {
            System.out.println("[Client] Usage: /msg <username> <message>");
            return;
        }
        String targetName = parts[1];
        String message = parts[2];

        PublicKey targetKey = publicKeys.get(targetName);
        if (targetKey == null) {
            System.out.println("[Client] No public key for '" + targetName + "'. Are they online?");
            return;
        }

        SecretKey aesKey = EncryptionUtils.generateAESKey();
        String encryptedMessage = EncryptionUtils.encryptMessage(message, aesKey);
        String encryptedKey = EncryptionUtils.encryptAESKey(aesKey, targetKey);

        out.println("/msg " + targetName + " " + encryptedMessage + " KEY " + encryptedKey);
        System.out.print("\r\033[2K");
        System.out.println("[PM to " + targetName + "] " + message);
        System.out.print("> ");
    }

    private static void handleIncoming(String line) {
        try {
            System.out.print("\r\033[2K");

            if (line.startsWith("MSG ")) {
                // Encrypted group message
                // Format: MSG encryptedMessage KEYS sender:encryptedKey|...
                String payload = line.substring(4);
                String[] split = payload.split(" KEYS ");
                if (split.length < 2) return;

                String encryptedMessage = split[0];
                String keySection = split[1];

                // Find our encrypted AES key in the bundle
                for (String entry : keySection.split("\\|")) {
                    String[] kv = entry.split(":", 2);
                    if (kv.length == 2 && kv[0].equals(username)) {
                        SecretKey aesKey = EncryptionUtils.decryptAESKey(kv[1], privateKey);
                        String decrypted = EncryptionUtils.decryptMessage(encryptedMessage, aesKey);
                        System.out.println(decrypted);
                        break;
                    }
                }

            } else if (line.startsWith("PM ")) {
                // Encrypted private message
                // Format: PM senderUsername encryptedMessage KEY encryptedKey
                String[] parts = line.split(" ", 3);
                if (parts.length < 3) return;
                String sender = parts[1];
                String[] msgAndKey = parts[2].split(" KEY ");
                if (msgAndKey.length < 2) return;

                SecretKey aesKey = EncryptionUtils.decryptAESKey(msgAndKey[1], privateKey);
                String decrypted = EncryptionUtils.decryptMessage(msgAndKey[0], aesKey);
                System.out.println("[PM from " + sender + "] " + decrypted);

            } else if (line.startsWith("NEW_KEY ")) {
                // A new client joined — store their public key
                // Format: NEW_KEY username:publicKey
                String payload = line.substring(8);
                String[] kv = payload.split(":", 2);
                if (kv.length == 2) {
                    publicKeys.put(kv[0], EncryptionUtils.stringToPublicKey(kv[1]));
                }
                // Don't print anything — the SERVER join message handles that

            } else if (line.startsWith("SHOUT ")) {
                System.out.println("SHOUT FROM " + line.substring(6));

            } else if (line.startsWith("SERVER ")) {
                System.out.println("[Server] " + line.substring(7));

            } else if (line.startsWith("ERROR ")) {
                System.out.println("[Error] " + line.substring(6));
            }

            System.out.print("> ");
        } catch (Exception e) {
            System.out.println("[Decryption error] " + e.getMessage());
            System.out.print("> ");
        }
    }
}