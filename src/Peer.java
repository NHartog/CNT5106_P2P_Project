import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Peer {

    public static class PeerInfo {
        public int getPeerID() {
            return peerID;
        }

        public void setPeerID(int peerID) {
            this.peerID = peerID;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        private int peerID;
        private String hostname;
        private int port;
    }

    private final PeerInfo peerInfo = new PeerInfo();
    private boolean hasFile;
    private int numPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private int fileSize;
    private int pieceSize;
    private int numPieces;

    private final Neighbors neighbors;
    private final Logger logger;
    private final Bitmap bitmap;
    private final FileManager fileManager;

    //match senders to ids to track can talk about this
    private final List<PeerInfo> peers = new ArrayList<>();
    private ServerSocket serverSocket;

    public Peer(String ID) {
        peerInfo.setPeerID(Integer.parseInt(ID));
        loadCommonConfig();
        loadPeerInfo();

        initializeServerSocket();

        this.logger = new Logger(peerInfo.getPeerID());
        this.bitmap = new Bitmap(numPieces, hasFile);
        this.fileManager = new FileManager(peerInfo.getPeerID(), fileName, fileSize, pieceSize);
        this.neighbors = new Neighbors(peerInfo.getPeerID(), logger);
    }

    private void initializeServerSocket() {
        try {
            serverSocket = new ServerSocket(peerInfo.getPort());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("\nShutdown hook triggered. Closing server...");
                    serverSocket.close(); // Interrupts accept()
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadCommonConfig() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("./Common.cfg"));
            for (String line : lines) {
                String[] tokens = line.split(" ");
                switch (tokens[0]) {
                    case "NumberOfPreferredNeighbors":
                        numPreferredNeighbors = Integer.parseInt(tokens[1]);
                        break;
                    case "UnchokingInterval":
                        unchokingInterval = Integer.parseInt(tokens[1]);
                        break;
                    case "OptimisticUnchokingInterval":
                        optimisticUnchokingInterval = Integer.parseInt(tokens[1]);
                        break;
                    case "FileName":
                        fileName = tokens[1];
                        break;
                    case "FileSize":
                        fileSize = Integer.parseInt(tokens[1]);
                        break;
                    case "PieceSize":
                        pieceSize = Integer.parseInt(tokens[1]);
                        break;
                }
            }
            numPieces = (int) Math.ceil((double) fileSize / pieceSize);
        } catch (IOException e) {
            System.out.println("Error reading common.cfg");
            System.out.println(e);
        }
    }

    private void loadPeerInfo() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("./PeerInfo.cfg"));
            for (String line : lines) {
                String[] tokens = line.split(" ");
                int id = Integer.parseInt(tokens[0]);

                if (id > peerInfo.getPeerID()) { // If past current peer, break
                    break;
                } else if (id == peerInfo.getPeerID()) { // If at current peer, get info and break
                    peerInfo.setHostname(tokens[1]);
                    peerInfo.setPort(Integer.parseInt(tokens[2]));
                    hasFile = tokens[3].equals("1");
                    break;
                } else { // Get general peer information for all peers above current id
                    PeerInfo info = new PeerInfo();
                    info.setPeerID(id);
                    info.setHostname(tokens[1]);
                    info.setPort(Integer.parseInt(tokens[2]));
                    peers.add(info);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading PeerInfo.cfg");
            e.printStackTrace();
        }
    }

    private void createSenders() {
        for (PeerInfo peer : peers) {
            try (Socket requestSocket = new Socket(peer.getHostname(), peer.getPort())) {
                Thread senderThread = new Thread(new Sender(requestSocket, this));
                safelyStartThread(senderThread);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createReceivers() {
        Thread newConnectionsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("\nCreating Receiver Thread");
                    Thread thread = new Thread(new Receiver(socket, this));
                    thread.start();
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        System.out.println("\nShutdown hook triggered. Cleaning up receiver thread...");
                        thread.interrupt(); // Signal the worker thread to stop
                        try {
                            thread.join(); // Wait for thread to terminate
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        System.out.println("Cleanup completed. Exiting.");
                    }));
                } catch (SocketException e) {
                    System.out.println("Socket Exception: " + e.getMessage());
                } catch (SocketTimeoutException e) {
                    System.out.println("No client connected within timeout. Retrying...");
                } catch (IOException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        });
        safelyStartThread(newConnectionsThread);
    }

    public void start() {
        //Create Senders AKA Client threads
        createSenders();
        //Create Receivers AKA Server threads
        createReceivers();
    }

    private void safelyStartThread(Thread thread){
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown hook triggered. Cleaning up thread...");
            thread.interrupt(); // Signal the worker thread to stop
            try {
                thread.join(); // Wait for thread to terminate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Cleanup completed. Exiting.");
        }));
    }
}
