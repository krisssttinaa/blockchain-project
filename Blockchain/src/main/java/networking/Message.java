package networking;

public class Message {
    private final MessageType type;
    private final String data;

    public Message(MessageType type, String data) {
        this.type = type;
        this.data = data;
    }

    public MessageType getType() {return type;}
    public String getData() {return data;}
}