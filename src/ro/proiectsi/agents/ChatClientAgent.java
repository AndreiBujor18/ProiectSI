package ro.proiectsi.agents;

import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.gui.GuiAgent;
import jade.gui.GuiEvent;
import jade.lang.acl.ACLMessage;
import ro.proiectsi.df.DFUtils;
import ro.proiectsi.gui.ChatClientGui;
import ro.proiectsi.protocol.ChatProtocol;
import ro.proiectsi.protocol.MsgType;
import ro.proiectsi.protocol.Wire;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * ChatClientAgent: client JADE cu GUI (Swing).
 * - descopera serverul prin DF
 * - trimite LOGIN/SEND/LOGOUT/SHUTDOWN_REQUEST (admin) catre server
 * - primeste DELIVER/USERLIST/ERROR/SHUTDOWN
 */
public class ChatClientAgent extends GuiAgent {

    public static final int GUI_CONNECT = 1;
    public static final int GUI_SEND = 2;
    public static final int GUI_BROADCAST = 3;
    public static final int GUI_LOGOUT = 4;
    public static final int GUI_SHUTDOWN = 5;
    public static final int GUI_ASSIST = 6;

    private volatile AID serverAid;
    private volatile boolean loggedIn = false;

    private String usernameHint = "";
    private boolean admin = false;

