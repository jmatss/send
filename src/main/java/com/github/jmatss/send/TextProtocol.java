package com.github.jmatss.send;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class TextProtocol extends Protocol {
    private final MessageType messageType = MessageType.Text;
    private final HashType hashType;
    private final String text;
    private final int pieceSize;

    TextProtocol(String text, int pieceSize, HashType hashType) {
        if (pieceSize > Protocol.MAX_PIECE_SIZE)
            throw new IllegalArgumentException(String.format("pieceSize > Protocol.MAX_PIECE_SIZE: (%d > %d)",
                    pieceSize, Protocol.MAX_PIECE_SIZE));

        this.text = text;
        this.pieceSize = pieceSize;
        this.hashType = hashType;
    }

    TextProtocol(String text, int pieceSize) {
        this(text, pieceSize, Protocol.DEFAULT_HASH_TYPE);
    }

    TextProtocol(String text) {
        this(text, Protocol.DEFAULT_PIECE_SIZE, Protocol.DEFAULT_HASH_TYPE);
    }

    @Override
    public HashType getHashType() {
        return this.hashType;
    }

    public String getText() {
        return this.text;
    }

    @Override
    public Iterator<byte[]> iterator() {
        return new Iterator<byte[]>() {
            int index = 0;
            TextProtocol sup = TextProtocol.this;

            @Override
            public boolean hasNext() {
                return sup.text.length() > index * sup.pieceSize;
            }

            @Override
            public byte[] next() {
                int remainingTextSize = sup.text.length() - index * sup.pieceSize;
                int pieceSize = Math.min(remainingTextSize, sup.pieceSize);

                int start = index * sup.pieceSize;
                int end = start + pieceSize;
                index++;

                return ByteBuffer
                        .allocate(1 + 4 + pieceSize)
                        .put((byte) sup.messageType.value())
                        .putInt(pieceSize)
                        .put(sup.text.substring(start, end).getBytes())
                        .array();
            }
        };
    }
}
