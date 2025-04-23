import java.io.RandomAccessFile;

public class FileManager {
    private final String filePath;
    private final int fileSize;
    private final int pieceSize;
    private final boolean containsInitialFile;

    // TODO: Somethign is wrong here idk what
    public FileManager(int peerID, String fileName, int fileSize, int pieceSize, int numPieces, boolean containsInitialFile) {
        this.filePath = "peer_" + peerID + "/" + fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.containsInitialFile = containsInitialFile;
    }

    public synchronized byte[] readPiece(Integer pieceIndex) {
        try (RandomAccessFile sourceFile = new RandomAccessFile(filePath, "r")) {
            long startByte = (long) pieceIndex * pieceSize;
            if (startByte >= sourceFile.length()) {
                System.out.println("Start byte is beyond file size. " + startByte + " " + pieceIndex);
                return new byte[0];
            }

            sourceFile.seek(startByte);
            int bytesToRead = (int) Math.min(pieceSize, sourceFile.length() - startByte); // Handles last piece size
            byte[] buffer = new byte[bytesToRead];
            sourceFile.readFully(buffer);

            return buffer;
        } catch (Exception e) {
            System.out.println("File not found issue with reading piece from file");
            e.printStackTrace();
        }
        return new byte[0];
    }

    public synchronized void writePiece(Integer pieceIndex, byte[] data) {
        if (!containsInitialFile) { // Never write to an original file to avoid problems. This is only for this project
            try (RandomAccessFile outputFile = new RandomAccessFile(filePath, "rw")) {
                long startByte = (long) pieceIndex * pieceSize;
                if (startByte + data.length > outputFile.length()) {
                    outputFile.setLength(startByte + data.length);
                }
                outputFile.seek(startByte);
                outputFile.write(data);
            } catch (Exception e) {
                System.out.println("File not found issue with writing piece to file");
                e.printStackTrace();
            }
        }
    }

}
