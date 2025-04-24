import java.io.*;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageManager {

    public static class Pair<K, V> {
        public final K first;
        public final V second;

        public Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }
    }

    public enum MessageType {
        CHOKE(0), // No payload
        UNCHOKE(1), // No payload
        INTERESTED(2), // No payload
        NOT_INTERESTED(3), // No payload
        HAVE(4),
        BITFIELD(5),
        REQUEST(6),
        PIECE(7);

        private final int value;

        MessageType(int value) {
            this.value = value;
        }

        public static MessageType fromValue(int value) {
            for (MessageType type : MessageType.values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unexpected value: " + value);
        }
    }

    public record ActualMessage(int length, MessageType type, byte[] payload) {}


    private final Peer peer;
    private final Map<Integer, DataOutputStream> peerOutputStreams = new ConcurrentHashMap<>();
    private final Map<Integer, BufferedInputStream> peerInputStreams = new ConcurrentHashMap<>();

    MessageManager(Peer peer) {
        this.peer = peer;
    }

    public synchronized void addOutputStream(Integer peerID, DataOutputStream out) {
        peerOutputStreams.put(peerID, out);
    }

    public synchronized void addInputStream(Integer peerID, BufferedInputStream in) {
        peerInputStreams.put(peerID, in);
    }

    public synchronized void sendMessage(Integer peerID, byte[] content) {
        DataOutputStream out = peerOutputStreams.get(peerID);
        synchronized (out) {
            try {
                out.write(content);
                out.flush();
            } catch (IOException ignored) {
            }
        }

    }

    public synchronized void closeAll() throws IOException {
        System.out.println("Closing all sockets on this peer");
        for(Map.Entry<Integer, DataOutputStream> connection: peerOutputStreams.entrySet()){
            connection.getValue().close();
        }
        for(Map.Entry<Integer, BufferedInputStream> connection: peerInputStreams.entrySet()){
            connection.getValue().close();
        }
    }

    public ActualMessage receiveActualMessage(Integer peerID) throws Exception {
        BufferedInputStream in = peerInputStreams.get(peerID);
        DataInputStream inputStream = new DataInputStream(in);
        ActualMessage message = null;
        while (!Thread.currentThread().isInterrupted() && message == null) {
            try {
                in.mark(5); // mark with enough room

                int initialLength = inputStream.readInt();
                if (inputStream.available() >= initialLength && initialLength > 0) {
                    in.reset();

                    int length = inputStream.readInt() - 1; // - 1 compensates for the inclusion of type in the message length
                    int type = inputStream.readByte();
                    byte[] payload = new byte[length];
                    int bytesRead = in.read(payload);
                    if (bytesRead != length) {
                        throw new Exception(String.format("Failed to retrieve expected length: Got %d instead of %d for %d", bytesRead, length, peerID));
                    }

                    message = new ActualMessage(length, MessageType.fromValue(type), payload);
                } else {
                    in.reset();
                }
            } catch (SocketException | SocketTimeoutException ignored) {
            } catch (EOFException e) {
                return null;
            } catch (Exception e) {
                throw e;
            }
        }


        return message;
    }

    public void sendActualMessage(Integer peerID, MessageType type) {
        sendActualMessage(peerID, type, new byte[0]);
    }

    public void sendActualMessage(Integer peerID, MessageType type, byte[] payload) {
        int length = payload.length + 1;

        byte[] lengthBytes = ByteBuffer
                .allocate(4)
                .putInt(length)
                .array();

        byte typeByte = (byte) type.value;

        byte[] actualMessageBytes = ByteBuffer
                .allocate(4 + 1 + payload.length)
                .put(lengthBytes)
                .put(typeByte)
                .put(payload)
                .array();

        sendMessage(peerID, actualMessageBytes);
    }

    public void sendHave(Integer peerID, Integer index) {
        ByteBuffer buffer = ByteBuffer.allocate(4);  // 4 bytes for an int
        buffer.putInt(index);
        sendActualMessage(peerID, MessageType.HAVE, buffer.array());
    }

    public void sendChoke(Integer peerID) {
        sendActualMessage(peerID, MessageType.CHOKE, new byte[]{});
    }

    public void sendUnchoke(Integer peerID) {
        sendActualMessage(peerID, MessageType.UNCHOKE, new byte[]{});
    }

    public int getHave(ActualMessage message) {
        return ByteBuffer.wrap(message.payload(), 0, 4).getInt();
    }

    public void sendInterested(Integer peerID) {
        if (!peer.getNeighbors().getInterestingNeighbors().contains(peerID)) {
            sendActualMessage(peerID, MessageType.INTERESTED);
            peer.getNeighbors().setInterestingNeighbor(peerID, true);
        }
    }

    public void sendNotInterested(Integer peerID) {
        if (!peer.getNeighbors().getNotInterestingNeighbors().contains(peerID)) {
            sendActualMessage(peerID, MessageType.NOT_INTERESTED);
            peer.getNeighbors().setInterestingNeighbor(peerID, false);
        }
    }

    public void sendRequest(Integer peerID, Integer index) {
        ByteBuffer buffer = ByteBuffer.allocate(4);  // 4 bytes for an int
        buffer.putInt(index);
        sendActualMessage(peerID, MessageType.REQUEST, buffer.array());
    }

    public int getReceive(ActualMessage message) {
        return ByteBuffer.wrap(message.payload(), 0, 4).getInt();
    }

    public void sendPiece(Integer peerID, Integer index, byte[] piece) {
        ByteBuffer buffer = ByteBuffer.allocate(4);  // 4 bytes for an int
        buffer.putInt(index);

        byte[] bytes = ByteBuffer
                .allocate(4 + piece.length)
                .put(buffer.array())
                .put(piece)
                .array();

        sendActualMessage(peerID, MessageType.PIECE, bytes);
    }

    public Pair<Integer, byte[]> getPiece(ActualMessage message) {
        return new Pair<>(ByteBuffer.wrap(message.payload(), 0, 4).getInt(), Arrays.copyOfRange(message.payload(), 4, message.payload().length));
    }

    public void sendBitmap(Integer peerID) {
        sendActualMessage(peerID, MessageType.BITFIELD, peer.getBitmap().getBitfield());
    }

    public Bitmap getBitmap(ActualMessage message) {
        return new Bitmap(message.payload(), peer.getNumPieces());
    }

    public void sendHandshakeMessage(Integer peerID) {
        sendMessage(peerID, getHandshakeMessage());
    }

    public int receivedValidHandshakeMessage(Integer peerID, int... validPeerIDs){
        BufferedInputStream bufferedInputStream = peerInputStreams.get(peerID);
        DataInputStream in = new DataInputStream(bufferedInputStream);
        while (!Thread.currentThread().isInterrupted()) {
            byte[] buffer = new byte[32];
            try {
                if (in.available() >= 32) {
                    int bytesRead = in.read(buffer);
                    boolean correctLength = bytesRead == 32;
                    String header = getHeaderFromHandshakeMessage(buffer);
                    boolean correctHeader = Peer.PeerInfo.HEADER.equals(header);

                    // A valid Peer is considered a peer that is expected for a connection
                    int peerFromHandshake = getPeerIDFromHandshake(buffer);
                    boolean validPeer = Arrays.stream(validPeerIDs).anyMatch(id -> id == peerFromHandshake);

                    System.out.println(peerID + " Valid connection values: " + header + " " + peerFromHandshake);
                    return correctLength && correctHeader && validPeer ? peerFromHandshake : -1;
                }
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                return -1;
            }
        }
        return -1;
    }

    public byte[] getHandshakeMessage() {
        byte[] strBytes = Peer.PeerInfo.HEADER.getBytes(StandardCharsets.UTF_8);

        byte[] zeroBytes = ByteBuffer.allocate(10).array();

        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(peer.getPeerInfo().getPeerID());
        byte[] intBytes = buffer.array();

        byte[] result = new byte[32];

        System.arraycopy(strBytes, 0, result, 0, 18);
        System.arraycopy(zeroBytes, 0, result, 18, 10);
        System.arraycopy(intBytes, 0, result, 28, 4);

        return result;
    }

    public String getHeaderFromHandshakeMessage(byte[] incomingMessage) {
        return new String(incomingMessage, 0, 18, StandardCharsets.UTF_8);
    }

    public int getPeerIDFromHandshake(byte[] incomingMessage) {
        return ByteBuffer.wrap(incomingMessage, 28, 4).getInt();
    }
}
