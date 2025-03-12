import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class Receiver implements Runnable {
    private Socket socket;
    private DataInputStream in;
    private Peer peer;
    private Neighbors neighbors;
    private Bitmap bitmap;
    private FileManager fileManager;
    private Logger logger;

    public Receiver(Socket socket, Peer peer) throws IOException {

    }

    @Override
    public void run() {

    }
}
