package com.github.jmatss.send.protocol;

import com.github.jmatss.send.MessageType;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;

public class TextProtocol extends Protocol {
    private final MessageType messageType = MessageType.TEXT;
    private final byte[] text;
    private final int pieceSize;

    TextProtocol(String text, int pieceSize) throws UnsupportedEncodingException {
        if (pieceSize > Protocol.MAX_PIECE_SIZE)
            throw new IllegalArgumentException(String.format("pieceSize > Protocol.MAX_PIECE_SIZE: (%d > %d)",
                    pieceSize, Protocol.MAX_PIECE_SIZE));

        this.text = text.getBytes(Protocol.ENCODING);
        this.pieceSize = pieceSize;
    }

    TextProtocol(String text) throws UnsupportedEncodingException {
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
            TextProtocol sup = TextProtocol.this;

            @Override
            public boolean hasNext() {
                return sup.text.length > this.index * sup.pieceSize;
            }

            @Override
            public byte[] next() {
                int remainingTextSize = sup.text.length - this.index * sup.pieceSize;
                int pieceSize = Math.min(remainingTextSize, sup.pieceSize);

                int start = this.index * sup.pieceSize;
                int end = start + pieceSize;

                byte[] packet = ByteBuffer
                        .allocate(1 + 4 + 4 + pieceSize)
                        .put((byte) sup.messageType.value())
                        .putInt(this.index)
                        .putInt(pieceSize)
                        .put(Arrays.copyOfRange(sup.text, start, end))
                        .array();

                this.index++;
                return packet;
            }
        };
    }
}
