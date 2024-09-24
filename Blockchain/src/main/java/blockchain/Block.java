package blockchain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Block {
    private final int index;
    private final String previousHash;
    private final long timestamp;
    private final List<Transaction> transactions;
    private String hash;
    private int nonce;

    public Block(int index, String previousHash) {
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = 0;
        this.transactions = new ArrayList<>();
        this.nonce = 0;
        this.hash = calculateHash();
    }

    // Modified constructor to accept a list of transactions
    public Block(int index, String previousHash, List<Transaction> transactions) {
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = new Date().getTime();
        this.transactions = transactions != null ? new ArrayList<>(transactions) : new ArrayList<>();
        this.nonce = 0;
        this.hash = calculateHash();
    }

    // Calculate the hash for the block using SHA-256 (without Merkle Root for simplicity)
    public String calculateHash() {
        String transactionsData = transactions.stream()
                .map(Transaction::toString) // Assuming Transaction class has a meaningful toString override
                .reduce("", String::concat);
        return StringUtil.applySha256(
                previousHash + timestamp + index + transactionsData + nonce
        );
    }

    // Mining the block: increment the nonce until the hash satisfies the difficulty
    public void mineBlock(int difficulty) {
        String target = new String(new char[difficulty]).replace('\0', '0');  // Create a target string with the correct number of leading zeros
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
        System.out.println("Block mined! Hash: " + hash);
    }

    // Getters
    public int getIndex() {
        return index;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

}