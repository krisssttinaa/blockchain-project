package blockchain;

public class TransactionOutput_old {
    private String recipient;
    private double amount;

    public TransactionOutput_old(String recipient, double amount) {
        this.recipient = recipient;
        this.amount = amount;
    }

    public String getRecipient() {
        return recipient;
    }

    public double getAmount() {
        return amount;
    }
}
