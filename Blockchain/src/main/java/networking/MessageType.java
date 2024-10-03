package networking;

public enum MessageType {
    NEW_TRANSACTION,
    NEW_BLOCK,
    SHARE_PEER_LIST,
    PEER_DISCOVERY_RESPONSE,
    PEER_DISCOVERY_REQUEST,
    PUBLIC_KEY_EXCHANGE,
    CONNECTION_ESTABLISHED,
    TIP_REQUEST,
    TIP_RESPONSE,
    BLOCK_REQUEST,
    BLOCK_RESPONSE,
    SINGLE_BLOCK_REQUEST
}