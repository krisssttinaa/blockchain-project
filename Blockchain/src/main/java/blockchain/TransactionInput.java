package blockchain;

public class TransactionInput {
    public final String transactionOutputId; // Reference to TransactionOutputs -> transactionId
    public TransactionOutput UTXO; // Contains the Unspent transaction output

    public TransactionInput(String transactionOutputId) {
        if (transactionOutputId == null || transactionOutputId.isEmpty()) {
            throw new IllegalArgumentException("TransactionOutputId cannot be null or empty");
        }
        this.transactionOutputId = transactionOutputId;
    }

    @Override
    public String toString() {
        return "TransactionInput{" +
                "transactionOutputId='" + transactionOutputId + '\'' +
                ", UTXO=" + (UTXO != null ? UTXO.toString() : "null") +
                '}';
    }
}