package ledger;

import blockchain.StringUtil;

public class TransactionOutput {
    public String id;
    public String recipient; // recipient address as a string (previously PublicKey)
    public float value; // amount of coins
    public String parentTransactionId; // id of the transaction this output was created in
    public int confirmations = 0;  // Number of blocks since this UTXO was created

    public TransactionOutput(String recipient, float value, String parentTransactionId) {
        this.recipient = recipient;
        this.value = value;
        this.parentTransactionId = parentTransactionId;
        this.id = StringUtil.applySha256(recipient + value + parentTransactionId);
    }

    // Check if the coin belongs to the provided public key
    public boolean isMine(String publicKey) {
        return publicKey.equals(recipient);
    }

    @Override
    public String toString() {
        return "TransactionOutput{" +
                "id='" + id + '\'' +
                ", recipient='" + recipient + '\'' +
                ", value=" + value +
                ", parentTransactionId='" + parentTransactionId + '\'' +
                //", confirmations=" + confirmations +
                '}';
    }
}