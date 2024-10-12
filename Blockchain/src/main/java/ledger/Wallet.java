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

    public float calculateBalance(boolean onlyConfirmed) {
        float total = 0;
        for (TransactionOutput utxo : Blockchain.UTXOs.values()) {
            if (utxo.isMine(StringUtil.getStringFromKey(publicKey))) {
                if (onlyConfirmed && utxo.confirmations >= Blockchain.MINIMUM_CONFIRMATIONS) {
                    total += utxo.value;
                } else if (!onlyConfirmed && utxo.confirmations < Blockchain.MINIMUM_CONFIRMATIONS) {
                    total += utxo.value;
                }
            }
        }
        return total;
    }

    public Transaction sendFunds(String recipient, float value) {
        if (value == 0) {
            System.out.println("Creating a zero-value transaction.");
            Transaction zeroTransaction = new Transaction(StringUtil.getStringFromKey(publicKey), recipient, 0, new ArrayList<>());
            zeroTransaction.generateSignature(privateKey);
            return zeroTransaction;
        }

        if (getBalance() < value) {
            System.out.println("Not enough funds.");
            return null;
        }

        ArrayList<TransactionInput> inputs = new ArrayList<>();
        float total = 0;
        for (TransactionOutput utxo : Blockchain.UTXOs.values()) {
            if (utxo.isMine(StringUtil.getStringFromKey(publicKey)) && utxo.confirmations >= Blockchain.MINIMUM_CONFIRMATIONS) {
                total += utxo.value;
                inputs.add(new TransactionInput(utxo.id));
                if (total >= value) break;  // Stop gathering inputs once we have enough
            }
        }
        if (total < value) {
            System.out.println("Not enough confirmed UTXOs.");
            return null;
        }
        Transaction newTransaction = new Transaction(StringUtil.getStringFromKey(publicKey), recipient, value, inputs);
        newTransaction.generateSignature(privateKey);
        float change = total - value;
        if (change > 0) {
            System.out.println("Creating change output for sender: " + change);
            newTransaction.outputs.add(new TransactionOutput(StringUtil.getStringFromKey(publicKey), change, newTransaction.transactionId));
        }
        return newTransaction;
    }

    private void saveWallet() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(WALLET_FILE))) {
            oos.writeObject(privateKey);
            oos.writeObject(publicKey);
            System.out.println("Wallet saved to " + WALLET_FILE);
        } catch (IOException e) {
            System.err.println("Error saving wallet: " + e.getMessage());
        }
    }

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

    public float getBalance() {return calculateBalance(true);}
    public float getMaturingBalance() {return calculateBalance(false);}
}