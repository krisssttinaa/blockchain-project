package blockchain;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class PublicKeyDeserializer implements JsonDeserializer<PublicKey> {

    @Override
    public PublicKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            // Decode the Base64 encoded public key string
            String publicKeyString = json.getAsString();
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);

            // Convert the bytes into a PublicKey object
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("ECDSA");
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize PublicKey", e);
        }
    }
}