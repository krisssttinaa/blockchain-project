package networking;

public enum MessageType {
    NEW_TRANSACTION, //for sending new transactions
    NEW_BLOCK, //for sending new blocks
    BLOCKCHAIN_REQUEST, //for requesting the blockchain
    BLOCKCHAIN_RESPONSE, //for sending the blockchain
    SHARE_PEER_LIST, //for sharing the list of known peers

    SYNC_REQUEST,  // New message type for requesting a full blockchain sync
    SYNC_RESPONSE,  // New message type for responding with the blockchain

    PEER_DISCOVERY_RESPONSE, //for responding to peer discovery requests
    PEER_DISCOVERY_REQUEST, //for requesting peer discovery
    PUBLIC_KEY_EXCHANGE, //for exchanging public keys
    PEER_DISCOVERY_ACK, //for acknowledging peer discovery
    TRANSACTION_CONFIRMATION //for acknowledging receipt of transactions
}