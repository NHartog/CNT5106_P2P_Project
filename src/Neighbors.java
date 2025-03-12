import java.io.IOException;
import java.net.Socket;
import java.util.*;

public class Neighbors {
    private int peerID;
    private Logger logger;
    private HashMap<Integer, Socket> connectedPeers = new HashMap<>();
    private HashMap<Integer, BitSet> peerBitfields = new HashMap<>();
    private Set<Integer> preferredNeighbors = new HashSet<>();

    public Neighbors(int peerID, Logger logger) {
        this.peerID = peerID;
        this.logger = logger;
    }

    public void addNeighbor(int neighborID, Socket socket) {
        connectedPeers.put(neighborID, socket);
        logger.log("Added neighbor: " + neighborID);
    }

    public void removeNeighbor(int neighborID) {
        connectedPeers.remove(neighborID);
        peerBitfields.remove(neighborID);  // Remove their bitfield too
        logger.log("Removed neighbor: " + neighborID);
    }

    public void updatePeerBitfield(int peerID, byte[] bitfieldData) {
        BitSet bitfield = BitSet.valueOf(bitfieldData);
        peerBitfields.put(peerID, bitfield);
        logger.log("Updated bitfield for Peer " + peerID + ": " + bitfield);
    }

    public BitSet getPeerBitfield(int peerID) {
        return peerBitfields.get(peerID);
    }

    public Set<Integer> getConnectedPeers() {
        return connectedPeers.keySet();
    }

    public void updatePreferredNeighbors(List<Integer> newPreferred) {
        preferredNeighbors.clear();
        preferredNeighbors.addAll(newPreferred);
        logger.log("Updated preferred neighbors: " + preferredNeighbors);
    }

    public boolean isPreferredNeighbor(int peerID) {
        return preferredNeighbors.contains(peerID);
    }

}


