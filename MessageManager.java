import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MessageManager {

    public class Pair<K, V> {
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

        public int getValue() {
            return value;
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

    MessageManager(Peer peer) {
        this.peer = peer;
    }

    public synchronized void sendMessage(DataOutputStream out, byte[] content) {
        try {
            out.write(content);
            out.flush();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public synchronized ActualMessage receiveActualMessage(DataInputStream in) {
        try {
            int length = in.readInt() - 1; // - 1 compensates for the inclusion of type in the message length
            int type = in.readByte();
            byte[] payload = new byte[length];
            int bytesRead = in.read(payload);
            if (bytesRead != length) {
                throw new Exception(String.format("Failed to retrieve expected length: Got %d instead of %d", bytesRead, length));
            }

            return new ActualMessage(length, MessageType.fromValue(type), payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void sendActualMessage(DataOutputStream out, MessageType type) {
        sendActualMessage(out, type, new byte[0]);
    }

    public synchronized void sendActualMessage(DataOutputStream out, MessageType type, byte[] payload) {
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

        sendMessage(out, actualMessageBytes);
    }

    public synchronized void sendHave(DataOutputStream out, Integer index) {
        sendActualMessage(out, MessageType.HAVE, new byte[]{index.byteValue()});
    }

    public synchronized void sendChoke(DataOutputStream out) {
        sendActualMessage(out, MessageType.CHOKE, new byte[]{});
    }

    public synchronized void sendUnchoke(DataOutputStream out) {
        sendActualMessage(out, MessageType.UNCHOKE, new byte[]{});
    }

    public synchronized int receiveHave(DataInputStream in) {
        ActualMessage message = receiveActualMessage(in);
        if (message.type != MessageType.HAVE) {
            throw new RuntimeException(String.format("Did not receive a HAVE but instead got a type of int %b", message.type));
        }
        return getHave(message);
    }

    public synchronized int getHave(ActualMessage message) {
        return ByteBuffer.wrap(message.payload(), 0, 4).getInt();
    }

    public synchronized void sendInterested(DataOutputStream out) {
        sendActualMessage(out, MessageType.INTERESTED);
    }

    public synchronized void sendNotInterested(DataOutputStream out) {
        sendActualMessage(out, MessageType.NOT_INTERESTED);
    }

    public synchronized void sendRequest(DataOutputStream out, Integer index) {
        sendActualMessage(out, MessageType.REQUEST, new byte[]{index.byteValue()});
    }

    public synchronized int receiveRequest(DataInputStream in) {
        ActualMessage message = receiveActualMessage(in);
        if (message.type != MessageType.REQUEST) {
            throw new RuntimeException(String.format("Did not receive a REQUEST but instead got a type of int %b", message.type));
        }
        return getReceive(message);
    }

    public synchronized int getReceive(ActualMessage message) {
        return ByteBuffer.wrap(message.payload(), 0, 4).getInt();
    }

    public synchronized void sendPiece(DataOutputStream out, Integer index, byte[] piece) {
        byte[] bytes = ByteBuffer
                .allocate(1 + piece.length)
                .put(new byte[]{index.byteValue()})
                .put(piece)
                .array();

        sendActualMessage(out, MessageType.PIECE, bytes);
    }

    public synchronized Pair<Integer, byte[]> receivePiece(DataInputStream in) {
        ActualMessage message = receiveActualMessage(in);
        if (message.type != MessageType.PIECE) {
            throw new RuntimeException(String.format("Did not receive a PIECE but instead got a type of int %b", message.type));
        }
        return getPiece(message);
    }

    public synchronized Pair<Integer, byte[]> getPiece(ActualMessage message) {
        return new Pair<>(ByteBuffer.wrap(message.payload(), 0, 4).getInt(), Arrays.copyOfRange(message.payload(), 4, message.payload().length)) ;
    }

    public synchronized void sendBitmap(DataOutputStream out) {
        sendActualMessage(out, MessageType.BITFIELD, peer.getBitmap().getBitfield());
    }


    public synchronized Bitmap receiveBitmap(DataInputStream in) {
        ActualMessage message = receiveActualMessage(in);
        if (message.type != MessageType.BITFIELD) {
            throw new RuntimeException(String.format("Did not receive a bitmap but instead got a type of int %b", message.type));
        }
        return getBitmap(message);
    }

    public synchronized Bitmap getBitmap(ActualMessage message) {
        return new Bitmap(message.payload(), peer.getNumPieces());
    }

    public synchronized void sendHandshakeMessage(DataOutputStream out) {
        sendMessage(out, getHandshakeMessage());
    }

    public synchronized int receivedValidHandshakeMessage(DataInputStream in, int... validPeerIDs){
        byte[] buffer = new byte[32];
        try {
            int bytesRead = in.read(buffer);
            boolean correctLength = bytesRead == 32;
            boolean correctHeader = Peer.PeerInfo.HEADER.equals(getHeaderFromHandshakeMessage(buffer));

            // A valid Peer is considered a peer that is expected for a connection
            int peerFromHandshake = getPeerIDFromHandshake(buffer);
            boolean validPeer = Arrays.stream(validPeerIDs).anyMatch(id -> id == peerFromHandshake);

            return correctLength && correctHeader && validPeer ? peerFromHandshake : -1;
        } catch (IOException e) {
            System.out.println(e);
            return -1;
        }
    }

    public synchronized byte[] getHandshakeMessage() {
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

    public synchronized String getHeaderFromHandshakeMessage(byte[] incomingMessage) {
        return new String(incomingMessage, 0, 18, StandardCharsets.UTF_8);
    }

    public synchronized int getPeerIDFromHandshake(byte[] incomingMessage) {
        return ByteBuffer.wrap(incomingMessage, 28, 4).getInt();
    }
}
