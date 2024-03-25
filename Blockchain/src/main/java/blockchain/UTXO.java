package blockchain;
import java.security.PublicKey;
import com.google.gson.annotations.Expose;//marks certain fields to be exposed for JSON serialization and deserialization.

public class UTXO {
    @Expose private String id; // Use the @Expose annotation to include the field in JSON serialization/deserialization
    private PublicKey owner; // Public keys are a special case and require custom serialization/deserialization
    @Expose private double amount;
    // Constructor
    public UTXO(String id, PublicKey owner, double amount) {
        this.id = id;
        this.owner = owner;
        this.amount = amount;
    }

    // This method checks whether this UTXO belongs to a public key (i.e., an owner or address)
    public boolean isOwner(PublicKey publicKey) {return publicKey.equals(this.owner);}

    // Override equals to check for UTXO equality based on id
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UTXO utxo = (UTXO) obj; //casts the obj to a UTXO object so that we can compare the id fields
        return id.equals(utxo.id); //returns true if the id fields of both UTXO instances are equal
    }

    // Override hashCode to ensure consistent hashing, important if UTXOs are stored in a HashMap
    @Override
    public int hashCode() {return id.hashCode();}

    // Getters and Setters
    public String getId() {return id;}
    public PublicKey getOwner() {return owner;}
    public void setOwner(PublicKey owner) {this.owner = owner;}
    public double getAmount() {return amount;}
    public void setAmount(double amount) {this.amount = amount;}

    // method to serialize UTXO for storage or network transmission
    // For example, you might convert the UTXO to a JSON string if you want to store it or send it over a network
    // public String toJSON() {
    //     // Implement JSON serialization
    // }

    // method to create a UTXO from a JSON string (deserialization)
    // public static UTXO fromJSON(String json) {
    //     // Implement JSON deserialization
    // }
}