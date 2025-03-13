public class PeerProcess {
    public static void main(String[] args) {
        Peer p2p = new Peer(args[0]);
        p2p.start();
    }
}