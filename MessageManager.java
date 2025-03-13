import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MessageManager {

    public enum MessageType {
        CHOKE(0),
        UNCHOKE(1),
        INTERESTED(2),
        NOT_INTERESTED(3),
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

    public class ActualMessage {
        public final int length;
        public final MessageType type;
        public final byte[] payload;

        public ActualMessage(int length, MessageType type, byte[] payload) {
            this.length = length;
            this.type = type;
            this.payload = payload;
        }
    }


    private final Peer peer;

    MessageManager(Peer peer) {
        this.peer = peer;
    }

    public void sendMessage(DataOutputStream out, byte[] content) {
        try {
            out.write(content);
            out.flush();
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public ActualMessage receiveActualMessage(DataInputStream in) {
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

    public void sendActualMessage(DataOutputStream out, MessageType type, byte[] payload) {
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

    public void sendBitmap(DataOutputStream out) {
        sendActualMessage(out, MessageType.BITFIELD, peer.getBitmap().getBitfield());
    }


    public Bitmap receiveBitmap(DataInputStream in) {
        ActualMessage message = receiveActualMessage(in);
        if (message.type != MessageType.BITFIELD) {
            throw new RuntimeException(String.format("Did not receive a bitmap but instead got a type of int %b", message.type));
        }
        return new Bitmap(message.payload);
    }

    public void sendHandshakeMessage(DataOutputStream out) {
        sendMessage(out, getHandshakeMessage());
    }

    public int receivedValidHandshakeMessage(DataInputStream in, int... validPeerIDs){
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
