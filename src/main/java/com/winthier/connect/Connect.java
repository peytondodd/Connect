package com.winthier.connect;

import com.winthier.connect.packet.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Connect {
    final String name;
    final File configFile;
    final ConnectHandler handler;
    String password = null;

    Server server = null;
    final List<Client> clients = new ArrayList<>();
    @Getter static Connect instance = null;

    public void start() {
        instance = this;
        try {
            BufferedReader in = new BufferedReader(new FileReader(configFile));
            String line = null;
            while (null != (line = in.readLine())) {
                if (line.startsWith("#")) continue;
                if (line.isEmpty()) continue;
                if (password == null) {
                    password = line;
                    continue;
                }
                String tokens[] = line.split("\\s+", 3);
                if (tokens.length != 3) continue;
                String serverName = tokens[0];
                int serverPort = Integer.parseInt(tokens[1]);
                String serverDisplayName = tokens[2];
                if (this.name.equals(serverName)) {
                    server = new Server(this, serverName, serverPort, serverDisplayName);
                    handler.runThread(server);
                }
                Client client = new Client(this, serverName, serverPort, serverDisplayName);
                clients.add(client);
                handler.runThread(client);
            }
            in.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        for (Client client: clients) {
            client.send(ConnectionMessages.PING.message(client));
        }
    }

    public void stop() {
        if (server != null) server.quit();
        for (Client client: clients) client.quit();
    }

    public Client getClient(String name) {
        for (Client client: clients) {
            if (client.getName().equals(name)) return client;
        }
        return null;
    }

    public boolean send(String name, String channel, Object payload) {
        Client client = getClient(name);
        if (client == null) return false;
        Message message = new Message(channel, this.name, client.getName(), payload);
        client.send(message);
        return true;
    }

    public void broadcast(String channel, Object payload, boolean all) {
        for (Client client: clients) {
            if (!all && name.equals(client.getName())) continue;
            Message message = new Message(channel, name, client.getName(), payload);
            client.send(message);
        }
    }

    public void broadcast(String channel, Object payload) {
        broadcast(channel, payload, false);
    }

    public void broadcastAll(String channel, Object payload) {
        broadcast(channel, payload, true);
    }

    public void pingAllConnected() {
        for (Client client: clients) {
            if (client.getStatus() == ConnectionStatus.CONNECTED) {
                client.send(ConnectionMessages.PING.message(client));
            }
        }
    }

    public void broadcastPlayerList(List<OnlinePlayer> players) {
        for (Client client: clients) {
            if (client.getStatus() == ConnectionStatus.CONNECTED) {
                PlayerList playerList = new PlayerList(PlayerList.Type.LIST, players);
                Message message = new Message("Connect", name, client.getName(), playerList.serialize());
                client.send(message);
            }
        }
    }

    public void broadcastPlayerStatus(OnlinePlayer player, boolean online) {
        for (Client client: clients) {
            if (client.getStatus() == ConnectionStatus.CONNECTED) {
                PlayerList playerList = new PlayerList(online ? PlayerList.Type.JOIN : PlayerList.Type.QUIT, Arrays.asList(player));
                Message message = new Message("Connect", name, client.getName(), playerList.serialize());
                client.send(message);
            }
        }
    }

    public void broadcastRemoteCommand(OnlinePlayer sender, String[] args) {
        for (Client client: clients) {
            if (client.getStatus() == ConnectionStatus.CONNECTED) {
                RemoteCommand remoteCommand = new RemoteCommand(sender, args);
                Message message = new Message("Connect", name, client.getName(), remoteCommand.serialize());
                client.send(message);
            }
        }
    }

    public List<OnlinePlayer> getOnlinePlayers() {
        List<OnlinePlayer> result = new ArrayList<>();
        if (server != null) {
            for (ServerConnection sc: server.connections) {
                result.addAll(sc.getOnlinePlayers());
            }
        }
        return result;
    }

    public OnlinePlayer findOnlinePlayer(String name) {
        if (server != null) {
            for (ServerConnection sc: server.connections) {
                for (OnlinePlayer onlinePlayer: sc.getOnlinePlayers()) {
                    if (onlinePlayer.getName().equals(name)) {
                        return onlinePlayer;
                    }
                }
            }
        }
        return null;
    }
}
