package com.github.jmatss.send;

public enum MessageType {
    // Used when multi casting publish packet.
    PUBLISH(0),
    // Sent to publisher when subscriber want to receive data.
    REQUEST(1),
    // Used from publisher to subscriber before sending "FILE_PIECE" data.
    FILE_INFO(2),
    // Used when sending a "FileProtocol".
    FILE_PIECE(3),
    // Used when sending a "TextProtocol".
    TEXT(4),
    // Sent to publisher when subscriber have received specified data
    // or sent to subscriber when publisher have no more data to send.
    DONE(5);
    private final int i;

    MessageType(int i) {
        this.i = i;
    }

    public int value() {
        return this.i;
    }
}
