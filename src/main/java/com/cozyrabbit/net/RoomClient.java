package com.cozyrabbit.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Joins a study room hosted by a friend on the same network, and reconnects if the Wi-Fi drops. */
public final class RoomClient implements Room {

    private final String code;
    private final InetSocketAddress hostAddress;
    private final Listener listener;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "room-send");
        t.setDaemon(true);
        return t;
    });
    private volatile Wire wire;
    private volatile List<MemberState> roster = List.of();
    private volatile MemberState lastMe;
    private volatile boolean connected;
    private volatile boolean closed;

    private RoomClient(String code, InetSocketAddress hostAddress, Listener listener) {
        this.code = code;
        this.hostAddress = hostAddress;
        this.listener = listener;
    }

    /**
     * Finds and joins a room. {@code codeOrAddress} is either the 4-digit room code
     * or the host's address shown in its journal (for example 192.168.1.20).
     * Blocks for a few seconds at most; call it off the JavaFX thread.
     */
    public static RoomClient join(String codeOrAddress, Listener listener) throws IOException {
        String input = codeOrAddress.trim();
        InetSocketAddress addr;
        String code = "";
        if (input.contains(".") || input.equalsIgnoreCase("localhost")) {
            String host = input;
            int port = TCP_PORT;
            int colon = input.lastIndexOf(':');
            if (colon > 0) {
                host = input.substring(0, colon);
                port = Integer.parseInt(input.substring(colon + 1));
            }
            addr = new InetSocketAddress(host, port);
        } else {
            code = input;
            addr = discover(code);
        }
        RoomClient c = new RoomClient(code, addr, listener);
        try {
            c.connectOnce();
        } catch (IOException e) {
            throw new IOException("Couldn't reach " + addr.getHostString() + " (" + e.getMessage() + "). "
                    + "Check the address, and that the host's room is still open. " + Wire.NETWORK_HINT, e);
        }
        Thread t = new Thread(c::readLoop, "room-read");
        t.setDaemon(true);
        t.start();
        return c;
    }

    private static InetSocketAddress discover(String code) throws IOException {
        byte[] ask = (Wire.MAGIC + "?" + code).getBytes(StandardCharsets.UTF_8);
        String expect = Wire.MAGIC + "!" + code + ":";
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            s.setSoTimeout(400);
            byte[] buf = new byte[256];
            for (int attempt = 0; attempt < 10; attempt++) {
                for (InetAddress b : Wire.broadcastAddresses()) {
                    try {
                        s.send(new DatagramPacket(ask, ask.length, b, DISCOVERY_PORT));
                    } catch (IOException ignored) {
                        // some interfaces refuse broadcasts; try the others
                    }
                }
                long until = System.currentTimeMillis() + 400;
                while (System.currentTimeMillis() < until) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        s.receive(p);
                        String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                        if (msg.startsWith(expect)) {
                            int port = Integer.parseInt(msg.substring(expect.length()));
                            return new InetSocketAddress(p.getAddress(), port);
                        }
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                }
            }
        }
        throw new IOException("Couldn't find room " + code + " on this network. Check that you're on the same Wi-Fi, "
                + "or type the address shown in the host's journal instead. " + Wire.NETWORK_HINT);
    }

    private void connectOnce() throws IOException {
        Socket s = new Socket();
        s.connect(hostAddress, 4000);
        Wire w = new Wire(s);
        w.send("HELLO", Wire.MAGIC + code);
        wire = w;
        connected = true;
        MemberState me = lastMe;
        if (me != null) {
            w.send("STATE", me.encode());
        }
    }

    private void readLoop() {
        while (!closed) {
            Wire w = wire;
            try {
                String[] msg = w == null ? null : w.receive();
                if (msg == null) {
                    throw new IOException("closed");
                }
                switch (msg[0]) {
                    case "ROSTER" -> roster = parseRoster(msg[1]);
                    case "CATCH" -> {
                        String[] f = msg[1].split(String.valueOf(MemberState.FIELD), -1);
                        if (f.length >= 3) {
                            listener.onCatch(f[0], f[1], f[2]);
                        }
                    }
                    case "REJECT" -> {
                        listener.onStatus(msg[1]);
                        close();
                        return;
                    }
                    case "CLOSED" -> {
                        listener.onStatus("The host closed the room.");
                        close();
                        return;
                    }
                    default -> {
                        // ignore unknown messages
                    }
                }
            } catch (IOException | RuntimeException e) {
                if (closed) {
                    return;
                }
                if (connected) {
                    connected = false;
                    listener.onStatus("Lost the room. Reconnecting…");
                }
                if (w != null) {
                    w.close();
                }
                wire = null;
                try {
                    Thread.sleep(3000);
                    connectOnce();
                    listener.onStatus("Back in the room.");
                } catch (IOException | InterruptedException again) {
                    // try again after the next pause
                }
            }
        }
    }

    private static List<MemberState> parseRoster(String payload) {
        List<MemberState> out = new ArrayList<>();
        if (payload.isEmpty()) {
            return out;
        }
        for (String rec : payload.split(String.valueOf(MemberState.RECORD))) {
            try {
                out.add(MemberState.decode(rec));
            } catch (IllegalArgumentException ignored) {
                // skip a broken record
            }
        }
        return out;
    }

    private void sendAsync(String type, String payload) {
        if (closed) {
            return;
        }
        sender.submit(() -> {
            Wire w = wire;
            if (w != null) {
                try {
                    w.send(type, payload);
                } catch (IOException ignored) {
                    // the read loop notices and reconnects
                }
            }
        });
    }

    @Override
    public String code() {
        return code.isEmpty() ? hostAddress.getHostString() : code;
    }

    @Override
    public boolean isHost() {
        return false;
    }

    @Override
    public String address() {
        return hostAddress.getHostString();
    }

    @Override
    public boolean connected() {
        return connected && !closed;
    }

    @Override
    public boolean closed() {
        return closed;
    }

    @Override
    public void publish(MemberState me) {
        lastMe = me;
        sendAsync("STATE", me.encode());
    }

    @Override
    public void announceCatch(String memberId, String memberName, String fishType) {
        sendAsync("CATCH", String.join(String.valueOf(MemberState.FIELD),
                MemberState.clean(memberId), MemberState.clean(memberName), fishType));
    }

    @Override
    public List<MemberState> members() {
        List<MemberState> out = new ArrayList<>(roster);
        MemberState me = lastMe;
        if (me != null) {
            out.removeIf(m -> m.id().equals(me.id()));
            out.add(me);
        }
        return out;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        connected = false;
        Wire w = wire;
        if (w != null) {
            try {
                w.send("BYE", "");
            } catch (IOException ignored) {
                // leaving anyway
            }
            w.close();
        }
        sender.shutdownNow();
    }
}
