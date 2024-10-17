package blockchain;

public class Constants {
    public static final int MINIMUM_CONFIRMATIONS = 3; // Minimum confirmations before UTXO is spendable
    public static final int MAX_HASH_COUNT = 300; // Keep only the last 300 block hashes for tracking
    public static final int MAX_RETRIES = 3; // Maximum retry attempts for networking

    // Mining Constants
    public static final float MINING_REWARD = 6.00f; // Mining reward per block mined
    public static final int NUM_TRANSACTIONS_TO_MINE = 2; // Number of transactions to mine per block
    public static final int MINING_DIFFICULTY = 6; // Mining difficulty (how many leading zeros in the hash)

    // Networking
    public static final int NODE_PORT = 7777; // Node's listening port
    public static final String SEED_NODE_ADDRESS = "172.18.0.2"; // Seed node IP address

    // Wallet
    public static final String WALLET_FILE = "wallet.dat";  // File to store wallet keys

    // Transaction Constants
    public static final float MINIMUM_TRANSACTION = 0.0f;  // Minimum allowed transaction value
}