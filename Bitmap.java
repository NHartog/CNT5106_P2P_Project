import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

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

    public Bitmap(byte[] data, int numPieces) {
        this.numPieces = numPieces;
        bitfield = BitSet.valueOf(data);
    }

    public synchronized boolean hasPiece(int index) {
        return bitfield.get(index);
    }

    public synchronized boolean hasAllPieces() {
        return (bitfield.length() == numPieces) && (bitfield.nextClearBit(0) >= numPieces);
    }

    public synchronized boolean containsInterestedPieces(Bitmap bitmap) {
        BitSet bitset = (BitSet) bitmap.getBitset().clone();

        // Remove bits from current bitfield, checking if the incoming bitmap contains set bits that are not present in
        // my bitmap
        bitset.andNot(bitfield);

        return !bitset.isEmpty();
    }

    public synchronized List<Integer> getRemainingPieces(Bitmap bitmap) {
        List<Integer> indices = new ArrayList<>();
        for (int i = bitfield.nextClearBit(0); i >= 0; i = bitfield.nextClearBit(i + 1)) {
            // The past bitmap must contain the missing piece
            if (bitmap.getBitset().get(i)){
                indices.add(i);
            }
        }

        return indices;
    }

    public synchronized Integer getRandomRemainingPiece(Bitmap bitmap) {
        List<Integer> indices = getRemainingPieces(bitmap);

        Random random = new Random();
        int randomIndex = random.nextInt(indices.size());

        return indices.get(randomIndex);
    }

    public synchronized void markPieceAsReceived(int index) {
        bitfield.set(index);
    }

    public synchronized byte[] getBitfield() {
        return bitfield.toByteArray();
    }

    public synchronized BitSet getBitset() {
        return bitfield;
    }


    // debugging
    public synchronized void printBitfield() {
        System.out.println("Current Bitfield: " + bitfield);
        for(int i = 0; i < bitfield.length(); i++) {
            System.out.print(bitfield.get(i) + ", ");
        }
        System.out.println();
    }
}
