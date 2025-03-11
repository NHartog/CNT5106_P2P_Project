
public class Logger {
    private String logFile;

    public Logger(int peerID) {
        this.logFile = "log_peer_" + peerID + ".log";
    }
}
