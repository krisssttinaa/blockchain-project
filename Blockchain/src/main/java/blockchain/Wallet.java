package blockchain;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;

public class Wallet {
    public PrivateKey privateKey;
    public PublicKey publicKey;
    public Wallet() {generateKeyPair();}

    private void generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA","BC");//object for generating public-private key pair
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");//pseudo random number
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1"); //secp256k1 curve specification for ECDSA key pair generation. This is the elliptic curve domain parameters used by Bitcoin for its public-private key cryptography.

            // Initialize the key generator and generate a KeyPair
            keyGen.initialize(ecSpec, random); //256 bytes provides an acceptable security level
            KeyPair keyPair = keyGen.generateKeyPair();

            privateKey = keyPair.getPrivate();
            publicKey = keyPair.getPublic();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Returns balance and stores the UTXO's owned by this wallet in this.UTXOs
    public float getBalance() {//This method calculates and returns the balance of the wallet by iterating over all known UTXOs.
        float total = 0;
        for (String utxoId : Main.UTXOs.keySet()) {
            TransactionOutput utxo = Main.UTXOs.get(utxoId);
            if (utxo.isMine(publicKey)) { // if output belongs to me (if coins belong to me)
                total += utxo.value;
            }
        }
        return total;
    }

    // Generates and returns a new transaction from this wallet.
    public Transaction sendFunds(PublicKey _recipient,float value ) {
        if(getBalance() < value) { //gather balance and check funds.
            System.out.println("#Not Enough funds to send transaction. Transaction Discarded.");
            return null;
        }
        //create array list of inputs
        ArrayList<TransactionInput> inputs = new ArrayList<>();

        float total = 0;
        for (String utxoId : Main.UTXOs.keySet()) {
            TransactionOutput utxo = Main.UTXOs.get(utxoId);//retrieve each UTXO from the main list using the UTXO ID.
            if(utxo.isMine(this.publicKey)) {//check if the UTXO belongs to the wallet (i.e., if it can be spent by this wallet).
                total += utxo.value;
                inputs.add(new TransactionInput(utxoId));//add it to the list of inputs for the new transaction
                if(total > value) break;
            }
        }

        Transaction newTransaction = new Transaction(publicKey, _recipient, value, inputs); //new transaction object using the collected inputs and the transaction details.
        newTransaction.generateSignature(privateKey);// cryptographic signature for the transaction to prove ownership of the UTXOs being spent

        for(TransactionInput input: inputs){
            //Remove each input's corresponding UTXO from the main UTXO list, as it is now spent
            Main.UTXOs.remove(input.transactionOutputId);
        }
        return newTransaction;
    }
}