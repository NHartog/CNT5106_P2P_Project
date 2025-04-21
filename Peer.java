import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

        public final static String HEADER = "P2PFILESHARINGPROJ";
        private int peerID;
        private String hostname;
        private int port;
    }

    public PeerInfo getPeerInfo() {
        return peerInfo;
    }

    public boolean isHasFile() {
        return hasFile;
    }

    public int getNumPreferredNeighbors() {
        return numPreferredNeighbors;
    }

    public int getUnchokingInterval() {
        return unchokingInterval;
    }

    public int getOptimisticUnchokingInterval() {
        return optimisticUnchokingInterval;
    }

    public String getFileName() {
        return fileName;
    }

    public int getFileSize() {
        return fileSize;
    }

    public int getPieceSize() {
        return pieceSize;
    }

    public int getNumPieces() {
        return numPieces;
    }

    public Neighbors getNeighbors() {
        return neighbors;
    }

    public Logger getLogger() {
        return logger;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public List<PeerInfo> getAllPeerInfo() {
        return peers;
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
    private final MessageManager messageManager;

    //match senders to ids to track can talk about this
    private final List<PeerInfo> peers = new ArrayList<>();
    private int indexInConfig = 0;
    private int numConnectedPeers = 0;
    private ServerSocket serverSocket;

    public Peer(String ID) {
        peerInfo.setPeerID(Integer.parseInt(ID));
        loadCommonConfig();
        loadPeerInfo();

        initializeServerSocket();

        this.logger = new Logger(peerInfo.getPeerID());
        this.bitmap = new Bitmap(numPieces, hasFile);
        this.fileManager = new FileManager(peerInfo.getPeerID(), fileName, fileSize, pieceSize, numPieces, hasFile);
        this.neighbors = new Neighbors(this);
        this.messageManager = new MessageManager(this);
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
            numPieces = (int) Math.ceil((double) fileSize / (double) pieceSize);
        } catch (IOException e) {
            System.out.println("Error reading common.cfg");
            System.out.println(e);
        }
    }

    private void loadPeerInfo() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("./PeerInfo.cfg"));
            int cnt = 0;
            for (String line : lines) {
                String[] tokens = line.split(" ");
                int id = Integer.parseInt(tokens[0]);

                // Note, all information from the peer info file is kept here and in order of the file,
                // including the current peer. This order is important when making sockets
                PeerInfo info = new PeerInfo();
                info.setPeerID(id);
                info.setHostname(tokens[1]);
                info.setPort(Integer.parseInt(tokens[2]));
                peers.add(info);

                if (id == peerInfo.getPeerID()) { // If at current peer, get info just for local use (duplicate info)
                    peerInfo.setHostname(tokens[1]);
                    peerInfo.setPort(Integer.parseInt(tokens[2]));
                    hasFile = tokens[3].equals("1");

                    // Note, we will keep track of the index in this ordered list so that we can use it for handling
                    // connections when receiving incoming connections and which peer to expect.
                    indexInConfig = cnt;
                }
                cnt++;
            }
        } catch (IOException e) {
            System.out.println("Error reading PeerInfo.cfg");
            e.printStackTrace();
        }
    }

    private void createSenders() {
        for (PeerInfo expectedPeer : peers) {
            if (expectedPeer.getPeerID() == peerInfo.getPeerID()) {
                // If we've reached the current peer, that means we've iterated over all the peers before the current one
                // from the perspective of the PeerInfo.cfg file. In that case, we've made all the possible connections.
                break;
            }
            try {
                Socket socket = new Socket(expectedPeer.getHostname(), expectedPeer.getPort());
                Thread thread = new Thread(new PrimaryConnector(socket, this, expectedPeer.getPeerID(), true));

                neighbors.addNeighbor(expectedPeer.getPeerID(), socket);

                safelyStartThread(thread);
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

                    // This is the expected peer because we should only receive new connections from peers that appear
                    // after this peer in the config file. Therefore, it would be the index of this peer plus the
                    // number of connected peers + 1. The + 1 describes the "next" peer to be expected.
                    PeerInfo expectedPeer = peers.get(indexInConfig + numConnectedPeers + 1);
                    numConnectedPeers++;

                    Thread thread = new Thread(new PrimaryConnector(socket, this, expectedPeer.getPeerID(), false));

                    neighbors.addNeighbor(expectedPeer.getPeerID(), socket);

                    safelyStartThread(thread);
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

    private void createPNHandler() {
        Thread newConnectionsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                try {
                    TimeUnit.SECONDS.sleep(unchokingInterval);
                } catch (InterruptedException e) {
                    System.out.println("Interrupted while waiting to reselect preferred neighbors");
                    e.printStackTrace();
                    System.exit(0);
                }

                List<Integer> preferredPeerIDs = neighbors.getNumOfPiecesByPeer().entrySet()
                        .stream()
                        // Only look at the analytics of interested neighbors and their statistics
                        .filter((Map.Entry<Integer, Integer> entry) -> neighbors.getInterestedNeighbors().contains(entry.getKey()))
                        // Sort them so that the greatest values are first
                        .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                        // Only get the number of neighbors from the config file
                        .limit(numPreferredNeighbors)
                        .map(Map.Entry::getKey)
                        .toList();

                // Reset the statistics for use next time
                neighbors.resetNumOfPiecesByPeer();

                // If a previous preferred is still preferred -> do nothing
                // If a previous non-preferred is still non-preferred -> do nothing

                // If a previous non-preferred is now preferred -> send unchoke
                for(Integer preferredPeerID: preferredPeerIDs) {
                    if (neighbors.isPreferredNeighbor(preferredPeerID)) {
                        continue; // We don't need to do anything if the neighbor is already preferred
                    }

                    // Send unchoke message
                    try (DataOutputStream outputStream = new DataOutputStream(neighbors.getConnectedPeers().get(preferredPeerID).getOutputStream())) {
                        messageManager.sendUnchoke(outputStream);
                    } catch (Exception e) {
                        System.out.println("Something happened when unchoking new preferred neighbor");
                        e.printStackTrace();
                    }
                }

                // If a previous preferred is no longer preferred -> send choke
                for(Integer previouslyPreferredID: neighbors.getPreferredNeighbors()) {
                    if (!preferredPeerIDs.contains(previouslyPreferredID)) {
                        try (DataOutputStream outputStream = new DataOutputStream(neighbors.getConnectedPeers().get(previouslyPreferredID).getOutputStream())) {
                            messageManager.sendChoke(outputStream);
                        } catch (Exception e) {
                            System.out.println("Something happened when choking new preferred neighbor");
                            e.printStackTrace();
                        }
                    }
                }

                neighbors.updatePreferredNeighbors(preferredPeerIDs);
            }
        });
        safelyStartThread(newConnectionsThread);
    }

    private void createONHandler() {
        Thread newConnectionsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                try {
                    TimeUnit.SECONDS.sleep(optimisticUnchokingInterval);
                } catch (InterruptedException e) {
                    System.out.println("Interrupted while waiting to reselect optimistic neighbor");
                    e.printStackTrace();
                    System.exit(0);
                }

                List<Integer> ids = neighbors.allChokedAndInterestedNeighbors().stream().toList();
                if (ids.isEmpty()) {
                    continue;
                }
                Random random = new Random();
                int newPeerIndex = random.nextInt(ids.size());
                int newPeerID = ids.get(newPeerIndex);

                int oldPeerID = neighbors.getOptimisticNeighbor() != null ? neighbors.getOptimisticNeighbor() : -1;
                if (oldPeerID != -1) {
                    try (DataOutputStream outputStream = new DataOutputStream(neighbors.getConnectedPeers().get(oldPeerID).getOutputStream())) {
                        messageManager.sendChoke(outputStream);
                    } catch (Exception e) {
                        System.out.println("Something happened when choking previous optimistic neighbor");
                        e.printStackTrace();
                    }
                }

                try (DataOutputStream outputStream = new DataOutputStream(neighbors.getConnectedPeers().get(newPeerID).getOutputStream())) {
                    messageManager.sendUnchoke(outputStream);
                } catch (Exception e) {
                    System.out.println("Something happened when unchoking previous optimistic neighbor");
                    e.printStackTrace();
                }

                neighbors.setOptimisticNeighbor(newPeerID);
            }
        });
        safelyStartThread(newConnectionsThread);
    }

    public void start() {
        createPNHandler();
        createONHandler();
        //Create Senders AKA Client threads
        createSenders();
        //Create Receivers AKA Server threads
        createReceivers();
    }

    private void safelyStartThread(Thread thread) {
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
