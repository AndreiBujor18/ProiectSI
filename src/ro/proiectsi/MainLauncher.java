package ro.proiectsi;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

/**
 * Launcher pentru ProiectSI.
 * Porneste:
 * - RMA
 * - Sniffer
 * - LoggerAgent (persistenta)
 * - AssistantAgent (optional, cu fallback daca serviciul nu ruleaza)
 * - ChatServerAgent (server central)
 * - 2 ChatClientAgent (ana admin + maria)
 */
public class MainLauncher {

    public static void main(String[] args) throws Exception {
        Runtime rt = Runtime.instance();
        rt.setCloseVM(true);

        ProfileImpl p = new ProfileImpl();
        p.setParameter(Profile.MAIN, "true");

        AgentContainer main = rt.createMainContainer(p);

        start(main, "rma", "jade.tools.rma.rma", null);
        start(main, "sniffer", "jade.tools.sniffer.Sniffer", null);

        start(main, "logger", "ro.proiectsi.agents.LoggerAgent", new Object[]{"chat-history.jsonl"});
        start(main, "assistant", "ro.proiectsi.agents.AssistantAgent",
                new Object[]{"http://127.0.0.1:8000/assist", "llama3.1"});

        start(main, "server", "ro.proiectsi.agents.ChatServerAgent", null);

        // Clienti (minim 3 agenti total e bifat deja cu server+logger+2 clienti)
        start(main, "c1", "ro.proiectsi.agents.ChatClientAgent", new Object[]{"ana", "true"});   // admin=true
        start(main, "c2", "ro.proiectsi.agents.ChatClientAgent", new Object[]{"maria", "false"});
        
        //Ollama
        start(main, "assistant", "ro.proiectsi.agents.AssistantAgent",
                new Object[]{"http://127.0.0.1:8000/assist", "llama3.1"});

    }

    private static void start(AgentContainer c, String name, String className, Object[] args) throws Exception {
        AgentController a = c.createNewAgent(name, className, args);
        a.start();
    }
}
