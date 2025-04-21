import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class PrimaryConnector implements Runnable {

    private final DataOutputStream outputStream;
    private final DataInputStream inputStream;
    private final Socket socket;
    private final Peer peer;
    private final int expectedPeerID;
    private final boolean madeTCPConnection;

    public PrimaryConnector(Socket socket, Peer peer, int expectedPeerID, boolean madeTCPConnection) throws IOException {
        this.peer = peer;
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.expectedPeerID = expectedPeerID;
        this.socket = socket;

        // This tracks whether this was an incoming connection or whether this was a created connection. This is the
        // initial concept of Sender/Receiver. This only matters for logging purposes during the handshake. Besides that,
        // it is pointless
        this.madeTCPConnection = madeTCPConnection;
    }

    @Override
    public void run() {
        try {
            // Handshake
            // Send Shake Message
            peer.getMessageManager().sendHandshakeMessage(outputStream);
            // Receive Shake Message
            int connectedPeerID = peer.getMessageManager().receivedValidHandshakeMessage(inputStream, expectedPeerID);
            if (connectedPeerID == -1) return;

            // Log Handshake Done
            if (madeTCPConnection) {
                peer.getLogger().logMakesConnectionTCP(connectedPeerID);
            } else {
                peer.getLogger().logConnectedFromTCP(connectedPeerID);
            }

            // Send Bitmap
            peer.getMessageManager().sendBitmap(outputStream);

            System.out.println("Peer Info Size" + peer.getAllPeerInfo().size());
            System.out.println("Peer Complete File" + peer.getNeighbors().getHasCompleteFileNeighbors().size());
            while (peer.getNeighbors().getHasCompleteFileNeighbors().size() != peer.getAllPeerInfo().size()) {
                try {
                    MessageManager.ActualMessage message = peer.getMessageManager().receiveActualMessage(inputStream);
                    switch (message.type()) {
                        case CHOKE:
//                            peer.getNeighbors().setChokedStatus(connectedPeerID, true);
                            peer.getLogger().logChoking(connectedPeerID);
                            break;
                        case UNCHOKE:
//                            peer.getNeighbors().setChokedStatus(connectedPeerID, false);
                            peer.getLogger().logUnchoking(connectedPeerID);

                            Integer randomPiece = peer.getBitmap().getRandomRemainingPiece(peer.getNeighbors().getPeerBitfield(connectedPeerID));

                            peer.getMessageManager().sendRequest(outputStream, randomPiece);
                            break;
                        case INTERESTED:
                            peer.getNeighbors().setInterestOfNeighbor(connectedPeerID, true);
                            peer.getLogger().logReceivedInterested(connectedPeerID);
                            break;
                        case NOT_INTERESTED:
                            peer.getNeighbors().setInterestOfNeighbor(connectedPeerID, false);
                            peer.getLogger().logReceivedNotInterested(connectedPeerID);
                            break;
                        case HAVE:
                            int pieceID = peer.getMessageManager().getHave(message);
                            peer.getNeighbors().updatePeerBitfield(connectedPeerID, pieceID);
                            peer.getLogger().logReceivedHave(connectedPeerID, pieceID);

                            // Check if this was the last piece for that peer
                            if (peer.getNeighbors().getPeerBitfield(connectedPeerID).hasAllPieces()) {
                                peer.getNeighbors().setHasCompleteFileNeighbors(connectedPeerID);
                            }
                            break;
                        case BITFIELD:
                            Bitmap bitmap = peer.getMessageManager().getBitmap(message);

                            // Save Bitmap
                            peer.getNeighbors().updatePeerBitfield(connectedPeerID, bitmap);

                            // Decide if interested
                            if (peer.getBitmap().containsInterestedPieces(bitmap)) {
                                peer.getMessageManager().sendInterested(outputStream);
                            } else {
                                peer.getMessageManager().sendNotInterested(outputStream);
                            }
                            break;
                        case REQUEST:
                            Integer requestedPiece = peer.getMessageManager().getReceive(message);

                            byte[] requestedData = peer.getFileManager().readPiece(requestedPiece);

                            peer.getMessageManager().sendPiece(outputStream, requestedPiece, requestedData);
                            break;
                        case PIECE:

                            MessageManager.Pair<Integer, byte[]> content = peer.getMessageManager().getPiece(message);

                            Integer receivedPiece = content.first;
                            byte[] receivedData = content.second;

                            peer.getFileManager().writePiece(receivedPiece, receivedData);
                            peer.getBitmap().markPieceAsReceived(receivedPiece);
                            peer.getNeighbors().incrementNumOfPiecesByPeer(connectedPeerID);
                            peer.getLogger().logDownloadedPiece(connectedPeerID, receivedPiece, peer.getBitmap().getBitset().cardinality());
                            peer.getNeighbors().sendHaveMessages(receivedPiece);

                            if (peer.getBitmap().hasAllPieces()) {
                                peer.getNeighbors().setHasCompleteFileNeighbors(peer.getPeerInfo().getPeerID());
                                peer.getLogger().logDownloadedFile(connectedPeerID);
                            }

                            // If still interested, request another piece. If not, send not interested
                            if (peer.getBitmap().containsInterestedPieces(peer.getNeighbors().getPeerBitfield(connectedPeerID))) {
                                Integer randomNextPiece = peer.getBitmap().getRandomRemainingPiece(peer.getNeighbors().getPeerBitfield(connectedPeerID));

                                peer.getMessageManager().sendRequest(outputStream, randomNextPiece);
                            } else {
                                peer.getMessageManager().sendNotInterested(outputStream);
                            }

                            break;
                    }
                } catch (Exception e) {
                    System.out.println("Something happened iterating over the switch case");
                    e.printStackTrace();
                }
            }

            /*
            * Just some general notes here:
            *
            * After handshake, the first thing to do is to send an interested/not-interested message
            *
            * Determine neighbors on the unchoked interval
            * Preferred and optimistic are considered unchoked, only send pieces to unchoked
            *
            * Whenever receiving a bitfield or have message, update that bitfield. If there is a piece desired,
            * send interested or not interested if not.
            *
            * Must maintain bitfields for all neighbor's pieces
            * Must maintain bitfield for all neighbors who are interested in myself
            *
            * Stop when all peers have complete file (including myself)
            *
            * When sending an unchoke message, the respected response is a request message.
            * Any neighbors that stay unchoked do not need to be sent a new message
            *
            * To stop sending messages to previously unchoked neighbors, you send a choke message
            *
            * If peer has the complete file, preferred neighbors are decided randomly amongst interested neighbors
            *
            * The optimistically unchoked neighbor is decided randomly amongst all choked neighbors that are interested
            *
            * Whenever receiving a piece, needs to check whether I am still interested in other neighbors and send them updated messages
            *
            * When receiving an unchoked message, send a request message, expecting a piece message. Continue this loop until one of two situations:
            *  - when I become choked
            *  - when I am no longer interested in any pieces
            *
            * Note it is possible to send a request message and not receive a piece message as neighbors may get re-evaluated, interrupting the expectations
            * */

        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println(e);
            }
        }

        System.exit(0);
    }
}
