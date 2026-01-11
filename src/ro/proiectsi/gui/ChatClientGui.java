package ro.proiectsi.gui;

import jade.gui.GuiEvent;
import ro.proiectsi.agents.ChatClientAgent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ChatClientGui {

    private final ChatClientAgent agent;
    private final boolean admin;

    private JFrame frame;

    private JTextField tfUsername;
    private JButton btnConnect;
    private JButton btnShutdown;

    private DefaultListModel<String> usersModel;
    private JList<String> usersList;

    private JTextArea taChat;
    private JTextField tfMessage;
    private JButton btnSend;
    private JButton btnBroadcast;

    private JLabel lblStatus;

    private JTextArea taAssistant;
    private JButton btnSuggest;
    private JButton btnSummarize;

    private volatile String currentUsername = "";

    public ChatClientGui(ChatClientAgent agent, String usernameHint, boolean admin) {
        this.agent = agent;
        this.admin = admin;
        build(usernameHint);
    }

    private void build(String usernameHint) {
        frame = new JFrame("ProiectSI - Chat Client");
        frame.setSize(920, 580);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        JPanel login = new JPanel(new FlowLayout(FlowLayout.LEFT));

        login.add(new JLabel("Username:"));
        tfUsername = new JTextField(usernameHint, 16);
        login.add(tfUsername);

        btnConnect = new JButton("Connect");
        btnConnect.setEnabled(false);
        login.add(btnConnect);

        if (admin) {
            btnShutdown = new JButton("Shutdown platform");
            btnShutdown.setEnabled(false);
            login.add(btnShutdown);
        }

        top.add(login, BorderLayout.WEST);

        lblStatus = new JLabel("Caut server in DF...");
        top.add(lblStatus, BorderLayout.EAST);

        frame.add(top, BorderLayout.NORTH);

        usersModel = new DefaultListModel<>();
        usersList = new JList<>(usersModel);
        usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel("Utilizatori online:"), BorderLayout.NORTH);
        left.add(new JScrollPane(usersList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(220, 10));
        frame.add(left, BorderLayout.WEST);

        JPanel center = new JPanel(new GridLayout(1, 2));

        taChat = new JTextArea();
        taChat.setEditable(false);
        center.add(new JScrollPane(taChat));

        JPanel assistantPanel = new JPanel(new BorderLayout());
        taAssistant = new JTextArea();
        taAssistant.setEditable(false);

        JPanel assistantButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnSuggest = new JButton("Suggest reply");
        btnSummarize = new JButton("Summarize chat");
        assistantButtons.add(btnSuggest);
        assistantButtons.add(btnSummarize);

        assistantPanel.add(assistantButtons, BorderLayout.NORTH);
        assistantPanel.add(new JScrollPane(taAssistant), BorderLayout.CENTER);

        center.add(assistantPanel);
        frame.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        tfMessage = new JTextField();
        btnSend = new JButton("Send (private)");
        btnBroadcast = new JButton("Broadcast");

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(btnBroadcast);
        btns.add(btnSend);

        bottom.add(tfMessage, BorderLayout.CENTER);
        bottom.add(btns, BorderLayout.EAST);
        frame.add(bottom, BorderLayout.SOUTH);

        setChatEnabled(false);

        // Actions
        btnConnect.addActionListener(e -> {
            GuiEvent ge = new GuiEvent(this, ChatClientAgent.GUI_CONNECT);
            ge.addParameter(tfUsername.getText());
            agent.postGuiEvent(ge);
        });

        btnSend.addActionListener(e -> {
            String to = usersList.getSelectedValue();
            if (to == null || to.trim().isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Selecteaza un utilizator din lista.", "Destinatar", JOptionPane.WARNING_MESSAGE);
                return;
            }
            GuiEvent ge = new GuiEvent(this, ChatClientAgent.GUI_SEND);
            ge.addParameter(to);
            ge.addParameter(tfMessage.getText());
            agent.postGuiEvent(ge);
            tfMessage.setText("");
        });

        btnBroadcast.addActionListener(e -> {
            GuiEvent ge = new GuiEvent(this, ChatClientAgent.GUI_BROADCAST);
            ge.addParameter(tfMessage.getText());
            agent.postGuiEvent(ge);
            tfMessage.setText("");
        });

        btnSuggest.addActionListener(e -> {
            GuiEvent ge = new GuiEvent(this, ChatClientAgent.GUI_ASSIST);
            ge.addParameter("suggest");
            ge.addParameter(getChatContext());
            agent.postGuiEvent(ge);
        });

        btnSummarize.addActionListener(e -> {
            GuiEvent ge = new GuiEvent(this, ChatClientAgent.GUI_ASSIST);
            ge.addParameter("summary");
            ge.addParameter(getChatContext());
            agent.postGuiEvent(ge);
        });

        if (admin) {
            btnShutdown.addActionListener(e -> {
                int ok = JOptionPane.showConfirmDialog(frame,
                        "Esti sigur ca vrei sa opresti toti agentii?",
                        "Shutdown", JOptionPane.YES_NO_OPTION);
                if (ok == JOptionPane.YES_OPTION) {
                    GuiEvent ge = new GuiEvent(this, ChatClientAgent.GUI_SHUTDOWN);
                    agent.postGuiEvent(ge);
                }
            });
        }

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                GuiEvent ge = new GuiEvent(ChatClientGui.this, ChatClientAgent.GUI_LOGOUT);
                agent.postGuiEvent(ge);
                dispose();
            }
        });
    }

    public void showGui() {
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    public JFrame getFrame() { return frame; }

    public void dispose() {
        SwingUtilities.invokeLater(() -> frame.dispose());
    }

    public void setStatus(String s) {
        SwingUtilities.invokeLater(() -> lblStatus.setText(s == null ? "" : s));
    }

    public void setConnectEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> btnConnect.setEnabled(enabled));
    }

    public void setChatEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            usersList.setEnabled(enabled);
            tfMessage.setEnabled(enabled);
            btnSend.setEnabled(enabled);
            btnBroadcast.setEnabled(enabled);
            btnSuggest.setEnabled(enabled);
            btnSummarize.setEnabled(enabled);
            if (admin && btnShutdown != null) btnShutdown.setEnabled(enabled);
        });
    }

    public void updateUserList(String csv) {
        SwingUtilities.invokeLater(() -> {
            usersModel.clear();
            if (csv == null || csv.trim().isEmpty()) return;
            for (String p : csv.split(",")) {
                String u = p.trim();
                if (!u.isEmpty() && !u.equals(getCurrentUsername())) usersModel.addElement(u);
            }
        });
    }

    public void appendSystem(String msg) {
        SwingUtilities.invokeLater(() -> taChat.append("[SYSTEM] " + safe(msg) + "\n"));
    }

    public void appendChat(String from, String text, boolean broadcast) {
        SwingUtilities.invokeLater(() -> {
            String tag = broadcast ? "[BROADCAST]" : "[PRIVATE]";
            taChat.append(tag + " " + safe(from) + ": " + safe(text) + "\n");
        });
    }

    public void appendOwn(String text, boolean broadcast, String to) {
        SwingUtilities.invokeLater(() -> {
            String tag = broadcast ? "[YOU->ALL]" : "[YOU->" + safe(to) + "]";
            taChat.append(tag + " " + safe(text) + "\n");
        });
    }

    public void showAssistantText(String txt) {
        SwingUtilities.invokeLater(() -> taAssistant.setText(safe(txt)));
    }

    public String getChatContext() {
        String all = taChat.getText();
        if (all == null) return "";
        int start = Math.max(0, all.length() - 2000);
        return all.substring(start);
    }

    public String getCurrentUsername() { return currentUsername == null ? "" : currentUsername; }

    public void setCurrentUsername(String u) {
        currentUsername = u == null ? "" : u.trim();
        SwingUtilities.invokeLater(() -> frame.setTitle("ProiectSI - Chat Client (" + currentUsername + ")"));
    }

    private String safe(String s) { return s == null ? "" : s; }
}
