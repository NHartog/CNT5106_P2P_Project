public class FileManager {
    private int peerID;
    private String filePath;
    private int fileSize;
    private int pieceSize;

    public FileManager(int peerID, String fileName, int fileSize, int pieceSize) {
        this.peerID = peerID;
        this.filePath = "peer_" + peerID + "/" + fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
    }
}
