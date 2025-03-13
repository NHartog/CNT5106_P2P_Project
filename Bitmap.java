import java.util.BitSet;

public class Bitmap {
    private final BitSet bitfield;
    private final int numPieces;

    public Bitmap(int numPieces, boolean hasCompleteFile) {
        this.numPieces = numPieces;
        this.bitfield = new BitSet(numPieces);

        // If the peer starts with the full file, mark all pieces as available
        if (hasCompleteFile) {
            bitfield.set(0, numPieces);
        }
    }

    public Bitmap(byte[] bitfield) {
        this.bitfield = new BitSet(bitfield.length);
        this.numPieces = bitfield.length;

        for(int i = 0; i < bitfield.length; i++) {
            if(bitfield[i] == 1) {
                this.bitfield.set(i);
            }
        }
    }

    public boolean hasPiece(int index) {
        return bitfield.get(index);
    }

    public void markPieceAsReceived(int index) {
        bitfield.set(index);
    }

    public byte[] getBitfield() {
        byte[] bytes = new byte[numPieces];
        for (int i = 0; i < bytes.length; i++) {
            if(bitfield.get(i)){
                bytes[i] = 1;
            }
        }
        return bytes;
    }

    // debugging
    public void printBitfield() {
        System.out.println("Current Bitfield: " + bitfield);
    }
}
