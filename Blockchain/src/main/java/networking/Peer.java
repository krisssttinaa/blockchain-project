package networking;

import old_networking.NetworkManager;

import java.io.*;
import java.net.Socket;

public class Peer implements Runnable {
    Socket connection;
    BufferedReader in;
    BufferedWriter out;
    NetworkManager networkManager;

    public Peer(Socket connection, NetworkManager networkManager) {
        this.connection = connection;
        this.networkManager = networkManager;
        try {
            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        String msg;
        try{
            while((msg = in.readLine()) != null){
                System.out.println("Msg: " + msg);
                networkManager.broadcast(msg);
            }
        } catch (Exception e){
            System.out.println("candy!");
        }
        //System.err.println("Connection with peer: " + connection.getInetAddress() + " established!");
    }

    public void send(String message) {
        try {
            out.write(message);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            System.err.println("Connection closed by remote host");
        }
    }
}
