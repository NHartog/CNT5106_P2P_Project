import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Logger {
    private final String logFile;
    private final int peerID;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Logger(int peerID) {
        this.logFile = "log_peer_" + peerID + ".log";
        this.peerID = peerID;
    }

    public void log(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(message);
            writer.newLine();
            System.out.println(message);
        } catch (IOException e) {
            System.out.println("Error writing to log file: " + logFile);
        }
    }

    public void logTime(String message) {
        log(String.format("%s: %s", formatter.format(LocalDateTime.now()), message));
    }

    // TCP Connection
    public void logMakesConnectionTCP(int connectedPeerID) {
        logTime(String.format("Peer %d makes a connection to Peer %d.", peerID, connectedPeerID));
    }

    public void logConnectedFromTCP(int connectedPeerID) {
        logTime(String.format("Peer %d is connected from Peer %d", peerID, connectedPeerID));
    }

    // Change of Preferred Neighbors
    public void logPreferredNeighbors(ArrayList<Integer> preferredPeerIDs) {
        String IDs = String.join(" ", preferredPeerIDs.stream().map(String::valueOf).toList());
        logTime(String.format("Peer %d has the preferred neighbors %s.", peerID, IDs));
    }

    // Change of Optimistically Unchoked Neighbor
    public void logUnchokedNeighbor(int unchokedPeerID) {
        logTime(String.format("Peer %d has the optimistically unchoked neighbor %d.", peerID, unchokedPeerID));
    }

    // Unchoking/Choking
    public void logUnchoking(int unchokedPeerID) {
        logTime(String.format("Peer %d is unchoked by %d.", peerID, unchokedPeerID));
    }

    public void logChoking(int chokedPeerID) {
        logTime(String.format("Peer %d is choked by %d.", peerID, chokedPeerID));
    }

    // Receiving [have/interest/not interested] message
    public void logReceivedHave(int connectedPeerID, int pieceIndex) {
        logTime(String.format("Peer %d received the ‘have’ message from %d for the piece %d.", peerID, connectedPeerID, pieceIndex));
    }

    public void logReceivedInterested(int connectedPeerID) {
        logTime(String.format("Peer %d received the ‘interested’ message from %d.", peerID, connectedPeerID));
    }

    public void logReceivedNotInterested(int connectedPeerID) {
        logTime(String.format("Peer %d received the ‘not interested’ message from %d.", peerID, connectedPeerID));
    }

    // Downloading a Piece
    public void logDownloadedPiece(int connectedPeerID, int pieceIndex, int numberOfPieces) {
        logTime(String.format("Peer %d has downloaded the piece %d from %d. Now the number of pieces it has is %d.", peerID, pieceIndex, connectedPeerID, numberOfPieces));
    }

    // Completion of Download
    public void logDownloadedFile(int connectedPeerID) {
        logTime(String.format("Peer %d has downloaded the complete file.", peerID));
    }
}
