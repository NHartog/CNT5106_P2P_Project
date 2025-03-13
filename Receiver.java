import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;

public class Receiver implements Runnable {

    private final DataOutputStream outputStream;
    private final DataInputStream inputStream;
    private final Socket socket;
    private final Peer peer;

    public Receiver(Socket socket, Peer peer) throws IOException {
        this.peer = peer;
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Handshake (Flipped from Sender)
            // Receive Shake Message (The stream handling ensures the expected peer id is any peer not already connected)
            HashSet<Integer> peers = peer.getConnectedPeers();
            int connectedPeerID = peer.getMessageManager().receivedValidHandshakeMessage(inputStream, peer.getOtherPeerInfo()
                    .stream()
                    .map(Peer.PeerInfo::getPeerID)
                    .filter(peerID -> !peers.contains(peerID))
                    .mapToInt(Integer::intValue)
                    .toArray()
            );
            if (connectedPeerID == -1) {
                return;
            }
            // Send Shake Message
            peer.getMessageManager().sendHandshakeMessage(outputStream);
            // Handshake Done (Note, the connected peers needs to be updated)
            peer.getConnectedPeers().add(connectedPeerID);
            peer.getLogger().logConnectedFromTCP(connectedPeerID);

        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }
}
