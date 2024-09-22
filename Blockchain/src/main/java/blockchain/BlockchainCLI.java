package blockchain;

import com.google.gson.Gson;
import networking.Message;
import networking.MessageType;
import networking.NetworkManager;
import networking.PeerInfo;
import java.util.Map;
import java.util.Scanner;
import static blockchain.Main.unconfirmedTransactions;

public class BlockchainCLI {
    private final Blockchain blockchain;
    private final Wallet senderWallet;
    private final NetworkManager networkManager;

    public BlockchainCLI(Blockchain blockchain, Wallet senderWallet, NetworkManager networkManager) {
        this.blockchain = blockchain;
        this.senderWallet = senderWallet;
        this.networkManager = networkManager;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("1. View Blockchain");
            System.out.println("2. Send Transaction");
            System.out.println("3. Check Balance");
            System.out.println("4. Exit");
            System.out.print("Choose an option: ");
            int choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    blockchain.printChain();
                    break;
                case 2:
                    sendTransaction();
                    break;
                case 3:
                    checkBalance();
                    break;
                case 4:
                    System.exit(0);
                default:
                    System.out.println("Invalid choice, please try again.");
            }
        }
    }
    private void sendTransaction() {
        Map<String, PeerInfo> peers = networkManager.getPeers();
        if (peers.isEmpty()) {
            System.out.println("No connected nodes with wallets available.");
            return;
        }

        System.out.println("Choose a recipient:");
        int i = 1;
        for (Map.Entry<String, PeerInfo> entry : peers.entrySet()) {
            String peerPublicKey = entry.getKey();
            PeerInfo peerInfo = entry.getValue();
            System.out.println(i++ + ". " + peerInfo.getIpAddress() + " - " + peerPublicKey);
        }

        Scanner scanner = new Scanner(System.in);
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume the newline

        if (choice < 1 || choice > peers.size()) {
            System.out.println("Invalid choice.");
            return;
        }

        String selectedPublicKey = (String) peers.keySet().toArray()[choice - 1];
        System.out.print("Enter amount to send: ");
        float amount = scanner.nextFloat();
        try {
            // Step 1: Create a transaction from the sender wallet
            Transaction transaction = senderWallet.sendFunds(selectedPublicKey, amount);
            if (transaction != null) {
                System.out.println("Transaction created and broadcasted.");
                blockchain.addTransaction(transaction); // Add the transaction to this node's pool
                networkManager.broadcastMessage(new Message(MessageType.NEW_TRANSACTION, new Gson().toJson(transaction)));
                if (unconfirmedTransactions.size() >= Main.numTransactionsToMine) {
                    System.out.println("Mining 2 pending transactions...");
                    Block minedBlock = blockchain.minePendingTransactions(Main.numTransactionsToMine);
                    if (minedBlock != null) {
                        System.out.println("Mining completed.");
                        networkManager.broadcastMessage(new Message(MessageType.NEW_BLOCK, new Gson().toJson(minedBlock)));
                    }
                }
                else {
                    System.out.println(unconfirmedTransactions.size() + " transactions in the pool.");
                    System.out.println("Not enough transactions to mine yet.");
                }
            } else {
                System.out.println("Transaction failed.");
            }
        } catch (Exception e) {
            System.err.println("An error occurred while sending the transaction: " + e.getMessage());
        }
    }

    private void checkBalance() {
        float balance = senderWallet.getBalance();
        System.out.println("Balance: " + balance);
    }
}