import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

public class PrimaryConnector implements Runnable {

    private final Socket socket;
    private final Peer peer;
    private final int expectedPeerID;
    private final boolean madeTCPConnection;

    public PrimaryConnector(Socket socket, Peer peer, int expectedPeerID, boolean madeTCPConnection) throws IOException {
        this.peer = peer;
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
            System.out.println(expectedPeerID + " Send Hanshake");
            peer.getMessageManager().sendHandshakeMessage(expectedPeerID);
            // Receive Shake Message
            System.out.println(expectedPeerID + " Receive Hanshake");
            int connectedPeerID = peer.getMessageManager().receivedValidHandshakeMessage(expectedPeerID, expectedPeerID);
            if (connectedPeerID == -1) return;

            System.out.println(expectedPeerID + " Log Connection");
            // Log Handshake Done
            if (madeTCPConnection) {
                peer.getLogger().logMakesConnectionTCP(connectedPeerID);
            } else {
                peer.getLogger().logConnectedFromTCP(connectedPeerID);
            }

            // Send Bitmap
            peer.getMessageManager().sendBitmap(connectedPeerID);
            peer.getNeighbors().addHandshakedNeighbor(connectedPeerID);

            while (!Thread.currentThread().isInterrupted() && peer.getNeighbors().getHasCompleteFileNeighbors().size() != peer.getAllPeerInfo().size()) {
                try {
                    MessageManager.ActualMessage message = peer.getMessageManager().receiveActualMessage(connectedPeerID);
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        continue;
                    }
                    if (message == null) {
                        continue;
                    }
                    switch (message.type()) {
                        case CHOKE:
                            peer.getLogger().logChoking(connectedPeerID);
                            break;
                        case UNCHOKE:
                            peer.getLogger().logUnchoking(connectedPeerID);

                            int randomPiece = peer.getBitmap().getRandomRemainingPiece(peer.getNeighbors().getPeerBitfield(connectedPeerID));

                            if (randomPiece == -1) {
                                continue;
                            }

                            peer.getMessageManager().sendRequest(connectedPeerID, randomPiece);
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
                            if (pieceID < 0 || pieceID > peer.getNumPieces()) {
                                continue; // Just skip this if the piece comes in bad
                            }
                            peer.getNeighbors().updatePeerBitfield(connectedPeerID, pieceID);
                            peer.getLogger().logReceivedHave(connectedPeerID, pieceID);

                            if (peer.getBitmap().containsInterestedPieces(peer.getNeighbors().getPeerBitfield(connectedPeerID))) {
                                peer.getMessageManager().sendInterested(connectedPeerID);
                            } else {
                                peer.getMessageManager().sendNotInterested(connectedPeerID);
                            }

                            // Check if this was the last piece for that peer
                            if (peer.getNeighbors().getPeerBitfield(connectedPeerID).hasAllPieces()) {
                                peer.getNeighbors().setHasCompleteFileNeighbors(connectedPeerID);
                            }
                            break;
                        case BITFIELD:
                            Bitmap bitmap = peer.getMessageManager().getBitmap(message);

                            // Save Bitmap
                            peer.getNeighbors().updatePeerBitfield(connectedPeerID, bitmap);
                            if (peer.getNeighbors().getPeerBitfield(connectedPeerID).hasAllPieces()) {
                                peer.getNeighbors().setHasCompleteFileNeighbors(connectedPeerID);
                            }

                            // Decide if interested
                            if (peer.getBitmap().containsInterestedPieces(bitmap)) {
                                peer.getMessageManager().sendInterested(connectedPeerID);
                            } else {
                                peer.getMessageManager().sendNotInterested(connectedPeerID);
                            }

                            break;
                        case REQUEST:
                            int requestedPiece = peer.getMessageManager().getReceive(message);
                            if (requestedPiece < 0 || requestedPiece > peer.getNumPieces()) {
                                continue; // Just skip this if the piece comes in bad
                            }
                            byte[] requestedData = peer.getFileManager().readPiece(requestedPiece);

                            peer.getMessageManager().sendPiece(connectedPeerID, requestedPiece, requestedData);
                            break;
                        case PIECE:

                            MessageManager.Pair<Integer, byte[]> content = peer.getMessageManager().getPiece(message);

                            int receivedPiece = content.first;
                            byte[] receivedData = content.second;
                            if (receivedPiece < 0 || receivedPiece > peer.getNumPieces()) {
                                continue; // Just skip this if the piece comes in bad
                            }

                            peer.getFileManager().writePiece(receivedPiece, receivedData);
                            peer.getBitmap().markPieceAsReceived(receivedPiece);
                            peer.getNeighbors().incrementNumOfPiecesByPeer(connectedPeerID);
                            peer.getLogger().logDownloadedPiece(connectedPeerID, receivedPiece, peer.getBitmap().getBitset().cardinality());
                            peer.getNeighbors().sendHaveMessages(receivedPiece);
                            peer.getNeighbors().sendNotInterestedMessages();

                            if (peer.getBitmap().hasAllPieces()) {
                                peer.getNeighbors().setHasCompleteFileNeighbors(peer.getPeerInfo().getPeerID());
                                peer.getLogger().logDownloadedFile();
                            }

                            // If still interested, request another piece. If not, send not interested
                            if (peer.getBitmap().containsInterestedPieces(peer.getNeighbors().getPeerBitfield(connectedPeerID))) {
                                int randomNextPiece = peer.getBitmap().getRandomRemainingPiece(peer.getNeighbors().getPeerBitfield(connectedPeerID));

                                if (randomNextPiece == -1) {
                                    continue;
                                }

                                peer.getMessageManager().sendRequest(connectedPeerID, randomNextPiece);
                            } else {
                                peer.getMessageManager().sendNotInterested(connectedPeerID);
                            }

                            break;
                    }
                } catch (Exception e) {
                    System.out.println(expectedPeerID + " LMAO " + Arrays.toString(e.getStackTrace()));
                    System.out.println(expectedPeerID + " LMAO " + e.getMessage());
                    break;
                }
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return;
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

        } catch (Exception e) {
            System.out.println(expectedPeerID + " " + "Something general happened" + e);
            System.out.println(expectedPeerID + " " + e.getMessage());
        }
        finally {
            try {
                System.out.println("Performing Close for " + expectedPeerID);
                socket.close();
                peer.getServerSocket().close();
                peer.getMessageManager().closeAll();
            } catch (IOException e) {
                System.out.println(peer.getPeerInfo().getPeerID() + " " + e);
            }
        }
        peer.getExecutor().shutdownNow();
    }
}
