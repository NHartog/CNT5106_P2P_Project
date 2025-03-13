import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Sender implements Runnable {

    private final DataOutputStream outputStream;
    private final DataInputStream inputStream;
    private final Socket socket;
    private final Peer peer;
    private final int expectedPeerID;

    public Sender(Socket socket, Peer peer, int expectedPeerID) throws IOException {
        this.peer = peer;
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.expectedPeerID = expectedPeerID;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Handshake
            // Send Shake Message
            peer.getMessageManager().sendHandshakeMessage(outputStream);
            // Receive Shake Message
            int connectedPeerID = peer.getMessageManager().receivedValidHandshakeMessage(inputStream, expectedPeerID);
            if (connectedPeerID == -1) {
                return;
            }
            // Handshake Done
            peer.getLogger().logMakesConnectionTCP(connectedPeerID);

            // Exchange Bitmaps
            peer.getMessageManager().sendBitmap(outputStream);
            Bitmap bitmap = peer.getMessageManager().receiveBitmap(inputStream);

            System.out.println(Arrays.toString(bitmap.getBitfield()));

        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }
}
