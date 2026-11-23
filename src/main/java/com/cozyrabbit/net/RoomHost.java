package com.cozyrabbit.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hosts a study room: accepts friends over TCP, answers "is room 1234 here?" broadcasts,
 * and sends everyone the full member list once a second.
 */
public final class RoomHost implements Room {

    private static final long STALE_MS = 20_000;

    private final String code;
    private final Listener listener;
    private final ServerSocket server;
    private final DatagramSocket discovery;
    private final Map<String, MemberState> members = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(daemon("room-roster"));
    private volatile String ownId = "";
    private volatile boolean closed;

    private final class Client {
        final Wire wire;
        volatile String memberId;

        Client(Wire wire) {
            this.wire = wire;
        }
    }

    public RoomHost(Listener listener) throws IOException {
        this.listener = listener;
        this.code = String.format("%04d", (int) (Math.random() * 10_000));
        ServerSocket s;
        try {
            s = new ServerSocket(TCP_PORT);
        } catch (IOException busy) {
            s = new ServerSocket(0);
        }
        server = s;
        DatagramSocket d = null;
        try {
            d = new DatagramSocket(null);
            d.setReuseAddress(true);
            d.bind(new InetSocketAddress(DISCOVERY_PORT));
        } catch (IOException e) {
            // Friends can still join by typing the address.
            if (d != null) {
                d.close();
            }
            d = null;
        }
        discovery = d;
        start(this::acceptLoop, "room-accept");
        if (discovery != null) {
            start(this::discoveryLoop, "room-discovery");
        }
        ticker.scheduleAtFixedRate(this::broadcastRoster, 1, 1, TimeUnit.SECONDS);
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    private static void start(Runnable r, String name) {
        daemon(name).newThread(r).start();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = server.accept();
                Client c = new Client(new Wire(s));
                start(() -> serve(c), "room-client");
            } catch (IOException e) {
                if (!closed) {
                    listener.onStatus("Room problem: " + e.getMessage());
                }
            }
        }
    }

    private void serve(Client c) {
        try {
            String[] hello = c.wire.receive();
            if (hello == null || !hello[0].equals("HELLO") || !hello[1].startsWith(Wire.MAGIC)) {
                return;
            }
            String theirCode = hello[1].substring(Wire.MAGIC.length());
            if (!theirCode.isEmpty() && !theirCode.equals(code)) {
                c.wire.send("REJECT", "Wrong room code");
                return;
            }
            clients.add(c);
            c.wire.send("ROSTER", roster());
            while (!closed) {
                String[] msg = c.wire.receive();
                if (msg == null) {
                    break;
                }
                switch (msg[0]) {
                    case "STATE" -> {
                        MemberState m = MemberState.decode(msg[1]);
                        c.memberId = m.id();
                        members.put(m.id(), m);
                        lastSeen.put(m.id(), System.currentTimeMillis());
                    }
                    case "CATCH" -> {
                        String[] f = msg[1].split(String.valueOf(MemberState.FIELD), -1);
                        if (f.length >= 3) {
                            listener.onCatch(f[0], f[1], f[2]);
                            relay("CATCH", msg[1], c);
                        }
                    }
                    case "BYE" -> {
                        return;
                    }
                    default -> {
                        // unknown message from a newer version: ignore
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // the friend left or the Wi-Fi dropped
        } finally {
            clients.remove(c);
            if (c.memberId != null) {
                members.remove(c.memberId);
                lastSeen.remove(c.memberId);
            }
            c.wire.close();
        }
    }

    private void discoveryLoop() {
        byte[] buf = new byte[256];
        while (!closed) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                discovery.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                if (msg.equals(Wire.MAGIC + "?" + code)) {
                    byte[] reply = (Wire.MAGIC + "!" + code + ":" + server.getLocalPort()).getBytes(StandardCharsets.UTF_8);
                    discovery.send(new DatagramPacket(reply, reply.length, p.getSocketAddress()));
                }
            } catch (IOException e) {
                if (closed) {
                    return;
                }
            }
        }
    }

    private String roster() {
        List<String> parts = new ArrayList<>();
        for (MemberState m : members.values()) {
            parts.add(m.encode());
        }
        return String.join(String.valueOf(MemberState.RECORD), parts);
    }

    private void broadcastRoster() {
        long now = System.currentTimeMillis();
        lastSeen.forEach((id, t) -> {
            if (!id.equals(ownId) && now - t > STALE_MS) {
                members.remove(id);
                lastSeen.remove(id);
            }
        });
        relay("ROSTER", roster(), null);
    }

    private void relay(String type, String payload, Client except) {
        for (Client c : clients) {
            if (c == except) {
                continue;
            }
            try {
                c.wire.send(type, payload);
            } catch (IOException e) {
                clients.remove(c);
                c.wire.close();
            }
        }
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public boolean isHost() {
        return true;
    }

    @Override
    public String address() {
        List<String> ips = Wire.localAddresses();
        String ip = ips.isEmpty() ? "not connected to a network" : ips.get(0);
        return server.getLocalPort() == TCP_PORT ? ip : ip + ":" + server.getLocalPort();
    }

    @Override
    public boolean connected() {
        return !closed;
    }

    @Override
    public boolean closed() {
        return closed;
    }

    @Override
    public void publish(MemberState me) {
        ownId = me.id();
        members.put(me.id(), me);
        lastSeen.put(me.id(), System.currentTimeMillis());
    }

    @Override
    public void announceCatch(String memberId, String memberName, String fishType) {
        relay("CATCH", String.join(String.valueOf(MemberState.FIELD),
                MemberState.clean(memberId), MemberState.clean(memberName), fishType), null);
    }

    @Override
    public List<MemberState> members() {
        return new ArrayList<>(members.values());
    }

    @Override
    public void close() {
        closed = true;
        relay("CLOSED", "The host closed the room", null);
        ticker.shutdownNow();
        for (Client c : clients) {
            c.wire.close();
        }
        try {
            server.close();
        } catch (IOException ignored) {
            // closing
        }
        if (discovery != null) {
            discovery.close();
        }
    }
}
