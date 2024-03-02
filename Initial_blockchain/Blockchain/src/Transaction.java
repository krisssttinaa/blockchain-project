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
        return sender + recipientMiner.getMinerName() + amount;
    }

    public void processTransaction() {
        // Update Miner's amount
        if ("System".equals(sender) && recipientMiner != null) {
            double newReward = recipientMiner.getReward()+6.25;
            recipientMiner.setReward(newReward);
            // You can directly modify the reward variable since it's public
        }
        // Add additional logic for handling other types of transactions
    }

    public String getSender() { return sender;}
    public String getRecipient() { return recipientMiner.getMinerName();}
    public double getAmount() { return amount;}
}