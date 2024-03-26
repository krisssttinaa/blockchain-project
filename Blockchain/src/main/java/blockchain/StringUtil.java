package blockchain;
import java.security.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.security.Signature;

public class StringUtil {
    // Applies ECDSA Signature and returns the result (as bytes).
    public static byte[] applyECDSASig(PrivateKey privateKey, String input) {
        Signature dsa;
        byte[] output = new byte[0];
        try {
            dsa = Signature.getInstance("ECDSA", "BC");
            dsa.initSign(privateKey);
            byte[] strByte = input.getBytes();
            dsa.update(strByte);
            byte[] realSig = dsa.sign();
            output = realSig;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return output;
    }

    // Verifies a String signature
    public static boolean verifyECDSASig(PublicKey publicKey, String data, byte[] signature) {
        try {
            Signature ecdsaVerify = Signature.getInstance("ECDSA", "BC");
            ecdsaVerify.initVerify(publicKey);
            ecdsaVerify.update(data.getBytes());
            return ecdsaVerify.verify(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Returns difficulty string target, to compare to hash. eg difficulty of 5 will return "00000"
    public static String getDifficultyString(int difficulty) {
        return new String(new char[difficulty]).replace('\0', '0');
    }

    // Applies SHA256 to a string and returns a hash.
    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Applies sha256 to our input,
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer(); // This will contain hash as hexidecimal
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Converts the signature bytes into a hexadecimal string
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Use this method when displaying the signature
    public static String applyECDSASigAndGetString(PrivateKey privateKey, String input) {
        byte[] sig = applyECDSASig(privateKey, input);
        return bytesToHex(sig);
    }

    // Utilize this method to convert a byte array to a SHA-256 hash byte array
    public static byte[] applySha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Gets the encoded string from any key.
    public static String getStringFromKey(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }


}
/*
    // Derives a human-readable address from a public key using Base58Check encoding
    public static String getAddressFromKey(PublicKey publicKey) {
        byte[] hash = applySha256(publicKey.getEncoded());

        // Version byte; 0x00 for Bitcoin's main network
        byte[] versionedPayload = new byte[hash.length + 1];
        versionedPayload[0] = 0;
        System.arraycopy(hash, 0, versionedPayload, 1, hash.length);

        // Double SHA-256 checksum
        byte[] checksum = applySha256(applySha256(versionedPayload));

        // Take the first 4 bytes of the second SHA-256 hash, this is the checksum
        byte[] addressBytes = new byte[versionedPayload.length + 4];
        System.arraycopy(versionedPayload, 0, addressBytes, 0, versionedPayload.length);
        System.arraycopy(checksum, 0, addressBytes, versionedPayload.length, 4);

        // Base58Check encode the version + payload + checksum
        return Base58.encode(addressBytes);
    }

    public static String toBase58Check(byte[] publicKeyHash) {
    // Step 1: Add version byte (0x00 for Bitcoin)
    byte[] versionedPayload = new byte[1 + publicKeyHash.length];
    versionedPayload[0] = 0; // Bitcoin mainnet address
    System.arraycopy(publicKeyHash, 0, versionedPayload, 1, publicKeyHash.length);

    // Step 2: Calculate the checksum (first 4 bytes of the double SHA-256)
    byte[] checksum = sha256(sha256(versionedPayload)).readBytes(4);

    // Step 3: Append the checksum to the versioned payload
    byte[] binaryAddress = new byte[versionedPayload.length + checksum.length];
    System.arraycopy(versionedPayload, 0, binaryAddress, 0, versionedPayload.length);
    System.arraycopy(checksum, 0, binaryAddress, versionedPayload.length, checksum.length);

    // Step 4: Convert the binary address to a Base58 string
    String base58CheckAddress = Base58.encode(binaryAddress);

    return base58CheckAddress;
}

// Helper method to perform SHA-256 hashing
private static byte[] sha256(byte[] data) {
    try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(data);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e); // Handle exception appropriately
    }
}

* */