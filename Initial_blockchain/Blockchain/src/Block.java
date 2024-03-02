import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
public class Block {
    private int index;
    private String previousHash;
    private long timestamp;
    private List<Transaction> transactions;
    private int nonce;
    private String hash;
    public Block(int index, String previousHash, Transaction rewardMiner) {
        //after it was instantiated and parameters are passed, it goes one by one and adds the needed elements
        //like timestamp etc., and in the end calls on hash the function for finding the correct one
        //it will change the nonce till finds the proper one
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = System.currentTimeMillis();
        this.transactions = new ArrayList<>();
        addTransaction(rewardMiner);
        this.nonce = 0;
        this.hash = mineBlock();
    }
    //It is supposed to be here, because private etc. and it's just too much to put it in Miner's class
    //just not logical, at least it seems like that to me at the moment
    public String mineBlock() {
        //Skip mining for the genesis block
        if (index == 0) {
            hash = hash("The Times 03/Jan/2009 Chancellor on brink of second bailout for banks");
        } else {
            String target = "00000"; // set the difficulty to 00000
            StringBuilder transactionData = new StringBuilder();

            for (Transaction transaction : transactions) {
                transactionData.append(transaction.toString());
            }
            String data = index + timestamp + previousHash + transactionData.toString() + nonce;

            //System.out.println("******************************************");
            //System.out.println("Mining Block - Index: " + index);
            //System.out.println("Transactions: " + transactionData.toString());
            //System.out.println("Nonce: " + nonce);
            //System.out.println("Data used for mining: " + data);

            /*
            The do-while loop within this method continues to increment the nonce until the resulting hash meets the
            specified target conditions. This loop is where the mining process takes place, and it ensures that the
            block's hash satisfies the PoW requirements.
            ?do we need to check again if it starts with 00000 :/?
             */
            do {
                nonce++;
                //System.out.println(nonce);
                hash = hash(data + nonce); //compute hash
            } while (!hash.startsWith(target)); //check of satisfies the condition
        }
        return hash;
    }
    public static String hash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    public void addTransaction(Transaction transaction) { transactions.add(transaction);}
    //Getters
    public int getIndex() { return index;}
    public String getPreviousHash() { return previousHash;}
    public long getTimestamp() { return timestamp;}
    public List<Transaction> getTransactions() { return transactions;}
    public String getHash() { return hash;}
    //public int getNonce(){ return nonce;}
}