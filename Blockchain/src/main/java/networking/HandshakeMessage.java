package networking;

public class HandshakeMessage {
    private String publicKey;
    private String nonce;
    private String newNonce;

    public HandshakeMessage(String publicKey, String nonce) {
        this.publicKey = publicKey;
        this.nonce = nonce;
    }

    public HandshakeMessage(String publicKey, String nonce, String newNonce) {
        this.publicKey = publicKey;
        this.nonce = nonce;
        this.newNonce = newNonce;
    }

    public HandshakeMessage(String nonce) {
        this.nonce = nonce;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getNonce() {
        return nonce;
    }

    public String getNewNonce() {
        return newNonce;
    }
}
