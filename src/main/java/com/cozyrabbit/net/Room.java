package com.cozyrabbit.net;

import java.util.List;

/**
 * A study room on the local network. One computer hosts it, the others join with the room code.
 * All methods are safe to call from the JavaFX thread; network work happens on background threads.
 */
public interface Room extends AutoCloseable {

    /** Called on a background thread; the app hands the work over to the JavaFX thread. */
    interface Listener {
        /** Someone in the room (not you) caught a fish. */
        void onCatch(String memberId, String memberName, String fishType);

        /** The connection changed, e.g. "Reconnecting…" or "The host closed the room". */
        void onStatus(String message);
    }

    int TCP_PORT = 47321;
    int DISCOVERY_PORT = 47322;

    String code();

    boolean isHost();

    /** Addresses others can type in if finding the room by code does not work. */
    String address();

    boolean connected();

    /** True once the room has ended for you (you left, or the host closed it). */
    boolean closed();

    /** Shares your latest state with the room. */
    void publish(MemberState me);

    /** Tells the others you just caught a fish. */
    void announceCatch(String memberId, String memberName, String fishType);

    /** Everybody in the room, including you. */
    List<MemberState> members();

    @Override
    void close();
}
