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

    public String getTransactionOutputId() {return transactionOutputId;}
    public TransactionOutput getUTXO() {return UTXO;}
    public void setUTXO(TransactionOutput UTXO) {this.UTXO = UTXO;}
}
