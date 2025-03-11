import java.util.BitSet;

//TODO want to talk about this code, specifically the bitset stuff
public class Bitmap {
    private BitSet bitfield;
    private int numPieces;

    public Bitmap(int numPieces, boolean hasCompleteFile) {
        this.numPieces = numPieces;
        this.bitfield = new BitSet(numPieces);

        // If the peer starts with the full file, mark all pieces as available
        if (hasCompleteFile) {
            bitfield.set(0, numPieces);
        }
    }

    public boolean hasPiece(int index) {
        return bitfield.get(index);
    }

    public void markPieceAsReceived(int index) {
        bitfield.set(index);
    }
}
