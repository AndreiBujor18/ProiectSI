package ro.proiectsi.protocol;

public final class ChatProtocol {
    private ChatProtocol() {}

    // Pentru Sniffer: tot ecosistemul foloseste acelasi conversationId
    public static final String CONVERSATION_ID = "PROIECTSI-CHAT-CENTRAL-V1";

    // Prefix in ACLMessage.getContent()
    public static final String WIRE_PREFIX = "CHAT/1";

    // DF service types
    public static final String DF_TYPE_SERVER = "chat-server";
    public static final String DF_TYPE_CLIENT = "chat-client";
    public static final String DF_TYPE_LOGGER = "chat-logger";
    public static final String DF_TYPE_ASSISTANT = "chat-assistant";

    // Default values
    public static final String DEFAULT_LOG_FILE = "chat-history.jsonl";
    public static final String DEFAULT_ASSISTANT_ENDPOINT = "http://127.0.0.1:8000/assist";
    public static final String DEFAULT_OLLAMA_MODEL = "llama3.1";
}
