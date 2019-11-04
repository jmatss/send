package com.github.jmatss.send;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class TextProtocolTest {
    @Test
    public void testOnePacket() {
        String text = "testabc123";
        byte[] packet_expected = ByteBuffer
                .allocate(1 + 4 + text.length())
                .put((byte) MessageType.Text.value())
                .putInt(text.length())
                .put(text.getBytes())
                .array();

        int i = 0;
        int expected_packets = 1;
        for (byte[] packet_got : new TextProtocol(text)) {
            if (i > expected_packets) break;
            assertArrayEquals(packet_got, packet_expected);
            i++;
        }
        assertEquals(i, expected_packets, String.format("Expected 1 packet, got %d packet(s).", i));
    }

    @Test
    public void testTwoPackets() {
        String text = "testabc123";
        int pieceSize = text.length() / 2;

        byte[][] packets_expected = new byte[2][];
        for (int i = 0; i < packets_expected.length; i++) {
            packets_expected[i] = ByteBuffer
                    .allocate(1 + 4 + pieceSize)
                    .put((byte) MessageType.Text.value())
                    .putInt(pieceSize)
                    .put(text.substring(i * pieceSize, (i + 1) * pieceSize).getBytes())
                    .array();
        }

        int i = 0;
        int expected_packets = 2;
        for (byte[] packet_got : new TextProtocol(text, pieceSize)) {
            if (i > expected_packets) break;
            assertArrayEquals(packet_got, packets_expected[i]);
            i++;
        }
        assertEquals(i, expected_packets, String.format("Expected 1 packet, got %d packet(s).", i));
    }
}
