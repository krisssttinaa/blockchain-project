package socket_try;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        int port = 6066; // Port to listen on
        try {
            Thread t = new BlockchainServer(port);
            t.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        BlockchainClient client = new BlockchainClient("127.0.0.1", port);
        client.connect();
    }
}