import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MessageManager {
    private final Peer peer;

    MessageManager(Peer peer) {
        this.peer = peer;
    }

    public void sendMessage(DataOutputStream out, byte[] content) {
        try {
            out.write(content);
            out.flush();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public void sendHandshakeMessage(DataOutputStream out) {
        sendMessage(out, getHandshakeMessage());
    }

    public int receivedValidHandshakeMessage(DataInputStream in, int... validPeerIDs){
        byte[] buffer = new byte[32];
        try {
            int bytesRead = in.read(buffer);
            boolean correctLength = bytesRead == 32;
            boolean correctHeader = Peer.PeerInfo.HEADER.equals(getHeaderFromHandshakeMessage(buffer));

            // A valid Peer is considered a peer that is expected for a connection
            int peerFromHandshake = getPeerIDFromHandshake(buffer);
            boolean validPeer = Arrays.stream(validPeerIDs).anyMatch(id -> id == peerFromHandshake);

            return correctLength && correctHeader && validPeer ? peerFromHandshake : -1;
        } catch (IOException e) {
            System.out.println(e);
            return -1;
        }
    }

    public byte[] getHandshakeMessage() {
        byte[] strBytes = Peer.PeerInfo.HEADER.getBytes(StandardCharsets.UTF_8);

        byte[] zeroBytes = ByteBuffer.allocate(10).array();

        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(peer.getPeerInfo().getPeerID());
        byte[] intBytes = buffer.array();

        byte[] result = new byte[32];

        System.arraycopy(strBytes, 0, result, 0, 18);
        System.arraycopy(zeroBytes, 0, result, 18, 10);
        System.arraycopy(intBytes, 0, result, 28, 4);

        return result;
    }

    public String getHeaderFromHandshakeMessage(byte[] incomingMessage) {
        return new String(incomingMessage, 0, 18, StandardCharsets.UTF_8);
    }

    public int getPeerIDFromHandshake(byte[] incomingMessage) {
        return ByteBuffer.wrap(incomingMessage, 28, 4).getInt();
    }
}
