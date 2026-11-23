package com.cozyrabbit.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Line-based messages over TCP: TYPE, a group separator, then the payload. */
final class Wire implements AutoCloseable {

    static final char TYPE_END = '\u001d';
    static final String MAGIC = "COZYRABBIT1";

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;

    Wire(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(15_000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    synchronized void send(String type, String payload) throws IOException {
        out.write(type);
        out.write(TYPE_END);
        out.write(payload.replace('\n', ' ').replace('\r', ' '));
        out.write('\n');
        out.flush();
    }

    /** @return {type, payload}, or null when the other side closed the connection */
    String[] receive() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        int i = line.indexOf(TYPE_END);
        if (i < 0) {
            return new String[]{line, ""};
        }
        return new String[]{line.substring(0, i), line.substring(i + 1)};
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    /**
     * The IPv4 addresses of this computer that friends could connect to (Wi-Fi, Ethernet, VPN).
     * Home networks use private addresses (192.168.x.x, 10.x.x.x); many universities, such as on
     * eduroam, hand out ordinary public-looking addresses instead, so those count too.
     * Private ones come first because they are the most likely to work.
     */
    static List<String> localAddresses() {
        List<String> privateOnes = new ArrayList<>();
        List<String> others = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()
                            || a.isAnyLocalAddress() || a.isMulticastAddress()) {
                        continue;
                    }
                    (a.isSiteLocalAddress() ? privateOnes : others).add(a.getHostAddress());
                }
            }
        } catch (IOException ignored) {
            // no network
        }
        privateOnes.addAll(others);
        return privateOnes;
    }

    /** Shown when a room can't be reached; school and public networks are the usual reason. */
    static final String NETWORK_HINT = "Many school, university, hotel and café networks (eduroam, for example) "
            + "don't let devices talk to each other. A phone hotspot works, or connect everybody through a VPN "
            + "app such as Tailscale and use the host's VPN address.";

    static List<InetAddress> broadcastAddresses() {
        List<InetAddress> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                ni.getInterfaceAddresses().forEach(ia -> {
                    if (ia.getBroadcast() != null) {
                        out.add(ia.getBroadcast());
                    }
                });
            }
            out.add(InetAddress.getByName("255.255.255.255"));
            out.add(InetAddress.getLoopbackAddress());
        } catch (IOException ignored) {
            // no network
        }
        return out;
    }
}
