package ro.proiectsi.protocol;

public enum MsgType {
    // Presence / auth
    LOGIN,
    LOGIN_OK,
    LOGIN_FAIL,
    LOGOUT,
    USERLIST,

    // Chat
    SEND,       // client -> server
    DELIVER,    // server -> client
    ACK,
    ERROR,

    // Logging
    LOG_EVENT,  // server -> logger

    // Shutdown centralizat
    SHUTDOWN_REQUEST,
    SHUTDOWN,

    // Assistant (optional)
    ASSIST_REQUEST,
    ASSIST_RESPONSE;

    public static MsgType fromString(String s) {
        if (s == null) return null;
        try {
            return MsgType.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
