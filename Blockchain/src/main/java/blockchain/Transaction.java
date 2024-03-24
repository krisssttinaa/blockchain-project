package blockchain;

public class Transaction {
    private String sender;
    private Miner recipientMiner;
    private double amount;

    public Transaction(String sender, Miner recipientMiner, double amount) {
        this.sender = sender;
        this.recipientMiner = recipientMiner;
        this.amount = amount;
    }

    // Override toString method to provide a meaningful representation
    @Override
    public String toString() {
        return "Transaction: Sender=" + sender + ", Recipient=" + recipientMiner.getMinerName() + ", Amount=" + amount;
    }

    public void processTransaction() {
        // Update Miner's reward
        if ("System".equals(sender) && recipientMiner != null) {
            double newReward = recipientMiner.getReward() + amount;
            recipientMiner.setReward(newReward);
        }
        // Add additional logic for handling other types of transactions
    }

    // Getters
    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipientMiner.getMinerName();
    }

    public double getAmount() {
        return amount;
    }
}
