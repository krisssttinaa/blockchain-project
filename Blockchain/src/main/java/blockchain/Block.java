package blockchain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Block {
    private int index;
    private String previousHash;
    private long timestamp;
    private List<Transaction> transactions; // Change from String data to a list of Transactions
    private String hash;
    private int nonce;

    public Block(int index, String previousHash) {
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = new Date().getTime();
        this.transactions = new ArrayList<>();
        this.nonce = 0;
        this.hash = calculateHash();
    }

    public String calculateHash() {
        String transactionsData = transactions.stream()
                .map(Transaction::toString) // Assuming Transaction class has a meaningful toString override
                .reduce("", String::concat);
        return StringUtil.applySha256(
                previousHash + Long.toString(timestamp) + Integer.toString(index) + transactionsData + Integer.toString(nonce)
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

    // Method to add a transaction to this block
    public boolean addTransaction(Transaction transaction) {
        // Here you can add a verification for the transaction if needed
        transactions.add(transaction);
        return true; // Return true if the transaction is successfully added
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
    public String getHash() {
        return hash;
    }
    public List<Transaction> getTransactions() {return transactions;}
}