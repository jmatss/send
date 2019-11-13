package com.github.jmatss.send.type;

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
    // Used to indicate that the subscriber wants the file advertised in the "FILE_INFO" packet.
    YES(5),
    // Used to indicate that the subscriber doesn't want the file advertised in the "FILE_INFO" packet.
    NO(6),
    // Sent to subscriber when publisher have no more data to send.
    DONE(7);
    private final int i;

    MessageType(int i) {
        this.i = i;
    }

    public int value() {
        return this.i;
    }
}
