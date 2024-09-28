package blockchain;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;

public class Wallet {
    public PrivateKey privateKey;
    public PublicKey publicKey;
    private Blockchain blockchain; // Reference to Blockchain instance

    public Wallet(Blockchain blockchain) {
        this.blockchain = blockchain;
        generateKeyPair();
    }

    private void generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");

            keyGen.initialize(ecSpec, random);
            KeyPair keyPair = keyGen.generateKeyPair();

            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public float getBalance() {
        float total = 0;
        for (TransactionOutput utxo : blockchain.getUTXOs().values()) {
            if (utxo.isMine(StringUtil.getStringFromKey(publicKey))) {
                total += utxo.value;
            }
        }
        return total;
    }

    public Transaction sendFunds(String recipient, float value) {
        if (getBalance() < value) {
            System.out.println("#Not Enough funds to send transaction. Transaction Discarded.");
            return null;
        }

        ArrayList<TransactionInput> inputs = new ArrayList<>();
        float total = 0;
        for (TransactionOutput utxo : blockchain.getUTXOs().values()) {
            if (utxo.isMine(StringUtil.getStringFromKey(publicKey))) {
                total += utxo.value;
                inputs.add(new TransactionInput(utxo.id));
                if (total > value) break;
            }
        }

        Transaction newTransaction = new Transaction(StringUtil.getStringFromKey(publicKey), recipient, value, inputs, blockchain);
        newTransaction.generateSignature(privateKey);

        // Remove the inputs used for this transaction from the UTXO pool
        for (TransactionInput input : inputs) {
            blockchain.getUTXOs().remove(input.transactionOutputId);
        }

        return newTransaction;
    }
}