package blockchain;

import java.security.PublicKey;
import java.util.Scanner;

public class BlockchainCLI {
    private Blockchain blockchain;

    public BlockchainCLI(Blockchain blockchain) {
        this.blockchain = blockchain;
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
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter recipient public key: ");
        String recipientKey = scanner.nextLine();
        System.out.print("Enter amount to send: ");
        float amount = scanner.nextFloat();

        Wallet senderWallet = new Wallet(); // This should be the user's wallet
        PublicKey recipient = StringUtil.getKeyFromString(recipientKey); // Convert string to PublicKey

        Transaction transaction = senderWallet.sendFunds(recipient, amount);
        if (transaction != null) {
            blockchain.addTransaction(transaction);
            blockchain.createAndAddBlock();
            System.out.println("Transaction sent and block added.");
        } else {
            System.out.println("Transaction failed.");
        }
    }

    private void checkBalance() {
        Wallet wallet = new Wallet(); // This should be the user's wallet
        System.out.println("Balance: " + wallet.getBalance());
    }
}