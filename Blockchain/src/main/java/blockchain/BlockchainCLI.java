package blockchain;

import java.util.Map;
import java.util.Scanner;

import networking.NetworkManager;
import networking.PeerInfo;
import ledger.Transaction;
import ledger.Wallet;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockchainCLI {
    private final Blockchain blockchain;
    private final Wallet senderWallet;
    private final NetworkManager networkManager;
    private final ForkResolution forkResolution;
    private final ExecutorService cliExecutor = Executors.newSingleThreadExecutor();

    public BlockchainCLI(Blockchain blockchain, Wallet senderWallet, NetworkManager networkManager, ForkResolution forkResolution) {
        this.blockchain = blockchain;
        this.senderWallet = senderWallet;
        this.networkManager = networkManager;
        this.forkResolution = forkResolution;
    }

    public void start() {
        cliExecutor.submit(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("1. View Blockchain");
                System.out.println("2. Send Transaction");
                System.out.println("3. Check Balance");
                System.out.println("4. Exit");
                System.out.print("Choose an option: ");
                int choice = scanner.nextInt();

                switch (choice) {
                    case 1 -> blockchain.printChain();
                    case 2 -> sendTransaction();
                    case 3 -> checkBalance();
                    case 4 -> checkMaturingBalance();  // New method to check maturing balance
                    case 5 -> System.exit(0);
                    default -> System.out.println("Invalid choice, please try again.");
                }
            }
        });
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
        float amount;
        while (true) {
            System.out.print("Enter amount to send: ");
            if (scanner.hasNextFloat()) {
                amount = scanner.nextFloat();
                scanner.nextLine(); // Consume the newline character
                break; // Valid float, even if it's 0 or negative
            } else {
                System.out.println("Invalid input. Please enter a valid number for the amount.");
                scanner.next(); // Consume the invalid input and retry
            }
        }
        try {
            Transaction transaction = senderWallet.sendFunds(selectedPublicKey, amount);
            if (transaction != null) {
                // Use the new method in Blockchain to handle adding, broadcasting, and mining
                blockchain.handleNewTransaction(transaction, null, networkManager, forkResolution);
                System.out.println("DONE");
            } else {
                System.out.println("Transaction creation failed.");
            }
        } catch (Exception e) {
            System.err.println("An error occurred while sending the transaction: " + e.getMessage());
        }
    }

    private void checkBalance() {
        float balance = senderWallet.getBalance();
        System.out.println("Balance: " + balance);
    }

    private void checkMaturingBalance() {
        float maturingBalance = senderWallet.getMaturingBalance();
        System.out.println("Maturing Balance: " + maturingBalance);
    }
}