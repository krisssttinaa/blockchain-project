package ledger;

import blockchain.Blockchain;
import blockchain.StringUtil;
import java.io.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;

public class Wallet {
    private PrivateKey privateKey;
    public PublicKey publicKey;
    private static final String WALLET_FILE = "wallet.dat";  // File to store wallet keys
    private static final int MINIMUM_CONFIRMATIONS = 5;

    public Wallet() {
        // If wallet file exists, load it, otherwise create a new wallet
        if (new File(WALLET_FILE).exists()) {
            loadWallet();
        } else {
            generateKeyPair();
            saveWallet();
        }
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
        for (TransactionOutput utxo : Blockchain.UTXOs.values()) {
            // Only include UTXOs that belong to the wallet and have at least 5 confirmations
            if (utxo.isMine(StringUtil.getStringFromKey(publicKey)) && utxo.confirmations >= MINIMUM_CONFIRMATIONS) {
                total += utxo.value;
            }
        }
        return total;
    }

    // Get the maturing balance (UTXOs that belong to this wallet but are not yet mature)
    public float getMaturingBalance() {
        float total = 0;
        for (TransactionOutput utxo : Blockchain.UTXOs.values()) {
            // Only include UTXOs that belong to this wallet and have NOT yet reached the minimum confirmations
            if (utxo.isMine(StringUtil.getStringFromKey(publicKey)) && utxo.confirmations < MINIMUM_CONFIRMATIONS) {
                total += utxo.value;
            }
        }
        return total;
    }

    public Transaction sendFunds(String recipient, float value) {
        // Step 1: Ensure the wallet has enough matured funds to send the transaction
        if (getBalance() < value) {
            System.out.println("#Not Enough funds to send transaction. Transaction Discarded.");
            return null;
        }

        ArrayList<TransactionInput> inputs = new ArrayList<>();
        float total = 0;

        // Step 2: Gather UTXOs that belong to the wallet and are fully matured (have enough confirmations)
        for (TransactionOutput utxo : Blockchain.UTXOs.values()) {
            // Check if the UTXO belongs to the wallet and has enough confirmations
            if (utxo.isMine(StringUtil.getStringFromKey(publicKey)) && utxo.confirmations >= MINIMUM_CONFIRMATIONS) {
                total += utxo.value;
                inputs.add(new TransactionInput(utxo.id));
                if (total > value) break;  // Stop if we have enough inputs to cover the value
            }
        }

        // Step 3: Create the transaction if we have enough inputs to cover the value
        if (total < value) {
            System.out.println("#Not enough confirmed UTXOs to cover the transaction. Transaction Discarded.");
            return null;
        }

        // Create a new transaction with the gathered inputs
        Transaction newTransaction = new Transaction(StringUtil.getStringFromKey(publicKey), recipient, value, inputs);
        newTransaction.generateSignature(privateKey);

        // Step 4: Remove the spent UTXOs from the UTXO pool
        for (TransactionInput input : inputs) {
            Blockchain.UTXOs.remove(input.transactionOutputId);
        }
        return newTransaction;
    }

    // Save the wallet (keys) to a file
    private void saveWallet() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(WALLET_FILE))) {
            oos.writeObject(privateKey);
            oos.writeObject(publicKey);
            System.out.println("Wallet saved to " + WALLET_FILE);
        } catch (IOException e) {
            System.err.println("Error saving wallet: " + e.getMessage());
        }
    }

    // Load the wallet (keys) from the file
    private void loadWallet() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(WALLET_FILE))) {
            privateKey = (PrivateKey) ois.readObject();
            publicKey = (PublicKey) ois.readObject();
            System.out.println("Wallet loaded from " + WALLET_FILE);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading wallet: " + e.getMessage());
            // If loading fails, create new keys
            generateKeyPair();
            saveWallet();
        }
    }
}