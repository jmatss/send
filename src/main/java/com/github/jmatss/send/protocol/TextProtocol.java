package com.github.jmatss.send.protocol;

import com.github.jmatss.send.Controller;
import com.github.jmatss.send.packet.TextPacket;
import com.github.jmatss.send.type.MessageType;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;

public class TextProtocol extends Protocol {
    private final MessageType messageType = MessageType.TEXT;
    private final byte[] text;
    private final int pieceSize;

    public TextProtocol(String text, int pieceSize) throws UnsupportedEncodingException {
        if (pieceSize > Protocol.MAX_PIECE_SIZE)
            throw new IllegalArgumentException(String.format("pieceSize > Protocol.MAX_PIECE_SIZE: (%d > %d)",
                    pieceSize, Protocol.MAX_PIECE_SIZE));

        this.text = text.getBytes(Controller.ENCODING);
        this.pieceSize = pieceSize;
    }

    public TextProtocol(String text) throws UnsupportedEncodingException {
        this(text, Protocol.DEFAULT_PIECE_SIZE);
    }

    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }

    @Override
    public Iterable<TextPacket> iter() {
        return () -> new Iterator<TextPacket>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return TextProtocol.this.text.length > this.index * TextProtocol.this.pieceSize;
            }

            @Override
            public TextPacket next() {
                int remainingTextSize = TextProtocol.this.text.length - this.index * TextProtocol.this.pieceSize;
                int pieceSize = Math.min(remainingTextSize, TextProtocol.this.pieceSize);

                int start = this.index * TextProtocol.this.pieceSize;
                try {
                    return new TextPacket(
                            this.index++,
                            new String(TextProtocol.this.text, start, pieceSize, Controller.ENCODING)
                    );
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        };
    }
}
