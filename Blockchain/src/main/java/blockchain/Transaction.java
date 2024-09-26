package blockchain;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

public class Transaction {
    public String transactionId; // unique identifier for the transaction, hash of the transaction's contents
    public String sender; // sender address as a string (previously PublicKey)
    public String recipient; // recipient address as a string (previously PublicKey)
    public float value; // amount of coins to send
    public byte[] signature; // prevents others from spending funds in the sender's wallet
    public List<TransactionInput> inputs; // previous transaction outputs being used as inputs
    public List<TransactionOutput> outputs = new ArrayList<>(); // outputs created by this transaction
    private static int sequence = 0; // to ensure transaction uniqueness

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
        return StringUtil.applySha256(
                sender + recipient + Float.toString(value) + sequence+ System.currentTimeMillis()
        );
    }

    // Generates a signature using the sender's private key
    public void generateSignature(PrivateKey privateKey) {
        String data = sender + recipient + Float.toString(value);
        signature = StringUtil.applyECDSASig(privateKey, data); // generate signature
    }

    // Verifies the transaction signature to ensure it was signed by the owner of the sender's private key
    public boolean verifySignature() {
        String data = sender + recipient + Float.toString(value);
        return StringUtil.verifyECDSASig(StringUtil.getKeyFromString(sender), data, signature); // verifies using sender's public key
    }

    // Process the transaction, updating UTXOs and checking for validity, include double-spending prevention
    public boolean processTransaction() {
        if (value == 0) {
            System.out.println("Processing zero-value transaction.");
            transactionId = calculateHash();
            outputs.add(new TransactionOutput(recipient, value, transactionId));
            return true;
        }

        // Step 1: Verify the signature
        if (!verifySignature()) {
            System.out.println("#Transaction Signature failed to verify");
            return false;
        }

        // Step 2: Gather and validate transaction inputs
        float inputSum = 0;
        for (TransactionInput input : inputs) {
            input.UTXO = Main.UTXOs.get(input.transactionOutputId);
            if (input.UTXO == null || !input.UTXO.isMine(sender)) {
                System.out.println("#Referenced input is invalid or does not belong to the sender");
                return false;
            }
            inputSum += input.UTXO.value;
        }

        // Step 3: Check for double-spending in the unconfirmed pool
        for (Transaction pendingTx : Main.unconfirmedTransactions) {
            for (TransactionInput pendingInput : pendingTx.inputs) {
                if (inputs.stream().anyMatch(i -> i.transactionOutputId.equals(pendingInput.transactionOutputId))) {
                    System.out.println("#Input already spent in another unconfirmed transaction.");
                    return false;
                }
            }
        }

        // Step 4: Check if inputs are sufficient to cover the value
        if (inputSum < value) {
            System.out.println("#Not enough input value to cover the transaction.");
            return false;
        }

        // Check if the transaction meets the minimum transaction value
        if (inputSum < Main.minimumTransaction) {
            System.out.println("#Transaction Inputs too small: " + inputSum);
            return false;
        }

        // Step 5: Generate outputs for recipient and sender
        transactionId = calculateHash();
        outputs.add(new TransactionOutput(recipient, value, transactionId));  // Recipient's output
        outputs.add(new TransactionOutput(sender, inputSum - value, transactionId));  // Change back to sender

        // Step 6: Update UTXOs
        for (TransactionOutput output : outputs) {
            Main.UTXOs.put(output.id, output);  // Add outputs to UTXO pool
        }
        for (TransactionInput input : inputs) {
            Main.UTXOs.remove(input.transactionOutputId);  // Remove used UTXOs
        }
        return true;
    }

    // Returns the total value of inputs (UTXOs) in the transaction
    public float getInputsValue() {
        float total = 0;
        for (TransactionInput i : inputs) {
            if (i.UTXO == null) continue;
            total += i.UTXO.value;
        }
        return total;
    }

    // Returns the total value of outputs in the transaction
    public float getOutputsValue() {
        float total = 0;
        for (TransactionOutput o : outputs) {
            total += o.value;
        }
        return total;
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