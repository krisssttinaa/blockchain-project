package networking;

public class Message {
    public MessageType type;
    public String data;

    public Message(MessageType type, String data) {
        this.type = type;
        this.data = data;
    }

    public MessageType getType() {return type;}
    public void setType(MessageType type) {this.type = type;}
    public String getData() {return data;}
    public void setData(String data) {this.data = data;}
}