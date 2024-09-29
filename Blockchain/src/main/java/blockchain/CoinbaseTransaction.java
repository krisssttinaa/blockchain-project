package blockchain;

import java.security.PrivateKey;
import java.util.ArrayList;

public class CoinbaseTransaction extends Transaction {

    public CoinbaseTransaction(String recipient, float reward) {
        super("COINBASE", recipient, reward, new ArrayList<>());
        this.transactionId = calculateHash();  // Assign unique transaction ID
    }

    // Since no inputs are involved, we override the method to skip UTXO validation
    @Override
    public boolean processTransaction() {
        outputs.add(new TransactionOutput(this.recipient, value, transactionId)); // Create the output for the reward
        Main.UTXOs.put(outputs.get(0).id, outputs.get(0)); // Add the reward UTXO to UTXOs
        return true;
    }

    @Override
    public void generateSignature(PrivateKey privateKey) {}
    @Override
    public boolean verifySignature() {return true;}
}