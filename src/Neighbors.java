import java.io.IOException;
import java.net.Socket;
import java.util.*;

public class Neighbors {
    private int peerID;
    private Logger logger;
    private HashMap<Integer, Socket> connectedPeers = new HashMap<>();
    private Set<Integer> preferredNeighbors = new HashSet<>();

    public Neighbors(int peerID, Logger logger) {
        this.peerID = peerID;
        this.logger = logger;
    }
}
