package ro.proiectsi.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import ro.proiectsi.df.DFUtils;
import ro.proiectsi.protocol.ChatProtocol;
import ro.proiectsi.protocol.MsgType;
import ro.proiectsi.protocol.Wire;
import ro.proiectsi.util.JsonUtil;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * LoggerAgent: primeste LOG_EVENT (de la server) si scrie in chat-history.jsonl.
 * La SHUTDOWN inchide fisierul si se opreste curat.
 */
public class LoggerAgent extends Agent {
    private BufferedWriter writer;
    private String logFile;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        logFile = (args != null && args.length >= 1 && args[0] != null)
                ? args[0].toString()
                : ChatProtocol.DEFAULT_LOG_FILE;

        DFUtils.registerService(this, ChatProtocol.DF_TYPE_LOGGER, "logger");

        try {
            writer = new BufferedWriter(new FileWriter(logFile, true));
        } catch (Exception e) {
            System.err.println("LoggerAgent cannot open file " + logFile + ": " + e.getMessage());
        }

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                if (!ChatProtocol.CONVERSATION_ID.equals(msg.getConversationId())) return;

                Map<String, String> decoded = Wire.decode(msg.getContent());
                MsgType type = Wire.typeOf(decoded);

                if (type == MsgType.LOG_EVENT) {
                    writeLog(decoded, msg.getSender() != null ? msg.getSender().getName() : "");
                } else if (type == MsgType.SHUTDOWN) {
                    // optional: logam shutdown si apoi inchidem
                    decoded.putIfAbsent("ev", "SHUTDOWN");
                    writeLog(decoded, msg.getSender() != null ? msg.getSender().getName() : "");
                    doDelete();
                }
            }
        });

        System.out.println(getLocalName() + " ready. Logging to " + logFile);
    }

    private void writeLog(Map<String, String> payload, String fromAid) {
        try {
            if (writer == null) return;

            Map<String, String> fields = new HashMap<>();
            fields.put("ts", Long.toString(System.currentTimeMillis()));
            fields.put("fromAid", fromAid);
            for (Map.Entry<String, String> e : payload.entrySet()) {
                fields.put(e.getKey(), e.getValue());
            }

            writer.write(JsonUtil.toJsonLine(fields));
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            System.err.println("LoggerAgent write failed: " + e.getMessage());
        }
    }

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        System.out.println(getLocalName() + " stopped.");
    }
}
