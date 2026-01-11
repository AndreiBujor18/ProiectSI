package ro.proiectsi.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import ro.proiectsi.df.DFUtils;
import ro.proiectsi.protocol.ChatProtocol;
import ro.proiectsi.protocol.MsgType;
import ro.proiectsi.protocol.Wire;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ChatServerAgent: server central (hub) pentru chat.
 * - gestioneaza LOGIN/LOGOUT
 * - tine username -> AID pentru online
 * - ruteaza SEND (private/broadcast)
 * - trimite USERLIST tuturor
 * - trimite LOG_EVENT catre LoggerAgent
 * - shutdown centralizat
 */
public class ChatServerAgent extends Agent {

    private final Map<String, AID> online = new LinkedHashMap<>();

    private volatile AID loggerAid;
    private volatile AID assistantAid;

    @Override
    protected void setup() {
        DFUtils.registerService(this, ChatProtocol.DF_TYPE_SERVER, "central-server");

        addBehaviour(new TickerBehaviour(this, 1500) {
            @Override
            protected void onTick() {
                if (loggerAid == null) loggerAid = DFUtils.findFirstByType(myAgent, ChatProtocol.DF_TYPE_LOGGER);
                if (assistantAid == null) assistantAid = DFUtils.findFirstByType(myAgent, ChatProtocol.DF_TYPE_ASSISTANT);
            }
        });

        addBehaviour(new ServerLoop());

        System.out.println(getLocalName() + " started (server).");
    }

