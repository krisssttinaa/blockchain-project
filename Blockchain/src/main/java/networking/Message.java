package networking;

public class Message {
    public MessageType type;
    public String data;

    public Message(MessageType type, String data) {
        this.type = type;
        this.data = data;
    }

    // Getters and Setters
    public MessageType getType() {return type;}
    public void setType(MessageType type) {this.type = type;}
    public String getData() {return data;}
    public void setData(String data) {this.data = data;}
}

/*
Message message = new Message("NewBlock", new Gson().toJson(block));
String jsonString = new Gson().toJson(message);
// Now you can send jsonString over the network

Message receivedMessage = new Gson().fromJson(jsonString, Message.class);
// Depending on the message type, you might further deserialize the payload
Block block = new Gson().fromJson(receivedMessage.getPayload(), Block.class);




// Example of sending a message
try (Socket socket = new Socket(peerAddress, PORT)) {
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    writer.write(jsonString);
    writer.newLine(); //denote end of message
    writer.flush();
} catch (IOException e) {
    e.printStackTrace();
}

// Example of receiving a message
try (ServerSocket serverSocket = new ServerSocket(PORT);
     Socket clientSocket = serverSocket.accept();
     BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
    String receivedJson = reader.readLine(); // Read the JSON string
    Message receivedMessage = new Gson().fromJson(receivedJson, Message.class);
    // Process the received message
} catch (IOException e) {
    e.printStackTrace();
}

* */