package Client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKey;

public class ChatAppGUI extends Application {

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final String BG       = "#0e0f11";
    private static final String BG2      = "#13151a";
    private static final String BG3      = "#181b22";
    private static final String BG4      = "#1e2130";
    private static final String GREEN    = "#4ade80";
    private static final String GREEN2   = "#34d399";
    private static final String GREEN3   = "#2dd4bf";
    private static final String GREEN4   = "#0d9488";
    private static final String GREEN_DIM= "#0f2320";
    private static final String AMBER    = "#fbbf24";
    private static final String RED      = "#f87171";
    private static final String BLUE     = "#60a5fa";
    private static final String TEXT     = "#cbd5e1";
    private static final String TEXT2    = "#64748b";
    private static final String TEXT3    = "#334155";
    private static final String BORDER   = "#1f2d1c";
    private static final String MONO     = "JetBrains Mono, Consolas, monospace";

    // ── Network state ────────────────────────────────────────────────────────
    private Socket socket;
    private PrintWriter netOut;
    private BufferedReader netIn;
    private PrivateKey privateKey;
    private final Map<String, PublicKey> publicKeys =
        new java.util.concurrent.ConcurrentHashMap<>();
    private String username;
    private boolean connected = false;

    // ── UI nodes ─────────────────────────────────────────────────────────────
    private VBox userListBox;
    private VBox messageBox;
    private ScrollPane messageScroll;
    private VBox keyStoreBox;
    private VBox serverLogBox;
    private TextField inputField;
    private Label statusDot;
    private Label statusLabel;
    private Label keyCountLabel;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        stage.setTitle("ChatApp — localhost:9000");
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + BG + ";");

        root.setTop(buildTitleBar(stage));
        root.setLeft(buildSidebar());
        root.setCenter(buildChatArea());
        root.setRight(buildKeyPanel());

        Scene scene = new Scene(root, 1100, 660);
        stage.setScene(scene);
        stage.show();

        // Prompt for connection on startup
        Platform.runLater(this::showConnectDialog);
    }

    // ── Title bar ─────────────────────────────────────────────────────────────
    private HBox buildTitleBar(Stage stage) {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(9, 14, 9, 14));
        bar.setStyle("-fx-background-color:" + BG2 + "; -fx-border-color:" + BORDER + "; -fx-border-width:0 0 1 0;");

        // Traffic-light dots
        for (String col : new String[]{"#f87171","#fbbf24","#4ade80"}) {
            Label dot = new Label("●");
            dot.setStyle("-fx-text-fill:" + col + "; -fx-font-size:12px;");
            bar.getChildren().add(dot);
        }

        Region spacer1 = new Region(); HBox.setHgrow(spacer1, Priority.ALWAYS);
        Label title = new Label("CHATAPP — localhost:9000");
        title.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT2 + "; -fx-font-weight:bold;");
        Region spacer2 = new Region(); HBox.setHgrow(spacer2, Priority.ALWAYS);

        statusDot = new Label("●");
        statusDot.setStyle("-fx-text-fill:#4a6348; -fx-font-size:10px;");
        statusLabel = new Label("DISCONNECTED");
        statusLabel.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT3 + ";");

        bar.getChildren().addAll(spacer1, title, spacer2, statusDot, statusLabel);
        return bar;
    }

    // ── Left sidebar ──────────────────────────────────────────────────────────
    private VBox buildSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.setPrefWidth(195);
        sidebar.setStyle("-fx-background-color:" + BG2 + "; -fx-border-color:" + BORDER + "; -fx-border-width:0 1 0 0;");

        // Users section
        VBox usersSection = new VBox(4);
        usersSection.setPadding(new Insets(12, 10, 10, 10));
        usersSection.setStyle("-fx-border-color:" + BORDER + "; -fx-border-width:0 0 1 0;");

        Label usersLabel = sectionLabel("Online — 0");
        usersLabel.setId("usersCountLabel");
        userListBox = new VBox(3);

        usersSection.getChildren().addAll(usersLabel, userListBox);

        // Commands section
        VBox cmdsSection = new VBox(2);
        cmdsSection.setPadding(new Insets(10, 10, 10, 10));
        cmdsSection.setStyle("-fx-border-color:" + BORDER + "; -fx-border-width:0 0 1 0;");
        cmdsSection.getChildren().add(sectionLabel("Commands"));

        String[][] cmds = {
            {"/msg", "<user> <msg>"},
            {"/list", "online users"},
            {"/nick", "<newname>"},
            {"/shout", "<message>"},
            {"/quit", "disconnect"},
            {"/help", "show help"},
        };
        for (String[] cmd : cmds) {
            HBox row = new HBox(4);
            Label name = new Label(cmd[0]);
            name.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + GREEN3 + ";");
            Label desc = new Label(cmd[1]);
            desc.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT3 + ";");
            row.getChildren().addAll(name, desc);
            cmdsSection.getChildren().add(row);
        }

        // Info footer
        VBox infoBox = new VBox(4);
        infoBox.setPadding(new Insets(10, 10, 10, 10));
        infoBox.setStyle("-fx-border-color:" + BORDER + "; -fx-border-width:1 0 0 0;");
        infoBox.getChildren().addAll(
            infoRow("cipher", "AES-128"),
            infoRow("key-ex", "RSA-2048"),
            infoRow("port", "9000"),
            infoRow("e2e", "active", GREEN3)
        );

        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().addAll(usersSection, cmdsSection, spacer, infoBox);
        return sidebar;
    }

    // ── Chat center ───────────────────────────────────────────────────────────
    private VBox buildChatArea() {
        VBox chat = new VBox(0);
        VBox.setVgrow(chat, Priority.ALWAYS);

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 14, 8, 14));
        header.setStyle("-fx-background-color:" + BG3 + "; -fx-border-color:" + BORDER + "; -fx-border-width:0 0 1 0;");

        Label hash = new Label("#");
        hash.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:16px; -fx-text-fill:" + TEXT3 + ";");
        Label chanName = new Label("general");
        chanName.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:" + TEXT + ";");
        Region hSpacer = new Region(); HBox.setHgrow(hSpacer, Priority.ALWAYS);
        Label desc = new Label("end-to-end encrypted");
        desc.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT3 + ";");
        Label encBadge = new Label("⬡ E2E");
        encBadge.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + GREEN3 + "; -fx-background-color:" + GREEN_DIM + "; -fx-padding:2 8 2 8; -fx-border-color:" + GREEN4 + "; -fx-border-radius:3; -fx-background-radius:3;");

        header.getChildren().addAll(hash, chanName, hSpacer, desc, encBadge);

        // Messages scroll
        messageBox = new VBox(2);
        messageBox.setPadding(new Insets(12, 14, 12, 14));
        messageBox.setStyle("-fx-background-color:" + BG + ";");

        messageScroll = new ScrollPane(messageBox);
        messageScroll.setFitToWidth(true);
        messageScroll.setStyle("-fx-background-color:" + BG + "; -fx-background:" + BG + "; -fx-border-color:transparent;");
        messageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(messageScroll, Priority.ALWAYS);

        // Auto-scroll when content grows
        messageBox.heightProperty().addListener((obs, old, now) ->
            messageScroll.setVvalue(1.0));

        appendSeparator("SESSION START");
        appendSystemMsg("Welcome to ChatApp. Connect to get started.");

        // Input area
        VBox inputArea = new VBox(6);
        inputArea.setPadding(new Insets(10, 14, 10, 14));
        inputArea.setStyle("-fx-background-color:" + BG3 + "; -fx-border-color:" + BORDER + "; -fx-border-width:1 0 0 0;");

        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        Label prompt = new Label();
        prompt.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:12px; -fx-text-fill:" + GREEN3 + ";");
        prompt.textProperty().addListener((o, old, v) -> {});
        // Update prompt when username set
        prompt.setText("guest@chat:~$");

        inputField = new TextField();
        inputField.setPromptText("type a message or /command...");
        inputField.setStyle(
            "-fx-font-family:" + MONO + "; -fx-font-size:12px;" +
            "-fx-background-color:" + BG4 + "; -fx-text-fill:" + TEXT + ";" +
            "-fx-border-color:" + BORDER + "; -fx-border-radius:4; -fx-background-radius:4;" +
            "-fx-padding:7 10 7 10; -fx-prompt-text-fill:" + TEXT3 + ";"
        );
        HBox.setHgrow(inputField, Priority.ALWAYS);

        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) handleSend(prompt);
        });

        inputRow.getChildren().addAll(prompt, inputField);

        // Quick-command buttons
        HBox btnRow = new HBox(6);
        for (String[] btn : new String[][]{{"/msg","/msg alice "},{"/list","/list"},{"/shout","/shout "},{"/nick","/nick "},{"/help","/help"}}) {
            Button b = new Button(btn[0]);
            b.setStyle(
                "-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT2 + ";" +
                "-fx-background-color:" + BG4 + "; -fx-border-color:" + BORDER + "; -fx-border-radius:3; -fx-background-radius:3;" +
                "-fx-padding:4 10 4 10; -fx-cursor:hand;"
            );
            final String fill = btn[1];
            b.setOnAction(e -> { inputField.setText(fill); inputField.requestFocus(); inputField.positionCaret(fill.length()); });
            b.setOnMouseEntered(e -> b.setStyle(
                "-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + GREEN + ";" +
                "-fx-background-color:" + GREEN_DIM + "; -fx-border-color:" + GREEN4 + "; -fx-border-radius:3; -fx-background-radius:3;" +
                "-fx-padding:4 10 4 10; -fx-cursor:hand;"
            ));
            b.setOnMouseExited(e -> b.setStyle(
                "-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT2 + ";" +
                "-fx-background-color:" + BG4 + "; -fx-border-color:" + BORDER + "; -fx-border-radius:3; -fx-background-radius:3;" +
                "-fx-padding:4 10 4 10; -fx-cursor:hand;"
            ));
            btnRow.getChildren().add(b);
        }

        Label hint = new Label("● messages encrypted with AES-128 + RSA-2048 key exchange · server cannot read content");
        hint.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + GREEN4 + ";");

        inputArea.getChildren().addAll(inputRow, btnRow, hint);

        chat.getChildren().addAll(header, messageScroll, inputArea);
        return chat;
    }

    // ── Right key panel ───────────────────────────────────────────────────────
    private VBox buildKeyPanel() {
        VBox panel = new VBox(0);
        panel.setPrefWidth(235);
        panel.setStyle("-fx-background-color:" + BG2 + "; -fx-border-color:" + BORDER + "; -fx-border-width:0 0 0 1;");

        // Header
        VBox kHeader = new VBox(2);
        kHeader.setPadding(new Insets(10, 12, 10, 12));
        kHeader.setStyle("-fx-border-color:" + BORDER + "; -fx-border-width:0 0 1 0;");
        Label kTitle = new Label("RSA KEY STORE");
        kTitle.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT2 + "; -fx-font-weight:bold;");
        keyCountLabel = new Label("0 keys loaded");
        keyCountLabel.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + TEXT3 + ";");
        kHeader.getChildren().addAll(kTitle, keyCountLabel);

        // Key entries scroll
        keyStoreBox = new VBox(0);
        ScrollPane keyScroll = new ScrollPane(keyStoreBox);
        keyScroll.setFitToWidth(true);
        keyScroll.setStyle("-fx-background-color:" + BG2 + "; -fx-background:" + BG2 + "; -fx-border-color:transparent;");
        VBox.setVgrow(keyScroll, Priority.ALWAYS);

        // Server log section
        VBox logSection = new VBox(4);
        logSection.setPadding(new Insets(8, 12, 8, 12));
        logSection.setStyle("-fx-border-color:" + BORDER + "; -fx-border-width:1 0 0 0;");
        logSection.getChildren().add(sectionLabel("Server Log"));

        serverLogBox = new VBox(3);
        ScrollPane logScroll = new ScrollPane(serverLogBox);
        logScroll.setPrefHeight(130);
        logScroll.setMaxHeight(130);
        logScroll.setFitToWidth(true);
        logScroll.setStyle("-fx-background-color:" + BG2 + "; -fx-background:" + BG2 + "; -fx-border-color:transparent;");
        serverLogBox.heightProperty().addListener((o, old, nv) -> logScroll.setVvalue(1.0));

        logSection.getChildren().add(logScroll);

        panel.getChildren().addAll(kHeader, keyScroll, logSection);
        return panel;
    }

    // ── Connect dialog ────────────────────────────────────────────────────────
    private void showConnectDialog() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Connect to ChatApp");
        dialog.setHeaderText(null);

        DialogPane dp = dialog.getDialogPane();
        dp.setStyle("-fx-background-color:" + BG3 + ";");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField hostField = styledField("localhost");
        TextField portField = styledField("9000");
        TextField userField = styledField("echo");

        grid.add(styledLabel("Host"), 0, 0); grid.add(hostField, 1, 0);
        grid.add(styledLabel("Port"), 0, 1); grid.add(portField, 1, 1);
        grid.add(styledLabel("Username"), 0, 2); grid.add(userField, 1, 2);

        dp.setContent(grid);

        ButtonType connectBtn = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dp.getButtonTypes().addAll(connectBtn, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt == connectBtn) return new String[]{hostField.getText(), portField.getText(), userField.getText()};
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(r -> connectToServer(r[0], Integer.parseInt(r[1]), r[2]));
    }

    // ── Network: connect ──────────────────────────────────────────────────────
    private void connectToServer(String host, int port, String user) {
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                netOut = new PrintWriter(socket.getOutputStream(), true);
                netIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // RSA key pair
                KeyPair kp = EncryptionUtils.generateRSAKeyPair();
                privateKey = kp.getPrivate();
                PublicKey myPub = kp.getPublic();

                // Step 1: username
                netIn.readLine(); // ENTER_USERNAME
                this.username = user;
                netOut.println(username);

                String serverReply = netIn.readLine();
                if (serverReply != null && serverReply.startsWith("ERROR")) {
                    Platform.runLater(() -> appendErrorMsg(serverReply.substring(6)));
                    return;
                }

                // Step 2: send public key
                // serverReply is SEND_PUBLIC_KEY
                netOut.println(EncryptionUtils.publicKeyToString(myPub));

                // Step 3: receive existing public keys
                String keyBundle = netIn.readLine();
                if (keyBundle != null && keyBundle.startsWith("PUBLIC_KEYS ")) {
                    String bundle = keyBundle.substring("PUBLIC_KEYS ".length());
                    if (!bundle.isBlank()) {
                        for (String entry : bundle.split(",")) {
                            String[] kv = entry.split(":", 2);
                            if (kv.length == 2) {
                                publicKeys.put(kv[0], EncryptionUtils.stringToPublicKey(kv[1]));
                            }
                        }
                    }
                }
                publicKeys.put(username, myPub);

                connected = true;
                String finalUser = username;
                Platform.runLater(() -> {
                    setConnected(true, finalUser);
                    appendSeparator("CONNECTED — " + host + ":" + port);
                    refreshKeyStore(myPub);
                });

                // Receiver loop
                String line;
                while ((line = netIn.readLine()) != null) {
                    final String fLine = line;
                    Platform.runLater(() -> handleIncoming(fLine));
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendErrorMsg("Connection failed: " + e.getMessage());
                    setConnected(false, "guest");
                });
            }
        }, "net-receiver").start();
    }

    // ── Send handling ─────────────────────────────────────────────────────────
    private void handleSend(Label prompt) {
        String text = inputField.getText().trim();
        if (text.isBlank()) return;
        inputField.clear();

        if (!connected) {
            appendErrorMsg("Not connected. Use File > Connect.");
            return;
        }

        try {
            if (text.startsWith("/msg ")) {
                String[] parts = text.split(" ", 3);
                if (parts.length < 3) { appendSystemMsg("Usage: /msg <username> <message>"); return; }
                sendPrivateMessage(parts[1], parts[2]);
                appendPrivateMsg("→ " + parts[1], parts[2]);
            } else if (text.startsWith("/")) {
                netOut.println(text);
            } else {
                sendGroupMessage(text);
                appendOwnMsg(text);
            }
        } catch (Exception ex) {
            appendErrorMsg("Send error: " + ex.getMessage());
        }
    }

    private void sendGroupMessage(String message) throws Exception {
        SecretKey aesKey = EncryptionUtils.generateAESKey();
        String enc = EncryptionUtils.encryptMessage(message, aesKey);
        StringBuilder bundle = new StringBuilder();
        synchronized (publicKeys) {
            for (Map.Entry<String, PublicKey> e : publicKeys.entrySet()) {
                if (!e.getKey().equals(username)) {
                    String ek = EncryptionUtils.encryptAESKey(aesKey, e.getValue());
                    bundle.append(e.getKey()).append(":").append(ek).append("|");
                }
            }
        }
        netOut.println(enc + " KEYS " + bundle.toString().replaceAll("\\|$", ""));
    }

    private void sendPrivateMessage(String target, String message) throws Exception {
        PublicKey pk = publicKeys.get(target);
        if (pk == null) { appendErrorMsg("No key for '" + target + "'. Are they online?"); return; }
        SecretKey aesKey = EncryptionUtils.generateAESKey();
        String encMsg = EncryptionUtils.encryptMessage(message, aesKey);
        String encKey = EncryptionUtils.encryptAESKey(aesKey, pk);
        netOut.println("/msg " + target + " " + encMsg + " KEY " + encKey);
    }

    // ── Incoming message handling ─────────────────────────────────────────────
    private void handleIncoming(String line) {
        try {
            if (line.startsWith("MSG ")) {

            String payload = line.substring(4);

            int firstSpace = payload.indexOf(' ');
            if (firstSpace == -1) return;

            String sender = payload.substring(0, firstSpace);

            String rest = payload.substring(firstSpace + 1);

            String[] split = rest.split(" KEYS ");
            if (split.length < 2) return;

            String encMsg = split[0];

            for (String entry : split[1].split("\\|")) {

                String[] kv = entry.split(":", 2);

                if (kv.length == 2 && kv[0].equals(username)) {

                    SecretKey ak =
                            EncryptionUtils.decryptAESKey(kv[1], privateKey);

                    String decrypted =
                            EncryptionUtils.decryptMessage(encMsg, ak);

                    appendPeerMsg(sender, decrypted);
                    break;
                }
            }
        } 
            else if (line.startsWith("PM ")) {
                String[] parts = line.split(" ", 3);
                if (parts.length < 3) return;
                String sender = parts[1];
                String[] msgAndKey = parts[2].split(" KEY ");
                if (msgAndKey.length < 2) return;
                SecretKey ak = EncryptionUtils.decryptAESKey(msgAndKey[1], privateKey);
                String decrypted = EncryptionUtils.decryptMessage(msgAndKey[0], ak);
                appendPrivateMsgReceived(sender, decrypted);
            } else if (line.startsWith("NEW_KEY ")) {
                String payload = line.substring(8);
                String[] kv = payload.split(":", 2);
                if (kv.length == 2) {
                    PublicKey newKey = EncryptionUtils.stringToPublicKey(kv[1]);
                    publicKeys.put(kv[0], newKey);
                    addKeyEntry(kv[0], kv[1], false);
                    addServerLog("key received from " + kv[0], GREEN3);
                }
            } else if (line.startsWith("SHOUT FROM ")) {
                // Fix: server sends "SHOUT FROM " + username + ": " + text
                appendShoutMsg(line.substring(10));
            } else if (line.startsWith("SERVER ")) {
                String msg = line.substring(7);

                if (msg.startsWith("Online users:")) {

                    String usersText =
                            msg.substring("Online users:".length()).trim();

                    List<String> users = new ArrayList<>();

                    if (!usersText.isBlank()) {

                        for (String user : usersText.split(",")) {
                            users.add(user.trim());
                        }
                    }

                    refreshUserList(users);
                    return;
                }

                appendSystemMsg(msg);
                if (msg.contains("joined")) addServerLog(msg, GREEN3);
                else if (msg.contains("left")) addServerLog(msg, AMBER);
                else addServerLog(msg, TEXT2);
                // Refresh user list hints (server sends join/leave events)
                if (msg.contains("joined") || msg.contains("left")) {
                    netOut.println("/list"); // trigger a list refresh (server will reply)
                }
            } else if (line.startsWith("ERROR ")) {
                appendErrorMsg(line.substring(6));
            }
        } catch (Exception e) {
            appendErrorMsg("Decryption error: " + e.getMessage());
        }
    }

    // ── UI helpers: message rendering ─────────────────────────────────────────
    private void appendSeparator(String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(8, 0, 8, 0));
        Label line1 = new Label("───────────────");
        line1.setStyle("-fx-text-fill:" + BORDER + "; -fx-font-size:12px;");
        Label label = new Label(text);
        label.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + TEXT3 + ";");
        Label line2 = new Label("───────────────");
        line2.setStyle("-fx-text-fill:" + BORDER + "; -fx-font-size:12px;");
        row.getChildren().addAll(line1, label, line2);
        messageBox.getChildren().add(row);
    }

    private HBox msgRow(String ts, String user, String userColor, String body, String bodyColor, boolean encBadge) {
        HBox row = new HBox(10);
        row.setPadding(new Insets(1, 0, 1, 0));

        Label tsLabel = new Label(ts);
        tsLabel.setMinWidth(36);
        tsLabel.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + TEXT3 + ";");

        Label userLabel = new Label(user);
        userLabel.setMinWidth(88);
        userLabel.setAlignment(Pos.CENTER_RIGHT);
        userLabel.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:" + userColor + ";");

        Label bodyLabel = new Label(body + (encBadge ? "  [AES]" : ""));
        bodyLabel.setWrapText(true);
        bodyLabel.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:12px; -fx-text-fill:" + bodyColor + ";");
        HBox.setHgrow(bodyLabel, Priority.ALWAYS);

        row.getChildren().addAll(tsLabel, userLabel, bodyLabel);
        return row;
    }

    private String now() {
        java.time.LocalTime t = java.time.LocalTime.now();
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private void appendSystemMsg(String msg) {
        messageBox.getChildren().add(msgRow(now(), "SERVER", TEXT3, msg, TEXT3, false));
    }

    private void appendErrorMsg(String msg) {
        messageBox.getChildren().add(msgRow(now(), "ERROR", RED, msg, RED, false));
    }

    private void appendOwnMsg(String msg) {
        messageBox.getChildren().add(msgRow(now(), username, AMBER, msg, TEXT, true));
    }

    private void appendPeerMsg(String sender, String msg) {
        messageBox.getChildren().add(msgRow(now(), sender, GREEN2, msg, TEXT, true));
    }

    private void appendPrivateMsg(String target, String msg) {
        messageBox.getChildren().add(msgRow(now(), target, BLUE, "[PM] " + msg, BLUE, false));
    }

    private void appendPrivateMsgReceived(String sender, String msg) {
        messageBox.getChildren().add(msgRow(now(), "← " + sender, BLUE, "[PM] " + msg, BLUE, false));
    }

    private void appendShoutMsg(String msg) {
        messageBox.getChildren().add(msgRow(now(), "SHOUT", AMBER, "⚡ " + msg.toUpperCase(), AMBER, false));
    }

    // ── User list ─────────────────────────────────────────────────────────────
    private void refreshUserList(List<String> users) {

        userListBox.getChildren().clear();

        for (String user : users) {
            boolean isYou =
                    user.equals(username) ||
                    user.equals(username + " (you)");

            addUserRow(
                user.replace(" (you)", ""),
                isYou
            );
        }

        Label count =
                (Label) userListBox.getScene()
                        .lookup("#usersCountLabel");

        if (count != null) {
            count.setText("Online — " + users.size());
        }
    }

    private void setConnected(boolean on, String user) {
        if (on) {
            statusDot.setStyle("-fx-text-fill:" + GREEN + "; -fx-font-size:10px;");
            statusLabel.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + GREEN + ";");
            statusLabel.setText("CONNECTED");
        } else {
            statusDot.setStyle("-fx-text-fill:" + TEXT3 + "; -fx-font-size:10px;");
            statusLabel.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT3 + ";");
            statusLabel.setText("DISCONNECTED");
        }
    }

    private void addUserRow(String name, boolean isYou) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 6, 3, 6));
        row.setStyle("-fx-background-radius:4;");
        if (isYou) row.setStyle("-fx-background-color:" + GREEN_DIM + "; -fx-background-radius:4; -fx-border-color:" + GREEN3 + "; -fx-border-width:0 0 0 2;");

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill:" + GREEN2 + "; -fx-font-size:8px;");

        VBox info = new VBox(0);
        Label uName = new Label(name + (isYou ? " (you)" : ""));
        uName.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:12px; -fx-text-fill:" + (isYou ? GREEN : TEXT) + ";");
        Label keyTag = new Label("⬡ RSA-2048");
        keyTag.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + GREEN4 + ";");
        info.getChildren().addAll(uName, keyTag);

        row.getChildren().addAll(dot, info);
        userListBox.getChildren().add(row);
    }

    // ── Key store panel ───────────────────────────────────────────────────────
    private void refreshKeyStore(PublicKey myPub) {
        keyStoreBox.getChildren().clear();
        String keyStr = EncryptionUtils.publicKeyToString(myPub);
        addKeyEntry(username, keyStr, true);
        synchronized (publicKeys) {
            for (Map.Entry<String, PublicKey> e : publicKeys.entrySet()) {
                if (!e.getKey().equals(username)) {
                    addKeyEntry(e.getKey(), EncryptionUtils.publicKeyToString(e.getValue()), false);
                }
            }
        }
        keyCountLabel.setText(publicKeys.size() + " keys loaded");
        userListBox.getChildren().clear();
        addUserRow(username, true);
        synchronized (publicKeys) {
            for (String k : publicKeys.keySet()) {
                if (!k.equals(username)) addUserRow(k, false);
            }
        }
    }

    private void addKeyEntry(String name, String keyBase64, boolean isYou) {
        VBox entry = new VBox(3);
        entry.setPadding(new Insets(10, 12, 10, 12));
        entry.setStyle("-fx-border-color:" + BORDER + "; -fx-border-width:0 0 1 0;");

        HBox nameRow = new HBox(6);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:11px; -fx-text-fill:" + TEXT + ";");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label badge = new Label(isYou ? "YOU" : "PEER");
        badge.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:9px; -fx-text-fill:" + (isYou ? AMBER : GREEN3) + ";" +
            "-fx-background-color:" + (isYou ? "#2a2010" : GREEN_DIM) + "; -fx-padding:1 6 1 6;" +
            "-fx-border-color:" + (isYou ? "#78350f" : GREEN4) + "; -fx-border-radius:2; -fx-background-radius:2;");
        nameRow.getChildren().addAll(nameLabel, sp, badge);

        // Show first 40 chars of key
        String preview = keyBase64.length() > 40 ? keyBase64.substring(0, 40) + "..." : keyBase64;
        Label keyVal = new Label(preview);
        keyVal.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + TEXT3 + "; -fx-wrap-text:true;");
        keyVal.setWrapText(true);

        Label algo = new Label("algo: RSA-2048  ·  enc: X.509");
        algo.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + TEXT3 + ";");

        entry.getChildren().addAll(nameRow, keyVal, algo);
        keyStoreBox.getChildren().add(entry);
        keyCountLabel.setText(publicKeys.size() + " keys loaded");
    }

    private void addServerLog(String msg, String color) {
        HBox row = new HBox(8);
        Label ts = new Label(now());
        ts.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + TEXT3 + ";");
        Label m = new Label(msg);
        m.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + color + "; -fx-wrap-text:true;");
        m.setWrapText(true);
        HBox.setHgrow(m, Priority.ALWAYS);
        row.getChildren().addAll(ts, m);
        serverLogBox.getChildren().add(row);
    }

    // ── Small helpers ─────────────────────────────────────────────────────────
    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + TEXT3 + "; -fx-font-weight:bold;");
        VBox.setMargin(l, new Insets(0, 0, 6, 0));
        return l;
    }

    private HBox infoRow(String key, String value) { return infoRow(key, value, TEXT2); }
    private HBox infoRow(String key, String value, String valColor) {
        HBox row = new HBox();
        Label k = new Label(key);
        k.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + TEXT3 + ";");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label v = new Label(value);
        v.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:10px; -fx-text-fill:" + valColor + ";");
        row.getChildren().addAll(k, sp, v);
        return row;
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:12px; -fx-text-fill:" + TEXT2 + ";");
        return l;
    }

    private TextField styledField(String def) {
        TextField f = new TextField(def);
        f.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:12px; -fx-background-color:" + BG4 + "; -fx-text-fill:" + TEXT + "; -fx-border-color:" + BORDER + ";");
        return f;
    }

    @Override
    public void stop() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        launch(args);
    }
}