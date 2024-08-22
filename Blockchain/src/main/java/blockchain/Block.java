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
        this(index, previousHash, new ArrayList<>()); // Call the overloaded constructor with an empty list
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

    public String calculateHash() {
        if (index == 0) {
            hash = StringUtil.applySha256("The Times 03/Jan/2009 Chancellor on brink of second bailout for banks");
            return hash;
        }
        String transactionsData = transactions.stream()
                .map(Transaction::toString) // Assuming Transaction class has a meaningful toString override
                .reduce("", String::concat);
        return StringUtil.applySha256(
                previousHash + timestamp + index + transactionsData + nonce
        );
    }

    // Method to add a transaction to this block
    public boolean addTransaction(Transaction transaction) {
        if(transaction == null) return false;

        // If we're not adding the genesis block, process the transaction
        if((!"0".equals(previousHash))) {
            if(!transaction.processTransaction()) {
                System.out.println("Transaction failed to process. Discarded.");
                return false;
            }
        }

        transactions.add(transaction);
        System.out.println("Transaction Successfully added to Block");
        return true;
    }

    // New method to recalculate the block's hash
    public void updateHash() {
        this.hash = calculateHash(); // Recalculate hash with the current nonce value
    }

    // Increment the nonce value
    public void incrementNonce() {
        this.nonce++;
    }

    // Getters
    public int getIndex() {return index;}
    public String getPreviousHash() {return previousHash;}
    public long getTimestamp() {return timestamp;}
    public String getHash() {return hash;}
    public List<Transaction> getTransactions() {return transactions;}
    public int getNonce() {return nonce;}
}