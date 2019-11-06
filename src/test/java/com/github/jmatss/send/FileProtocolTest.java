package com.github.jmatss.send;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class FileProtocolTest {
    @Test
    public void testOneFileOnePacketNoHash() {
        try {
            String path = "test_data1.txt";
            String fullPath = getClass().getClassLoader().getResource(path).getFile();
            byte[] content = null;
            HashType hashType = HashType.NONE;

            try {
                File file = new File(fullPath);
                content = new byte[Math.toIntExact(file.length())];
                if (new FileInputStream(file).read(content) != file.length()) {
                    fail(String.format("Unable to read all data from the file %s", fullPath));
                }
            } catch (Exception e) {
                fail(String.format("Unable to load test file %s: %s", fullPath, e.getMessage()));
            }

            byte[] packet_expected = ByteBuffer
                    .allocate(1 + 4 + 4 + content.length + 1)
                    .put((byte) MessageType.FILE.value())
                    .putInt(0)
                    .putInt(content.length)
                    .put(content)
                    .put((byte) hashType.value())
                    .array();

            int i = 0;
            int j = 0;
            int expected_files = 1;
            int expected_packets = 1;
            for (PFile pFile : new FileProtocol(fullPath).iter()) {
                if (i > expected_files) break;
                j = 0;
                for (byte[] packet_got : pFile.packetIterator(Protocol.DEFAULT_PIECE_SIZE)) {
                    if (j > expected_packets) break;
                    assertArrayEquals(packet_got, packet_expected);
                    j++;
                }
                i++;
            }

            assertEquals(i, expected_files, String.format("Expected %d file, got %d files(s).", expected_files, i));
            assertEquals(j, expected_packets,
                    String.format("Expected %d packet, got %d packet(s).", expected_packets, j));

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testOneFileTwoPacketsNoHash() {
        try {
            String path = "test_data1.txt";
            String fullPath = getClass().getClassLoader().getResource(path).getFile();
            byte[] content = null;
            HashType hashType = HashType.NONE;

            try {
                File file = new File(fullPath);
                content = new byte[Math.toIntExact(file.length())];
                if (new FileInputStream(file).read(content) != file.length()) {
                    fail(String.format("Unable to read all data from the file %s", fullPath));
                }
            } catch (Exception e) {
                fail(String.format("Unable to load test file %s: %s", fullPath, e.getMessage()));
            }

            int pieceSize = content.length / 2;
            byte[][] packets_expected = new byte[2][];
            for (int i = 0; i < packets_expected.length; i++) {
                packets_expected[i] = ByteBuffer
                        .allocate(1 + 4 + 4 + pieceSize + 1)
                        .put((byte) MessageType.FILE.value())
                        .putInt(i)
                        .putInt(pieceSize)
                        .put(Arrays.copyOfRange(content, i * pieceSize, (i+1) * pieceSize))
                        .put((byte) hashType.value())
                        .array();
            }

            int i = 0;
            int j = 0;
            int expected_files = 1;
            int expected_packets = 2;
            for (PFile pFile : new FileProtocol(fullPath).iter()) {
                if (i > expected_files) break;
                j = 0;
                for (byte[] packet_got : pFile.packetIterator(pieceSize)) {
                    if (j > expected_packets) break;
                    assertArrayEquals(packet_got, packets_expected[j]);
                    j++;
                }
                i++;
            }

            assertEquals(i, expected_files, String.format("Expected %d file, got %d files(s).", expected_files, i));
            assertEquals(j, expected_packets,
                    String.format("Expected %d packets, got %d packet(s).", expected_packets, j));

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
