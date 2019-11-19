package com.github.jmatss.send.protocol;

import com.github.jmatss.send.Controller;
import com.github.jmatss.send.type.MessageType;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
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

    public byte[] getText() {
        return this.text;
    }

    @Override
    public Iterable<byte[]> iter() {
        return () -> new Iterator<byte[]>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return TextProtocol.this.text.length > this.index * TextProtocol.this.pieceSize;
            }

            @Override
            public byte[] next() {
                int remainingTextSize = TextProtocol.this.text.length - this.index * TextProtocol.this.pieceSize;
                int pieceSize = Math.min(remainingTextSize, TextProtocol.this.pieceSize);

                int start = this.index * TextProtocol.this.pieceSize;
                int end = start + pieceSize;

                byte[] packet = ByteBuffer
                        .allocate(1 + 4 + 4 + pieceSize)
                        .put((byte) TextProtocol.this.messageType.value())
                        .putInt(this.index)
                        .putInt(pieceSize)
                        .put(Arrays.copyOfRange(TextProtocol.this.text, start, end))
                        .array();

                this.index++;
                return packet;
            }
        };
    }
}
