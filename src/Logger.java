import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class Logger {
    private String logFile;

    public Logger(int peerID) {
        this.logFile = "log_peer_" + peerID + ".log";
    }

    public void log(String message) {
        String timestampedMessage = LocalDateTime.now() + ": " + message;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(timestampedMessage);
            writer.newLine();
        } catch (IOException e) {
            System.out.println("Error writing to log file: " + logFile);
        }
    }
}
