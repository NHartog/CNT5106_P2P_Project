public class PeerProcess {
    public static void main(String[] args) throws InterruptedException {
        Peer p2p = new Peer(args[0]);
        p2p.start();
    }
}