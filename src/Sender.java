import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Sender implements Runnable {

    private DataOutputStream out;
    private Peer peer;

    public Sender(Socket socket, Peer peer) throws IOException {
        this.peer = peer;
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void run() {

    }
}
