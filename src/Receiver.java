import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class Receiver implements Runnable {
    private Socket socket;
    private DataInputStream in;
    private Peer receivingPeer;
    private Peer sendingPeer;
    private Neighbors neighbors;
    private Bitmap bitmap;
    private FileManager fileManager;
    private Logger logger;

    public Receiver(Socket socket, Peer peer) throws IOException {
        this.socket = socket;
        this.receivingPeer = peer;
        // TODO: initialize sendingPeer with the three way handshake handling
    }

    @Override
    public void run() {

    }
}
