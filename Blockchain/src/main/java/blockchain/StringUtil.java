package blockchain;
import java.security.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import org.bitcoinj.core.Base58;
import java.security.Signature;

public class StringUtil {
    // Applies ECDSA Signature and returns the result (as bytes).
    public static byte[] applyECDSASig(PrivateKey privateKey, String input) {
        Signature dsa;
        byte[] output;
        try {
            dsa = Signature.getInstance("ECDSA", "BC");
            dsa.initSign(privateKey);
            byte[] strByte = input.getBytes();
            dsa.update(strByte);
            output = dsa.sign();
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

    // Returns difficulty string target, to compare to hash. e.g. difficulty of 5 will return "00000"
    public static String getDifficultyString(int difficulty) {
        return new String(new char[difficulty]).replace('\0', '0');
    }

    // Applies SHA256 to a string and returns a hash.
    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Applies sha256 to our input,
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder(); // This will contain hash as hexadecimal
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
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

    // Utilize this method to convert a byte array to an SHA-256 hash byte array
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

    public static String toBase58Check(byte[] publicKeyHash) {
        try {
            // Step 1: Add version byte (0x00 for Bitcoin Main Network)
            byte[] versionedPayload = new byte[1 + publicKeyHash.length];
            versionedPayload[0] = 0; // Version byte for main network
            System.arraycopy(publicKeyHash, 0, versionedPayload, 1, publicKeyHash.length);

            // Step 2: Double SHA-256 to get checksum
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] firstSHA = digest.digest(versionedPayload);
            byte[] secondSHA = digest.digest(firstSHA);

            // Step 3: Take the first 4 bytes of the second SHA-256 hash as checksum
            byte[] checksum = Arrays.copyOfRange(secondSHA, 0, 4);

            // Step 4: Append checksum to versioned payload
            byte[] binaryAddress = new byte[versionedPayload.length + checksum.length];
            System.arraycopy(versionedPayload, 0, binaryAddress, 0, versionedPayload.length);
            System.arraycopy(checksum, 0, binaryAddress, versionedPayload.length, checksum.length);

            // Step 5: Base58 encode the binary address
            return Base58.encode(binaryAddress);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
}