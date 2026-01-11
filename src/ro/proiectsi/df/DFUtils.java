package ro.proiectsi.df;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.ArrayList;
import java.util.List;

public final class DFUtils {
    private DFUtils() {}

    public static void registerService(Agent a, String type, String name) {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(a.getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType(type);
            sd.setName(name);
            dfd.addServices(sd);

            DFService.register(a, dfd);
        } catch (FIPAException e) {
            System.err.println("DF register failed for " + a.getLocalName() + ": " + e.getMessage());
        }
    }

    public static void deregister(Agent a) {
        try { DFService.deregister(a); } catch (Exception ignored) {}
    }

    public static AID findFirstByType(Agent a, String type) {
        List<AID> all = findAllByType(a, type);
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<AID> findAllByType(Agent a, String type) {
        List<AID> out = new ArrayList<>();
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType(type);
            template.addServices(sd);

            DFAgentDescription[] res = DFService.search(a, template);
            for (DFAgentDescription d : res) out.add(d.getName());
        } catch (FIPAException e) {
            System.err.println("DF search failed: " + e.getMessage());
        }
        return out;
    }
}
