package ledger;

import blockchain.Blockchain;
import blockchain.Constants;
import blockchain.StringUtil;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class Transaction {
    public String transactionId; // unique identifier for the transaction, hash of the transaction's contents
    public String sender; // sender address as a string (previously PublicKey)
    public String recipient; // recipient address as a string (previously PublicKey)
    public float value; // amount of coins to send
    public byte[] signature; // prevents others from spending funds in the sender's wallet
    public List<TransactionInput> inputs = new ArrayList<>(); // previous transaction outputs being used as inputs
    public List<TransactionOutput> outputs = new ArrayList<>(); // outputs created by this transaction
    private static int sequence = 0; // to ensure transaction uniqueness
    public static final float minimumTransaction = Constants.MINIMUM_TRANSACTION; // Minimum transaction value

    public Transaction(String from, String to, float value, List<TransactionInput> inputs) {
        this.sender = from;
        this.recipient = to;
        this.value = value;
        this.inputs = inputs;
        transactionId= calculateHash();
    }

    // Calculates the transaction hash (used as transactionId)
    public String calculateHash() {
        sequence++; // ensure uniqueness
        return StringUtil.applySha256(sender + recipient + value + sequence+ System.currentTimeMillis());
    }

    // Generates a signature using the sender's private key
    public void generateSignature(PrivateKey privateKey) {
        String data = sender + recipient + value;
        signature = StringUtil.applyECDSASig(privateKey, data); // generate signature
    }

    // Process the transaction, updating UTXOs and checking for validity, including double-spending prevention
    public boolean processTransaction() {
        if ("COINBASE".equals(sender)) {
            // Process the coinbase transaction
            outputs.add(new TransactionOutput(recipient, value, transactionId));
            Blockchain.UTXOs.put(outputs.get(0).id, outputs.get(0));
            return true;
        }
        if (value == 0) {
            System.out.println("Processing zero-value transaction.");
            //transactionId = calculateHash();
            return true;
        }
        // Step 1: Verify the transaction signature
        if (!verifySignature()) {
            System.out.println("#Transaction Signature failed to verify");
            return false;
        }
        // Step 2: Gather and validate transaction inputs
        float inputSum = 0;
        for (TransactionInput input : inputs) {
            input.UTXO = Blockchain.UTXOs.get(input.transactionOutputId);
            if (input.UTXO == null) {
                System.out.println("#Referenced input is invalid or missing: " + input.transactionOutputId);
                return false;
            }
            if (!input.UTXO.isMine(sender)) {
                System.out.println("#Referenced input does not belong to the sender: " + input.transactionOutputId);
                return false;
            }
            // Ensure UTXO is mature enough to be spent
            if (input.UTXO.confirmations < Blockchain.MINIMUM_CONFIRMATIONS) {
                System.out.println("#UTXO is not mature enough to be spent. Required confirmations: " + Blockchain.MINIMUM_CONFIRMATIONS);
                return false;
            }
            inputSum += input.UTXO.value;
        }
        // Step 3: Check for double-spending in the unconfirmed transaction pool
        for (Transaction pendingTx : Blockchain.unconfirmedTransactions) {
            for (TransactionInput pendingInput : pendingTx.inputs) {
                if (inputs.stream().anyMatch(i -> i.transactionOutputId.equals(pendingInput.transactionOutputId))) {
                    System.out.println("#Input already spent in another unconfirmed transaction.");
                    return false;
                }
            }
        }
        // Step 4: Check if inputs are sufficient to cover the transaction value
        if (inputSum < value) {
            System.out.println("#Not enough input value to cover the transaction. Required: " + value + ", Available: " + inputSum);
            return false;
        }
        // Ensure the transaction meets the minimum transaction value
        if (inputSum < minimumTransaction) {
            System.out.println("#Transaction Inputs too small: " + inputSum);
            return false;
        }
        // Step 5: Generate outputs for recipient and sender (change)
        transactionId = calculateHash();
        outputs.add(new TransactionOutput(recipient, value, transactionId));  // Add recipient's output
        // Return change to sender if input sum is greater than the value being sent
        if (inputSum > value) {
            outputs.add(new TransactionOutput(sender, inputSum - value, transactionId));  // Change output
        }
        return true;
    }

    // Verifies the transaction signature to ensure it was signed by the owner of the sender's private key
    public boolean verifySignature() {
        // Skip signature verification for Coinbase transactions
        if ("COINBASE".equals(this.sender)) {
            System.out.println("Skipping signature verification for Coinbase transaction: " + this.transactionId);
            return true;
        }
        String data = sender + recipient + value;
        try {
            PublicKey senderPublicKey = StringUtil.getKeyFromString(sender);
            boolean verified = StringUtil.verifyECDSASig(senderPublicKey, data, signature);
            if (verified) {
                System.out.println("Signature successfully verified!");
            } else {
                System.out.println("Signature verification failed.");
            }
            return verified;
        } catch (Exception e) {
            System.out.println("Error during signature verification: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean isStillValid() {
        // Check that the transaction's inputs have not been spent in the current UTXO set
        for (TransactionInput input : this.inputs) {
            TransactionOutput utxo = Blockchain.UTXOs.get(input.transactionOutputId);
            if (utxo == null || !utxo.isMine(this.sender)) {
                return false;  // Transaction is not valid if the input has already been spent
            }
        }
        return true;  // The transaction is still valid
    }

    public List<TransactionInput> getInputs() {return inputs;}
    public List<TransactionOutput> getOutputs() {return outputs;}
    public String getTransactionId() {return transactionId;}
    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", value=" + value +
                ", inputs=" + inputs +
                ", outputs=" + outputs +
                '}';
    }
}