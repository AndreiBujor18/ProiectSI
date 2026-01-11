package ro.proiectsi.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import ro.proiectsi.df.DFUtils;
import ro.proiectsi.protocol.ChatProtocol;
import ro.proiectsi.protocol.MsgType;
import ro.proiectsi.protocol.Wire;
import ro.proiectsi.util.JsonUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * AssistantAgent (optional): primeste ASSIST_REQUEST de la server,
 * apeleaza un endpoint HTTP local (assistant-llm/assistant_server.py),
 * si trimite ASSIST_RESPONSE inapoi la server.
 */
public class AssistantAgent extends Agent {
    private String endpoint;
    private String model;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        endpoint = (args != null && args.length >= 1 && args[0] != null)
                ? args[0].toString()
                : ChatProtocol.DEFAULT_ASSISTANT_ENDPOINT;

        model = (args != null && args.length >= 2 && args[1] != null)
                ? args[1].toString()
                : ChatProtocol.DEFAULT_OLLAMA_MODEL;

        DFUtils.registerService(this, ChatProtocol.DF_TYPE_ASSISTANT, "assistant");

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                if (!ChatProtocol.CONVERSATION_ID.equals(msg.getConversationId())) return;

                Map<String, String> decoded = Wire.decode(msg.getContent());
                MsgType type = Wire.typeOf(decoded);
                if (type != MsgType.ASSIST_REQUEST) return;

                String username = decoded.get("username");
                String task = decoded.get("task");
                String context = decoded.get("context");

                String result = callAssistant(task, context, model);
                if (result == null || result.trim().isEmpty()) {
                    result = fallback(task, context);
                }

                Map<String, String> out = new HashMap<>();
                out.put("username", username == null ? "" : username);
                out.put("text", result);

                ACLMessage resp = new ACLMessage(ACLMessage.INFORM);
                resp.addReceiver(msg.getSender()); // server
                resp.setConversationId(ChatProtocol.CONVERSATION_ID);
                resp.setContent(Wire.encode(MsgType.ASSIST_RESPONSE, out));
                send(resp);
            }
        });

        System.out.println(getLocalName() + " ready. endpoint=" + endpoint + " model=" + model);
    }

    private String callAssistant(String task, String context, String model) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(800);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);

            String body = JsonUtil.assistantRequestJson(task, context, model);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            String text = JsonUtil.extractField(resp, "text");
            return text != null ? text : resp;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString().trim();
        }
    }

    private String fallback(String task, String context) {
        String t = task == null ? "" : task.toLowerCase();
        if (t.contains("summary")) return "Rezumat (fallback): serviciul LLM nu este disponibil.";
        return "Sugestie (fallback): raspunde politicos si pune o intrebare de clarificare.";
    }

    @Override
    protected void takeDown() {
        DFUtils.deregister(this);
        System.out.println(getLocalName() + " stopped.");
    }
}
