# 🔐 Secure Java Chat Application

A secure multi-user chat application built in Java using **Sockets**, **JavaFX**, **RSA**, and **AES encryption**. The server never sees message contents, making all chat messages end-to-end encrypted.

## ✨ Features

### 🔒 End-to-End Encryption

* RSA-2048 public/private key pairs for every client
* AES session key generated for every message
* AES key encrypted separately for each recipient
* Server forwards encrypted data only
* Server cannot decrypt messages

### 💬 Messaging

* Group chat
* Private messaging (`/msg`)
* Real-time message delivery
* Join/leave notifications
* Online user list

### 👥 User Management

* Username selection
* Username changes (`/nick`)
* Online users command (`/list`)
* Duplicate username protection

### 📢 Additional Features

* Unencrypted shout messages (`/shout`)
* Command help menu (`/help`)
* JavaFX graphical user interface
* Live public key exchange

---

# 🏗 Architecture

## Client

Responsible for:

* Generating RSA key pair
* Managing public keys of other users
* Encrypting outgoing messages
* Decrypting incoming messages
* Rendering the JavaFX interface

## Server

Responsible for:

* User connections
* User authentication (username uniqueness)
* Public key distribution
* Forwarding encrypted message bundles

The server **never decrypts any message**.

---

# 🔐 Encryption Workflow

### Group Message

1. Sender creates a random AES key.
2. Message is encrypted using AES.
3. AES key is encrypted separately using each recipient's RSA public key.
4. Sender sends:

```text
encryptedMessage KEYS user1:key1|user2:key2|...
```

5. Server forwards the bundle.
6. Each recipient:

   * Finds their encrypted AES key.
   * Decrypts it with their RSA private key.
   * Uses the AES key to decrypt the message.

---

### Private Message

1. Sender generates an AES key.
2. Message is encrypted using AES.
3. AES key is encrypted using recipient's RSA public key.
4. Recipient decrypts the AES key using their RSA private key.
5. Recipient decrypts the message.

---

# 📂 Project Structure

```text
SecureChatApp
│
├── Client
│   ├── ChatAppGUI.java
│   ├── ChatAppClient.java
│   └── EncryptionUtils.java
│
├── Server
│   ├── ChatAppServer.java
│   └── ClientHandler.java
│
└── README.md
```

---

# 🖥 Commands

| Command                 | Description                    |
| ----------------------- | ------------------------------ |
| `/msg <user> <message>` | Send private encrypted message |
| `/list`                 | Show online users              |
| `/nick <newName>`       | Change username                |
| `/shout <message>`      | Send unencrypted message       |
| `/help`                 | Show command list              |
| `/quit`                 | Leave chat                     |

---

# 🚀 Running the Project

## Start Server

Compile and run:

```bash
javac Server/*.java
java Server.ChatAppServer
```

Server listens on:

```text
localhost:9000
```

---

## Start Client

Compile and run:

```bash
javac Client/*.java
java Client.ChatAppGUI
```

or

```bash
java Client.ChatAppClient
```

depending on whether you want the GUI or console version.

---

# 📡 Protocol Overview

### Initial Handshake

```text
SERVER -> ENTER_USERNAME
CLIENT -> username

SERVER -> SEND_PUBLIC_KEY
CLIENT -> rsaPublicKey

SERVER -> PUBLIC_KEYS ...
```

---

### Public Key Exchange

```text
NEW_KEY username:publicKey
```

---

### Group Message

```text
MSG sender encryptedMessage KEYS user1:key1|user2:key2
```

---

### Private Message

```text
PM sender encryptedMessage KEY encryptedKey
```

---

### Server Message

```text
SERVER message
```

---

### Error Message

```text
ERROR message
```

---

# ⚠ Current Limitations

* No message history persistence
* No file transfer support
* No image sharing
* No offline messaging
* No authentication/password system
* Public keys are not verified against impersonation attacks
* AES currently uses the default cipher mode (`AES/ECB`) provided by Java's `"AES"` transformation

---

# 🔮 Future Improvements

* AES-GCM authenticated encryption
* Digital signatures for message authenticity
* File transfer support
* User authentication system
* Database-backed message history
* Multiple chat rooms
* Dark/light themes
* Message reactions
* Typing indicators

---

# 🛠 Technologies Used

* Java 17+
* JavaFX
* TCP Sockets
* RSA-2048
* AES-128
* Multithreading
* Concurrent Collections

---

# 📚 Educational Concepts Demonstrated

This project showcases:

* Client-Server Architecture
* TCP Networking
* Socket Programming
* Multithreading
* JavaFX GUI Development
* Public Key Cryptography
* Symmetric Encryption
* Hybrid Encryption Systems
* Concurrent Programming
* Custom Network Protocol Design

---

**Author:** Aza Naiyer
**Project Type:** Educational Secure Chat Application
**Language:** Java
**Encryption:** RSA + AES Hybrid Encryption System 🔐
