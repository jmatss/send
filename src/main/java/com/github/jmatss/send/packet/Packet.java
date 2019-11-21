package com.github.jmatss.send.packet;

import com.github.jmatss.send.type.MessageType;

import java.io.UnsupportedEncodingException;

public interface Packet {
    public byte[] getBytes() throws UnsupportedEncodingException;
    public MessageType getMessageType();
}
