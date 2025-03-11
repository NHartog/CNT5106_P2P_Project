//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class PeerProcessing {
    public static void main(String[] args) {

        Peer p2p = new Peer(args[0]);
        p2p.start();
    }
}