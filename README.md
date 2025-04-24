
# P2P File Sharing System

## IMPORTANT NOTE

In the Demo, it will be demonstrated that we completed this project to 100% completion.

## Overview

This project implements a peer-to-peer (P2P) file sharing system in Java. Each peer can download pieces of a file from other peers concurrently. The system uses TCP sockets for communication, custom protocol messages, and multi-threading to handle sending, receiving, and maintaining connections.

The design ensures that all peers receive the complete file through piece exchanges. The system terminates cleanly once all peers have received the full file.

---

## 🔧 How to Compile and Run

### 1. Compile:
```bash
javac *.java
```

### 2. Run:
Each peer must be started with its unique ID as specified in `PeerInfo.cfg`.

```bash
java PeerProcess <peerID>
```

You can automate this using a script (we made a `launch.sh`(mac) `launch_peers.bat`(windows) on both of our devices to auto run) that launches peers 1001 to 1008 in separate terminal windows or background processes.

In both that bat and sh file, the absolute directory is required. In the current submitted files, it is OUR absolute directory. If you would like to test it using those commands, please change the PROJECT_DIR value.

---

## Files and Components

- **PeerProcess.java** – Main class to start a peer
- **Peer.java** – Manages initialization, configuration, and thread spawning
- **PrimaryConnector.java** – Handles socket connection and message handling loop
- **MessageManager.java** – Defines and processes protocol messages
- **Neighbors.java** – Tracks peer status, bitfields, and choking/unchoking
- **Bitmap.java** – Manages piece availability
- **FileManager.java** – Handles file read/write operations
- **Logger.java** – Logs P2P events and peer activities

---

##  Contributors

### Nicholas Hartog
- Implemented core socket and thread coordination logic in `Peer.java` and `PrimaryConnector.java`
- Worked on the protocol message parsing and dispatch in `MessageManager.java`
- Ensured thread safety using `ConcurrentHashMap` and improved error handling
- Created scripts and documentation for command-line execution and peer automation

### Sebastian Paulis
- Designed the logic for neighbor state management in `Neighbors.java`
- Implemented the bitmap piece exchange and choking/unchoking strategy
- Handled file operations and piece assembly in `FileManager.java`
- Refined the logging system for better visibility of system state (`Logger.java`)

---

## Testing and Stability

- The system was tested with various peer counts (3, 6, 9).
- Transitioned to `ConcurrentHashMap` to resolve synchronization issues.
- Shutdown logic is managed gracefully, and data transfer is complete for all peers.

---

## Config Files

- `Common.cfg` – Contains system-wide configuration (file name, piece size, etc.)
- `PeerInfo.cfg` – Contains peer IDs, IP addresses, ports, and file possession flags

Ensure both config files are placed in the working directory before launching the peers.
