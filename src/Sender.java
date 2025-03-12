import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Sender {

    private DataOutputStream out;
    private int peerID;

    public Sender(Socket socket, int peerID) throws IOException {
        this.peerID = peerID;
        this.out = new DataOutputStream(socket.getOutputStream());
    }

}
