package com.github.jmatss.send.protocol;

import com.github.jmatss.send.packet.TextPacket;
import com.github.jmatss.send.type.MessageType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class TextProtocolTest {
    @Test
    public void testOneTextPacket() {
        try {
            String text = "testabc123";
            byte[] packet_expected = ByteBuffer
                    .allocate(1 + 4 + 4 + text.length())
                    .put((byte) MessageType.TEXT.getValue())
                    .putInt(0)
                    .putInt(text.length())
                    .put(text.getBytes())
                    .array();

            int i = 0;
            int expected_packets = 1;
            for (TextPacket packet_got : new TextProtocol(text).iter()) {
                if (i > expected_packets) break;
                assertArrayEquals(packet_expected, packet_got.getBytes());
                i++;
            }

            assertEquals(expected_packets, i, String.format("Expected 1 packet, got %d packet(s).", i));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testTwoTextPackets() {
        try {
            String text = "testabc123";
            int pieceSize = text.length() / 2;

            byte[][] packets_expected = new byte[2][];
            for (int i = 0; i < packets_expected.length; i++) {
                packets_expected[i] = ByteBuffer
                        .allocate(1 + 4 + 4 + pieceSize)
                        .put((byte) MessageType.TEXT.getValue())
                        .putInt(i)
                        .putInt(pieceSize)
                        .put(text.substring(i * pieceSize, (i + 1) * pieceSize).getBytes())
                        .array();
            }

            int i = 0;
            int expected_packets = 2;
            for (TextPacket packet_got : new TextProtocol(text, pieceSize).iter()) {
                if (i > expected_packets) break;
                assertArrayEquals(packets_expected[i], packet_got.getBytes());
                i++;
            }
            assertEquals(i, expected_packets, String.format("Expected 1 packet, got %d packet(s).", i));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
