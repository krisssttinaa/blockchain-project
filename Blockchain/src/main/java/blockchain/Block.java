package blockchain;import java.util.Date;

public class Block {
    private int index;
    private String previousHash;
    private long timestamp;
    private String data;
    private String hash;
    private int nonce; // Declare nonce variable

    public Block(int index, String previousHash, String data) {
        this.index = index;
        this.previousHash = previousHash;
        this.data = data;
        this.timestamp = new Date().getTime();
        this.hash = calculateHash();
        this.nonce = 0; // Initialize nonce to 0
    }

    public String calculateHash() {
        return StringUtil.applySha256(
                previousHash +
                        Long.toString(timestamp) +
                        Integer.toString(index) +
                        data +
                        Integer.toString(nonce) // Include nonce in hash calculation
        );
    }

    public void mineBlock(int difficulty) {
        String target = StringUtil.getDifficultyString(difficulty);
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
        System.out.println("Block Mined: " + hash);
    }


    // Getters
    public int getIndex() {
        return index;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getData() {
        return data;
    }

    public String getHash() {
        return hash;
    }
}