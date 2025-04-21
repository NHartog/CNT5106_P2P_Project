import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;

public class Neighbors {
    private Peer peer;
    private final HashMap<Integer, Socket> connectedPeers = new HashMap<>();
    private final HashMap<Integer, Bitmap> peerBitmaps = new HashMap<>();
    private final HashMap<Integer, Integer> numOfPiecesByPeer = new HashMap<>();
    private final Set<Integer> preferredNeighbors = new HashSet<>();
    private final Set<Integer> interestedNeighbors = new HashSet<>();
    private final Set<Integer> chokedStatus = new HashSet<>();
    private final Set<Integer> hasCompleteFileNeighbors = new HashSet<>();
    private volatile Integer optimisticNeighbor = null;

    Neighbors(Peer peer) {
        this.peer = peer;

        for(Peer.PeerInfo info : peer.getAllPeerInfo()) {
            if(peer.getPeerInfo().getPeerID() == info.getPeerID()) {
                continue; // skip current peer
            }
            chokedStatus.add(info.getPeerID()); // all neighbors start choked
        }
    }

    public synchronized void addNeighbor(int neighborID, Socket socket) {
        connectedPeers.put(neighborID, socket);
    }

    public synchronized void updatePeerBitfield(int peerID, Bitmap bitmap) {
        peerBitmaps.put(peerID, bitmap);
        updateCompleteFileNeighbors();
    }

    public synchronized void updatePeerBitfield(int peerID, int index) {
        peerBitmaps.get(peerID).markPieceAsReceived(index);
        updateCompleteFileNeighbors();
    }

    public synchronized Bitmap getPeerBitfield(int peerID) {
        return peerBitmaps.get(peerID);
    }

    public synchronized Map<Integer, Socket> getConnectedPeers() {
        return connectedPeers;
    }

    public synchronized void updatePreferredNeighbors(List<Integer> newPreferred) {
        preferredNeighbors.clear();
        preferredNeighbors.addAll(newPreferred);
    }

    public synchronized boolean isPreferredNeighbor(int peerID) {
        return preferredNeighbors.contains(peerID);
    }

    public synchronized Set<Integer> getPreferredNeighbors() {
        return preferredNeighbors;
    }

    public synchronized Integer getOptimisticNeighbor() {
        return optimisticNeighbor;
    }

    public synchronized void setOptimisticNeighbor(Integer optimisticNeighbor) {
        this.optimisticNeighbor = optimisticNeighbor;
    }

    public synchronized Set<Integer> getInterestedNeighbors() {
        return interestedNeighbors;
    }

    public synchronized void setInterestOfNeighbor(Integer peerID, boolean isInterested) {
        Consumer<Integer> action = isInterested ? interestedNeighbors::add : interestedNeighbors::remove;
        action.accept(peerID);
    }

    // Returns a set of peer IDs that represent the neighbors that view me as a choked neighbor
    public synchronized Set<Integer> getChokedStatus() {
        return chokedStatus;
    }

    public synchronized void setChokedStatus(Integer peerID, boolean isChoked) {
        Consumer<Integer> action = isChoked ? chokedStatus::add : chokedStatus::remove;
        action.accept(peerID);
    }

    public synchronized HashMap<Integer, Integer> getNumOfPiecesByPeer() {
        return numOfPiecesByPeer;
    }

    public synchronized void incrementNumOfPiecesByPeer(Integer peerID) {
        numOfPiecesByPeer.merge(peerID, 1, Integer::sum);
    }

    public synchronized void resetNumOfPiecesByPeer() {
        numOfPiecesByPeer.clear();
    }

    public synchronized Set<Integer> getHasCompleteFileNeighbors() {
        return hasCompleteFileNeighbors;
    }

    public synchronized void updateCompleteFileNeighbors() {
        for (Map.Entry<Integer, Bitmap> entry : peerBitmaps.entrySet()) {
            Integer peerID = entry.getKey();
            Bitmap bitmap = entry.getValue();

            if (bitmap.hasAllPieces()) {
                setHasCompleteFileNeighbors(peerID);
            }
        }
    }

    public synchronized void setHasCompleteFileNeighbors(Integer peerID) {
        hasCompleteFileNeighbors.add(peerID);
    }

    public synchronized void sendHaveMessages(Integer pieceIndex) {
        for(Map.Entry<Integer, Socket> connection : connectedPeers.entrySet()) {
            Integer peerID = connection.getKey();

            if (peerID == peer.getPeerInfo().getPeerID()){ // Skip the current peer
                continue;
            }

            Socket socket = connection.getValue();
            try (DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
                peer.getMessageManager().sendHave(outputStream, pieceIndex);
            } catch (IOException e) {
                System.out.println("Something happened with sending have messages");
                e.printStackTrace();
            }
        }
    }

    public synchronized Set<Integer> allChokedAndInterestedNeighbors(){
        Set<Integer> intersection = new HashSet<>(chokedStatus); // Start with all currently choked neighbors
        intersection.retainAll(interestedNeighbors);  // keep only IDs that are also interested
        return intersection;
    }
}


