package old_networking;

public class Main {
    public static void main(String[] args) {
        NetworkManager networkManager = new NetworkManager();
        networkManager.connect("192.168.31.157", 4567);

    }

}