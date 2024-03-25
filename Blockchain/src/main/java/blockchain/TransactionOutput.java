package blockchain;

import java.security.PublicKey;
public class TransactionOutput {
    public String id;
    public PublicKey recipient; // Also known as the new owner of these coins.
    public float value; // The amount of coins they own.
    public String parentTransactionId; // The id of the transaction this output was created in.

    // Constructor
    public TransactionOutput(PublicKey recipient, float value, String parentTransactionId) {
        this.recipient = recipient;
        this.value = value;
        this.parentTransactionId = parentTransactionId;
        this.id = StringUtil.applySha256(StringUtil.getStringFromKey(recipient) + Float.toString(value) + parentTransactionId);
    }

    // Check if coin belongs to you
    public boolean isMine(PublicKey publicKey) {
        return (publicKey.equals(recipient));
    }
}