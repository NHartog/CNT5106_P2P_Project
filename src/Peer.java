import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

public class Peer {

    private int peerID;
    private String hostname;
    private int port;
    private boolean hasFile;
    private int numPreferredNeighbors;
    private int unchokingInterval;
    private int optimisticUnchokingInterval;
    private String fileName;
    private int fileSize;
    private int pieceSize;
    private int numPieces;

    private Neighbors neighbors;
    private Logger logger;
    private Bitmap bitmap;
    private FileManager fileManager;

    private ServerSocket serverSocket;
    //match senders to ids to track can talk about this
    private HashMap<Integer, Sender> senders = new HashMap<>();
    private List<Integer> peerIDs = new ArrayList<>();

    public Peer(String ID) {
        peerID = Integer.parseInt(ID);
        loadCommonConfig();
        loadPeerInfo();

        this.logger = new Logger(peerID);
        this.bitmap = new Bitmap(numPieces, hasFile);
        this.fileManager = new FileManager(peerID, fileName, fileSize, pieceSize);
        this.neighbors = new Neighbors(peerID, logger);

    }

    private void loadCommonConfig() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("Common.cfg"));
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
        }
    }

    private void loadPeerInfo() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("PeerInfo.cfg"));
            for (String line : lines) {
                String[] tokens = line.split(" ");
                int id = Integer.parseInt(tokens[0]);
                peerIDs.add(id);  // ✅ Now we correctly populate peerIDs

                if (id == peerID) { // ✅ Assigns details for the current peer
                    hostname = tokens[1];
                    port = Integer.parseInt(tokens[2]);
                    hasFile = tokens[3].equals("1");
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading PeerInfo.cfg");
        }
    }


    public void start() {

    }

}
