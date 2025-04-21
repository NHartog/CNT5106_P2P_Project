import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class FileManager {
    private int peerID;
    private String filePath;
    private int fileSize;
    private int pieceSize;
    private int numPieces;
    private boolean containsInitialFile;

    public FileManager(int peerID, String fileName, int fileSize, int pieceSize, int numPieces, boolean containsInitialFile) {
        this.peerID = peerID;
        this.filePath = "peer_" + peerID + "/" + fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.numPieces = numPieces;
        this.containsInitialFile = containsInitialFile;

        if (!containsInitialFile) {
            intializeFileLength();
        }
    }

    private synchronized void intializeFileLength() {
        try (RandomAccessFile outputFile = new RandomAccessFile(filePath, "rw")) {
            outputFile.setLength(fileSize);
        } catch (Exception e) {
            System.out.println("File not found issue with initializing file length");
            e.printStackTrace();
        }
    }

    public synchronized byte[] readPiece(Integer pieceIndex) {
        try (RandomAccessFile sourceFile = new RandomAccessFile(filePath, "r")) {
            long startByte = (long) pieceIndex * pieceSize;
            if (startByte >= sourceFile.length()) {
                System.out.println("Start byte is beyond file size.");
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
        if (!containsInitialFile) { // Never write to a an original file to avoid problems. This is only for this project
            try (RandomAccessFile outputFile = new RandomAccessFile(filePath, "rw")) {
                outputFile.seek(pieceIndex + pieceSize);
                outputFile.write(data);
            } catch (Exception e) {
                System.out.println("File not found issue with writing piece to file");
                e.printStackTrace();
            }
        }
    }

}