    private ChatClientGui gui;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length >= 1 && args[0] != null) usernameHint = args[0].toString();
        if (args != null && args.length >= 2 && args[1] != null) admin = Boolean.parseBoolean(args[1].toString());

        gui = new ChatClientGui(this, usernameHint, admin);
        gui.showGui();

        // periodic DF search pentru server
        addBehaviour(new TickerBehaviour(this, 1200) {
            @Override
            protected void onTick() {
                if (serverAid == null) {
                    serverAid = DFUtils.findFirstByType(myAgent, ChatProtocol.DF_TYPE_SERVER);
                    if (serverAid != null) {
                        gui.setStatus("Server gasit: " + serverAid.getLocalName());
                        gui.setConnectEnabled(true);
                    } else {
                        gui.setStatus("Caut server in DF...");
                        gui.setConnectEnabled(false);
                    }
                }
            }
        });

        // receive loop
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                if (!ChatProtocol.CONVERSATION_ID.equals(msg.getConversationId())) return;

                Map<String, String> decoded = Wire.decode(msg.getContent());
                MsgType type = Wire.typeOf(decoded);

                if (type == MsgType.LOGIN_OK) {
                    loggedIn = true;
                    String u = decoded.get("username");
                    if (u != null && !u.trim().isEmpty()) gui.setCurrentUsername(u.trim());
                    gui.appendSystem("Conectat ca: " + gui.getCurrentUsername());
                    gui.setChatEnabled(true);

                    // Inregistram clientul in DF dupa login
                    DFUtils.registerService(myAgent, ChatProtocol.DF_TYPE_CLIENT, gui.getCurrentUsername());
                }
                else if (type == MsgType.LOGIN_FAIL) {
                    loggedIn = false;
                    gui.appendSystem("LOGIN_FAIL: " + decoded.get("reason"));
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui.getFrame(),
                            "Login esuat: " + decoded.get("reason"),
                            "LOGIN_FAIL",
                            JOptionPane.ERROR_MESSAGE));
                }
                else if (type == MsgType.USERLIST) {
                    gui.updateUserList(decoded.get("users"));
                }
                else if (type == MsgType.DELIVER) {
                    boolean broadcast = "*".equals(decoded.get("to"));
                    gui.appendChat(decoded.get("from"), decoded.get("text"), broadcast);
                }
                else if (type == MsgType.ASSIST_RESPONSE) {
                    gui.showAssistantText(decoded.get("text"));
                }
                else if (type == MsgType.ERROR) {
                    gui.appendSystem("ERROR: " + decoded.get("message"));
                }
                else if (type == MsgType.ACK) {
                    String info = decoded.get("info");
                    if (info != null && !info.trim().isEmpty()) gui.setStatus("ACK: " + info);
                }
                else if (type == MsgType.SHUTDOWN) {
                    gui.appendSystem("SHUTDOWN: " + decoded.get("reason"));
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(gui.getFrame(),
                            "Server shutdown: " + decoded.get("reason"),
                            "SHUTDOWN",
                            JOptionPane.WARNING_MESSAGE));
                    gui.dispose();
                    doDelete();
                }
            }
        });

        System.out.println(getLocalName() + " started (client).");
    }

    @Override
    protected void onGuiEvent(GuiEvent ge) {
        if (serverAid == null && ge.getType() != GUI_LOGOUT) {
            gui.appendSystem("Server indisponibil (inca). Asteapta DF discovery.");
            return;
        }

        switch (ge.getType()) {
            case GUI_CONNECT:
                handleConnect((String) ge.getParameter(0));
                break;
            case GUI_SEND:
                handleSend((String) ge.getParameter(0), (String) ge.getParameter(1));
                break;
            case GUI_BROADCAST:
                handleBroadcast((String) ge.getParameter(0));
                break;
            case GUI_LOGOUT:
                handleLogout();
                break;
            case GUI_SHUTDOWN:
                handleShutdown();
                break;
            case GUI_ASSIST:
                handleAssist((String) ge.getParameter(0), (String) ge.getParameter(1));
                break;
            default:
                gui.appendSystem("Eveniment GUI necunoscut: " + ge.getType());
        }
    }

    private void handleConnect(String username) {
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(gui.getFrame(), "Introdu un username.", "Username", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Map<String, String> out = new HashMap<>();
        out.put("username", username.trim());

        ACLMessage login = new ACLMessage(ACLMessage.REQUEST);
        login.addReceiver(serverAid);
        login.setConversationId(ChatProtocol.CONVERSATION_ID);
        login.setContent(Wire.encode(MsgType.LOGIN, out));
        send(login);

        gui.setStatus("Trimit LOGIN...");
    }

    private void handleSend(String toUser, String text) {
        if (!loggedIn) { gui.appendSystem("Nu esti logat."); return; }
        if (text == null || text.trim().isEmpty()) return;
        if (toUser == null || toUser.trim().isEmpty()) {
            JOptionPane.showMessageDialog(gui.getFrame(), "Selecteaza un destinatar.", "Destinatar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Map<String, String> out = new HashMap<>();
        out.put("from", gui.getCurrentUsername());
        out.put("to", toUser.trim());
        out.put("text", text);

        ACLMessage sendMsg = new ACLMessage(ACLMessage.REQUEST);
        sendMsg.addReceiver(serverAid);
        sendMsg.setConversationId(ChatProtocol.CONVERSATION_ID);
        sendMsg.setContent(Wire.encode(MsgType.SEND, out));
        send(sendMsg);

        gui.appendOwn(text, false, toUser.trim());
    }

    private void handleBroadcast(String text) {
        if (!loggedIn) { gui.appendSystem("Nu esti logat."); return; }
        if (text == null || text.trim().isEmpty()) return;

        Map<String, String> out = new HashMap<>();
        out.put("from", gui.getCurrentUsername());
        out.put("to", "*");
        out.put("text", text);

        ACLMessage sendMsg = new ACLMessage(ACLMessage.REQUEST);
        sendMsg.addReceiver(serverAid);
        sendMsg.setConversationId(ChatProtocol.CONVERSATION_ID);
        sendMsg.setContent(Wire.encode(MsgType.SEND, out));
        send(sendMsg);

        gui.appendOwn(text, true, "*");
    }

    private void handleLogout() {
        if (!loggedIn) {
            doDelete();
            return;
        }

        Map<String, String> out = new HashMap<>();
        out.put("username", gui.getCurrentUsername());

        ACLMessage lo = new ACLMessage(ACLMessage.REQUEST);
        lo.addReceiver(serverAid);
        lo.setConversationId(ChatProtocol.CONVERSATION_ID);
        lo.setContent(Wire.encode(MsgType.LOGOUT, out));
        send(lo);

        loggedIn = false;
        gui.appendSystem("Logout trimis.");
        doDelete();
    }

    private void handleShutdown() {
        if (!admin) {
            gui.appendSystem("Nu ai drepturi de admin pentru shutdown.");
            return;
        }

        Map<String, String> out = new HashMap<>();
        out.put("by", gui.getCurrentUsername());

        ACLMessage sd = new ACLMessage(ACLMessage.REQUEST);
        sd.addReceiver(serverAid);
        sd.setConversationId(ChatProtocol.CONVERSATION_ID);
        sd.setContent(Wire.encode(MsgType.SHUTDOWN_REQUEST, out));
        send(sd);

        gui.appendSystem("SHUTDOWN_REQUEST trimis.");
    }

    private void handleAssist(String task, String context) {
        if (!loggedIn) { gui.appendSystem("Nu esti logat."); return; }

        Map<String, String> out = new HashMap<>();
        out.put("username", gui.getCurrentUsername());
        out.put("task", task == null ? "suggest" : task);
        out.put("context", context == null ? "" : context);

        ACLMessage ar = new ACLMessage(ACLMessage.REQUEST);
        ar.addReceiver(serverAid);
        ar.setConversationId(ChatProtocol.CONVERSATION_ID);
        ar.setContent(Wire.encode(MsgType.ASSIST_REQUEST, out));
        send(ar);

        gui.setStatus("Assistant request trimis...");
    }

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        System.out.println(getLocalName() + " stopped.");
    }
}