    private class ServerLoop extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) { block(); return; }
            if (!ChatProtocol.CONVERSATION_ID.equals(msg.getConversationId())) return;

            Map<String, String> decoded = Wire.decode(msg.getContent());
            MsgType type = Wire.typeOf(decoded);
            if (type == null) {
                sendError(msg.getSender(), "Tip mesaj necunoscut");
                return;
            }

            switch (type) {
                case LOGIN: handleLogin(msg, decoded); break;
                case LOGOUT: handleLogout(msg, decoded); break;
                case SEND: handleSend(msg, decoded); break;
                case SHUTDOWN_REQUEST: handleShutdownRequest(decoded); break;
                case ASSIST_REQUEST: handleAssistRequest(msg, decoded); break;
                case ASSIST_RESPONSE: handleAssistResponse(decoded); break;
                default:
                    sendError(msg.getSender(), "Tip neacceptat: " + type);
            }
        }
    }

    private void handleLogin(ACLMessage msg, Map<String, String> decoded) {
        String username = safe(decoded.get("username"));
        if (username.isEmpty()) {
            loginFail(msg.getSender(), "Username gol");
            return;
        }
        if (online.containsKey(username)) {
            loginFail(msg.getSender(), "Username deja folosit");
            return;
        }

        online.put(username, msg.getSender());

        Map<String, String> out = new HashMap<>();
        out.put("username", username);

        ACLMessage ok = new ACLMessage(ACLMessage.INFORM);
        ok.addReceiver(msg.getSender());
        ok.setConversationId(ChatProtocol.CONVERSATION_ID);
        ok.setContent(Wire.encode(MsgType.LOGIN_OK, out));
        send(ok);

        logEvent("LOGIN", username, "", "");
        broadcastUserList();
    }

    private void handleLogout(ACLMessage msg, Map<String, String> decoded) {
        String username = safe(decoded.get("username"));
        if (!username.isEmpty()) {
            online.remove(username);
            logEvent("LOGOUT", username, "", "");
            broadcastUserList();
        }
    }

    private void handleSend(ACLMessage msg, Map<String, String> decoded) {
        String from = safe(decoded.get("from"));
        String to = safe(decoded.get("to"));
        String text = safe(decoded.get("text"));

        if (from.isEmpty() || text.isEmpty()) {
            sendError(msg.getSender(), "Campuri lipsa: from/text");
            return;
        }

        boolean isBroadcast = to.isEmpty() || "*".equals(to);
        if (isBroadcast) {
            int delivered = 0;
            for (Map.Entry<String, AID> e : online.entrySet()) {
                String uname = e.getKey();
                if (uname.equals(from)) continue;
                deliverToClient(e.getValue(), from, "*", text);
                delivered++;
            }
            logEvent("BROADCAST", from, "*", text);
            ack(msg.getSender(), "broadcast_delivered=" + delivered);
            return;
        }

        AID dest = online.get(to);
        if (dest == null) {
            sendError(msg.getSender(), "Destinatar offline/necunoscut: " + to);
            return;
        }

        deliverToClient(dest, from, to, text);
        logEvent("MSG", from, to, text);
        ack(msg.getSender(), "delivered_to=" + to);
    }

    private void handleShutdownRequest(Map<String, String> decoded) {
        String by = safe(decoded.get("by"));
        logEvent("SHUTDOWN_REQUEST", by, "", "");
        broadcastShutdown("Requested by " + by);
        doDelete();
    }

    private void handleAssistRequest(ACLMessage msg, Map<String, String> decoded) {
        if (assistantAid == null) {
            sendError(msg.getSender(), "AssistantAgent nu este disponibil (nu e gasit in DF).");
            return;
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("username", safe(decoded.get("username")));
        payload.put("task", safe(decoded.get("task")));
        payload.put("context", safe(decoded.get("context")));

        ACLMessage fwd = new ACLMessage(ACLMessage.REQUEST);
        fwd.addReceiver(assistantAid);
        fwd.setConversationId(ChatProtocol.CONVERSATION_ID);
        fwd.setContent(Wire.encode(MsgType.ASSIST_REQUEST, payload));
        send(fwd);

        ack(msg.getSender(), "assist_forwarded=true");
    }

    private void handleAssistResponse(Map<String, String> decoded) {
        String username = safe(decoded.get("username"));
        String text = safe(decoded.get("text"));

        AID client = online.get(username);
        if (client == null) return;

        Map<String, String> out = new HashMap<>();
        out.put("text", text);

        ACLMessage resp = new ACLMessage(ACLMessage.INFORM);
        resp.addReceiver(client);
        resp.setConversationId(ChatProtocol.CONVERSATION_ID);
        resp.setContent(Wire.encode(MsgType.ASSIST_RESPONSE, out));
        send(resp);

        logEvent("ASSIST_RESPONSE", "assistant", username, text);
    }

    private void broadcastUserList() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String u : online.keySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(u);
        }

        Map<String, String> out = new HashMap<>();
        out.put("users", sb.toString());

        for (AID aid : online.values()) {
            ACLMessage ul = new ACLMessage(ACLMessage.INFORM);
            ul.addReceiver(aid);
            ul.setConversationId(ChatProtocol.CONVERSATION_ID);
            ul.setContent(Wire.encode(MsgType.USERLIST, out));
            send(ul);
        }
    }

    private void deliverToClient(AID client, String from, String to, String text) {
        Map<String, String> out = new HashMap<>();
        out.put("from", from);
        out.put("to", to);
        out.put("text", text);

        ACLMessage deliver = new ACLMessage(ACLMessage.INFORM);
        deliver.addReceiver(client);
        deliver.setConversationId(ChatProtocol.CONVERSATION_ID);
        deliver.setContent(Wire.encode(MsgType.DELIVER, out));
        send(deliver);
    }

    private void broadcastShutdown(String reason) {
        Map<String, String> out = new HashMap<>();
        out.put("reason", reason);

        for (AID aid : online.values()) {
            ACLMessage s = new ACLMessage(ACLMessage.INFORM);
            s.addReceiver(aid);
            s.setConversationId(ChatProtocol.CONVERSATION_ID);
            s.setContent(Wire.encode(MsgType.SHUTDOWN, out));
            send(s);
        }
        if (loggerAid != null) {
            ACLMessage s = new ACLMessage(ACLMessage.INFORM);
            s.addReceiver(loggerAid);
            s.setConversationId(ChatProtocol.CONVERSATION_ID);
            s.setContent(Wire.encode(MsgType.SHUTDOWN, out));
            send(s);
        }
        if (assistantAid != null) {
            ACLMessage s = new ACLMessage(ACLMessage.INFORM);
            s.addReceiver(assistantAid);
            s.setConversationId(ChatProtocol.CONVERSATION_ID);
            s.setContent(Wire.encode(MsgType.SHUTDOWN, out));
            send(s);
        }
    }

    private void logEvent(String ev, String from, String to, String text) {
        if (loggerAid == null) return;

        Map<String, String> out = new HashMap<>();
        out.put("type", MsgType.LOG_EVENT.name());
        out.put("ev", ev);
        out.put("from", safe(from));
        out.put("to", safe(to));
        out.put("text", safe(text));

        ACLMessage log = new ACLMessage(ACLMessage.INFORM);
        log.addReceiver(loggerAid);
        log.setConversationId(ChatProtocol.CONVERSATION_ID);
        log.setContent(Wire.encode(MsgType.LOG_EVENT, out));
        send(log);
    }

    private void ack(AID to, String info) {
        Map<String, String> out = new HashMap<>();
        out.put("info", info);

        ACLMessage ack = new ACLMessage(ACLMessage.INFORM);
        ack.addReceiver(to);
        ack.setConversationId(ChatProtocol.CONVERSATION_ID);
        ack.setContent(Wire.encode(MsgType.ACK, out));
        send(ack);
    }

    private void sendError(AID to, String err) {
        Map<String, String> out = new HashMap<>();
        out.put("message", err);

        ACLMessage e = new ACLMessage(ACLMessage.INFORM);
        e.addReceiver(to);
        e.setConversationId(ChatProtocol.CONVERSATION_ID);
        e.setContent(Wire.encode(MsgType.ERROR, out));
        send(e);
    }

    private void loginFail(AID to, String reason) {
        Map<String, String> out = new HashMap<>();
        out.put("reason", reason);

        ACLMessage fail = new ACLMessage(ACLMessage.INFORM);
        fail.addReceiver(to);
        fail.setConversationId(ChatProtocol.CONVERSATION_ID);
        fail.setContent(Wire.encode(MsgType.LOGIN_FAIL, out));
        send(fail);
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        System.out.println(getLocalName() + " stopped.");
    }
}
