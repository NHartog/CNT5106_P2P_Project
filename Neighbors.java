import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Neighbors {
    private Peer peer;
    private final HashMap<Integer, Socket> connectedPeers = new HashMap<>();
    private final HashMap<Integer, Bitmap> peerBitmaps = new HashMap<>();
    private final HashMap<Integer, Integer> numOfPiecesByPeer = new HashMap<>();
    // This set tracks whether the current peer is already interested in any neighbors
    private final HashMap<Integer, String> interestingNeighbors = new HashMap<>();
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
            interestingNeighbors.put(info.getPeerID(), null);
            peerBitmaps.put(info.getPeerID(), new Bitmap(new byte[0], peer.getNumPieces()));
            chokedStatus.add(info.getPeerID()); // all neighbors start choked
            numOfPiecesByPeer.put(info.getPeerID(), 0);
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

    public Bitmap getPeerBitfield(int peerID) {
        return peerBitmaps.get(peerID);
    }

    public Map<Integer, Socket> getConnectedPeers() {
        return connectedPeers;
    }

    public synchronized void updatePreferredNeighbors(List<Integer> newPreferred) {
        preferredNeighbors.clear();
        preferredNeighbors.addAll(newPreferred);
    }

    public boolean isPreferredNeighbor(int peerID) {
        return preferredNeighbors.contains(peerID);
    }

    public Set<Integer> getPreferredNeighbors() {
        return preferredNeighbors;
    }

    public Integer getOptimisticNeighbor() {
        return optimisticNeighbor;
    }

    public synchronized void setOptimisticNeighbor(Integer optimisticNeighbor) {
        this.optimisticNeighbor = optimisticNeighbor;
    }

    public Set<Integer> getInterestedNeighbors() {
        return interestedNeighbors;
    }

    public Set<Integer> getInterestingNeighbors() {
        return interestingNeighbors.entrySet().stream()
                .filter(entry -> entry.getValue() != null && "interesting".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<Integer> getNotInterestingNeighbors() {
        return interestingNeighbors.entrySet().stream()
                .filter(entry -> entry.getValue() != null && "not interesting".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public synchronized void setInterestOfNeighbor(Integer peerID, boolean isInterested) {
        Consumer<Integer> action = isInterested ? interestedNeighbors::add : interestedNeighbors::remove;
        action.accept(peerID);
    }

    public synchronized void setInterestingNeighbor(Integer peerID, boolean isInteresting) {
        interestingNeighbors.put(peerID, isInteresting ? "interesting" : "not interesting");
    }

    // Returns a set of peer IDs that represent the neighbors that view me as a choked neighbor
    public Set<Integer> getChokedStatus() {
        return chokedStatus;
    }

    public synchronized void setChokedStatus(Integer peerID, boolean isChoked) {
        Consumer<Integer> action = isChoked ? chokedStatus::add : chokedStatus::remove;
        action.accept(peerID);
    }

    public HashMap<Integer, Integer> getNumOfPiecesByPeer() {
        return numOfPiecesByPeer;
    }

    public synchronized void incrementNumOfPiecesByPeer(Integer peerID) {
        numOfPiecesByPeer.merge(peerID, 1, Integer::sum);
    }

    public synchronized void resetNumOfPiecesByPeer() {
        numOfPiecesByPeer.clear();
        for(Peer.PeerInfo info : peer.getAllPeerInfo()) {
            if(peer.getPeerInfo().getPeerID() == info.getPeerID()) {
                continue; // skip current peer
            }
            numOfPiecesByPeer.put(info.getPeerID(), 0);
        }
    }

    public Set<Integer> getHasCompleteFileNeighbors() {
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

            peer.getMessageManager().sendHave(peerID, pieceIndex);
        }
    }

    public synchronized void sendNotInterestedMessages() {
        for(Map.Entry<Integer, Socket> connection : connectedPeers.entrySet()) {
            Integer peerID = connection.getKey();

            if (peerID == peer.getPeerInfo().getPeerID()){ // Skip the current peer
                continue;
            }

            if (!peer.getBitmap().containsInterestedPieces(getPeerBitfield(peerID))) {
                peer.getMessageManager().sendNotInterested(peerID);
            }
        }
    }

    public Set<Integer> allChokedAndInterestedNeighbors(){
        Set<Integer> unchoked = new HashSet<>(preferredNeighbors);
        unchoked.add(optimisticNeighbor);
        Set<Integer> choked = peer.getAllPeerInfo().stream().map((Peer.PeerInfo::getPeerID)).collect(Collectors.toSet());
        choked.removeAll(unchoked);

        Set<Integer> intersection = new HashSet<>(choked); // Start with all currently choked neighbors
        intersection.retainAll(interestedNeighbors);  // keep only IDs that are also interested
        intersection.retainAll(connectedPeers.keySet()); // only look at the neighbors that you are actually connected to
        return intersection;
    }
}


