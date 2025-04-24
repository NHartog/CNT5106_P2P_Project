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

    public boolean hasAllPieces() {
        return (bitfield.cardinality() == numPieces);
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
        // Checking against the bitmap that has more pieces, go through all the set pieces in the given bitmap to see if
        // any of those pieces are missing from the current bitfield.
        for (int i = bitmap.getBitset().nextSetBit(0); i >= 0 && i < bitmap.getBitset().length(); i = bitmap.getBitset().nextSetBit(i + 1)) {
            if (!bitfield.get(i)) {
                indices.add(i);
            }
        }

        return indices;
    }

    public synchronized Integer getRandomRemainingPiece(Bitmap bitmap) {
        List<Integer> indices = getRemainingPieces(bitmap);

        if (indices.isEmpty()) {
            return -1;
        }
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

    public synchronized void printBitfield() {
        System.out.println("Current Bitfield: " + bitfield);
        StringBuilder printout = new StringBuilder();
        for(int i = 0; i < bitfield.length(); i++) {
            printout.append(bitfield.get(i)).append(", ");
        }
        System.out.println(printout);
    }
}
