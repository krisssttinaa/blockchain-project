package blockchain;
import java.security.*;
import java.util.ArrayList;
import java.util.List;

import static blockchain.Main.unconfirmedTransactions;

public class Transaction {
    public String transactionId; //unique identifier for the transaction, hash of the transaction's contents
    public PublicKey sender; //public key/address
    public PublicKey recipient;
    public float value; //the amount of coins to send
    public byte[] signature; //prevent anybody else from spending funds in our wallet
    public List<TransactionInput> inputs; //Inputs refer to previous transaction outputs that are being spent, and outputs create new unspent transaction outputs
    public List<TransactionOutput> outputs = new ArrayList<>();
    private static int sequence = 0; //count of how many transactions have been generated, to ensure uniqueness of transactions by incrementing its value for each transaction

    public Transaction(PublicKey from, PublicKey to, float value, List<TransactionInput> inputs) {
        this.sender = from;
        this.recipient = to;
        this.value = value;
        this.inputs = inputs;
    }

    // This Calculates the transaction hash (which will be used as its Id)
    private String calculateHash() {
        sequence++; //increase the sequence to avoid 2 identical transactions having the same hash
        return StringUtil.applySha256(
                StringUtil.getStringFromKey(sender) +
                        StringUtil.getStringFromKey(recipient) + Float.toString(value) + sequence);
    }

    //Signs all the data we dont wish to be tampered with, generates a digital signature for the transaction using the sender's private key
    public void generateSignature(PrivateKey privateKey) {
        //the sender, recipient, and value, which will be signed by the sender's private key
        String data = StringUtil.getStringFromKey(sender) + StringUtil.getStringFromKey(recipient) + Float.toString(value) ;
        signature = StringUtil.applyECDSASig(privateKey,data); //generates signature
    }

    //Verifies that the transaction's signature is valid and matches the data signed by the sender's private key
    public boolean verifySignature() {
        String data = StringUtil.getStringFromKey(sender) + StringUtil.getStringFromKey(recipient) + Float.toString(value) ;
        return StringUtil.verifyECDSASig(sender, data, signature); //Verifies the signature using the sender's public key.
    }

    // Returns true if new transaction could be created
    // Returns true if new transaction could be created
    public boolean processTransaction() {
        if(!verifySignature()) {
            System.out.println("#Transaction Signature failed to verify");
            return false;
        }

        // Gather transaction inputs
        float inputSum = 0;
        for (TransactionInput i : inputs) {
            i.UTXO = Main.UTXOs.get(i.transactionOutputId);
            if (i.UTXO == null) {
                System.out.println("#Referenced input on Transaction(" + transactionId + ") is Missing or Invalid");
                return false;
            }
            if (!i.UTXO.isMine(this.sender)) {
                System.out.println("#Transaction Input does not belong to the sender");
                return false;
            }
            inputSum += i.UTXO.value;
        }

        // Validate total output value equals input value
        float outputSum = getOutputsValue();
        if (inputSum != outputSum) {
            System.out.println("#Input values and output values do not match");
            return false;
        }

        // Gather transaction inputs and check for double spending
        for (TransactionInput i : inputs) {
            i.UTXO = Main.UTXOs.get(i.transactionOutputId);
            if (i.UTXO == null) {
                System.out.println("#Referenced input on Transaction(" + transactionId + ") is Missing or Invalid");
                return false;
            }
        }

        // Check unconfirmed transactions for double spending
        for (TransactionInput i : inputs) {
            for (Transaction pendingTx : unconfirmedTransactions) {
                for (TransactionInput pendingInput : pendingTx.inputs) {
                    if (pendingInput.transactionOutputId.equals(i.transactionOutputId)) {
                        System.out.println("#Input Transaction(" + i.transactionOutputId + ") is already spent in an unconfirmed transaction");
                        return false;
                    }
                }
            }
        }

        //Checks if transaction is valid:
        if(getInputsValue() < Main.minimumTransaction) {
            System.out.println("#Transaction Inputs too small: " + getInputsValue());
            return false;
        }

        //Generate transaction outputs:
        float leftOver = getInputsValue() - value; //difference btw the total input value and the transaction value
        transactionId = calculateHash();
        outputs.add(new TransactionOutput( this.recipient, value,transactionId)); //send value to recipient
        outputs.add(new TransactionOutput( this.sender, leftOver,transactionId)); //send the left over 'change' back to sender

        //Add outputs to Unspent list
        for(TransactionOutput o : outputs) {
            Main.UTXOs.put(o.id , o);
        }

        //Remove transaction inputs from UTXO lists as spent:
        for(TransactionInput i : inputs) {
            if(i.UTXO == null) continue; //if Transaction can't be found skip it
            Main.UTXOs.remove(i.UTXO.id);
        }
        return true;
    }

    //returns sum of inputs(UTXOs) values
    public float getInputsValue() {
        float total = 0;
        for(TransactionInput i : inputs) {
            if(i.UTXO == null) continue; //if Transaction can't be found skip it, this should not happen
            total += i.UTXO.value;
        }
        return total;
    }

    //returns sum of outputs
    public float getOutputsValue() {
        float total = 0;
        for(TransactionOutput o : outputs) {
            total += o.value;
        }
        return total;
    }

    public List<TransactionInput> getInputs() {
        return inputs;
    }
    public List<TransactionOutput> getOutputs() {
        return outputs;
    }
}