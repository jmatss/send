package com.github.jmatss.send;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileProtocolTest {
    @Test
    public void testOneFileOnePacketNoHash() {
        String path = "test_data1.txt";
        String name = path;
        TestFile t = new TestFile(name, path);

        byte[] file_info_expected = ByteBuffer
                .allocate(1 + 4 + t.pathBytes.length + 8 + 1)
                .put((byte) MessageType.FILE_INFO.value())
                .putInt(t.pathBytes.length)
                .put(t.pathBytes)
                .putLong(t.length)
                .put((byte) t.hashType.value())
                .array();

        byte[] packet_expected = ByteBuffer
                .allocate(1 + 4 + 4 + t.content.length + 1)
                .put((byte) MessageType.FILE.value())
                .putInt(0)
                .putInt(t.content.length)
                .put(t.content)
                .put((byte) t.hashType.value())
                .array();

        TestFile[] ts = {t};
        runTest(ts, file_info_expected, packet_expected);
    }

    @Test
    public void testOneFileTwoPacketsNoHash() {
        String path = "test_data1.txt";
        String name = path;
        TestFile t = new TestFile(name, path);
        int pieceSize = (int) (t.length / 2);

        byte[] file_info_expected = ByteBuffer
                .allocate(1 + 4 + t.pathBytes.length + 8 + 1)
                .put((byte) MessageType.FILE_INFO.value())
                .putInt(t.pathBytes.length)
                .put(t.pathBytes)
                .putLong(t.length)
                .put((byte) t.hashType.value())
                .array();

        List<byte[]> packets_expected = new ArrayList<>(2);
        for (int i = 0; i < 2; i++) {
            packets_expected.add(ByteBuffer
                    .allocate(1 + 4 + 4 + pieceSize + 1)
                    .put((byte) MessageType.FILE.value())
                    .putInt(i)
                    .putInt(pieceSize)
                    .put(Arrays.copyOfRange(t.content, i * pieceSize, (i + 1) * pieceSize))
                    .put((byte) t.hashType.value())
                    .array());
        }

        TestFile[] ts = {t};
        runTest(ts, file_info_expected, packets_expected, pieceSize);
    }

    @Test
    public void testExceptionIfFileDoesntExist() {
        String[] paths = {"this_file_doesnt_exist.abc"};
        String[] names = paths;

        assertThrows(Exception.class, () -> new FileProtocol(names, paths));
    }

    private void runTest(TestFile[] ts, List<byte[]> file_infos_expected, List<byte[]> packets_expected,
                         int pieceSize) {
        if (ts.length == 0)
            fail("Incorrect testdata given, can't run test with zero test files");

        String[] names = new String[ts.length];
        String[] paths = new String[ts.length];
        int k = 0;
        for (TestFile t : ts) {
            names[k] = t.name;
            paths[k] = t.path;
        }

        System.out.println("ts.size(): " + ts.length + ", f_info: " + file_infos_expected.size() + ", p: " + packets_expected.size());

        try {
            int i = 0, j = 0;
            for (PFile pFile : new FileProtocol(names, paths).iter()) {
                if (i >= file_infos_expected.size()) break;
                assertArrayEquals(file_infos_expected.get(i), pFile.getFileInfo());
                j = 0;
                for (byte[] packet_got : pFile.packetIterator(pieceSize)) {
                    if (j >= packets_expected.size()) break;
                    assertArrayEquals(packets_expected.get(j), packet_got);
                    j++;
                }
                i++;
            }

            assertEquals(file_infos_expected.size(), i,
                    String.format("Expected %d file(s), got %d files(s).", file_infos_expected.size(), i));
            assertEquals(packets_expected.size(), j,
                    String.format("Expected %d packet(s), got %d packet(s).", packets_expected.size(), j));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runTest(TestFile[] ts, byte[] file_info_expected, List<byte[]> packets_expected, int pieceSize) {
        List<byte[]> file_infos_expected = new ArrayList<>();
        file_infos_expected.add(file_info_expected);

        runTest(ts, file_infos_expected, packets_expected, pieceSize);
    }

    private void runTest(TestFile[] ts, byte[] file_info_expected, List<byte[]> packets_expected) {
        runTest(ts, file_info_expected, packets_expected, Protocol.DEFAULT_PIECE_SIZE);
    }

    private void runTest(TestFile[] ts, byte[] file_info_expected, byte[] packet_expected, int pieceSize) {
        List<byte[]> file_infos_expected = new ArrayList<>();
        file_infos_expected.add(file_info_expected);
        List<byte[]> packets_expected = new ArrayList<>();
        packets_expected.add(packet_expected);

        runTest(ts, file_infos_expected, packets_expected, pieceSize);
    }

    private void runTest(TestFile[] ts, byte[] file_info_expected, byte[] packets_expected) {
        runTest(ts, file_info_expected, packets_expected, Protocol.DEFAULT_PIECE_SIZE);
    }

    // Helper class that contains info of a file that is to be used in a test.
    // this.path == path relative to test/java/resources
    private class TestFile {
        HashType hashType;
        String name;
        String path;
        byte[] pathBytes;
        byte[] content;
        long length;

        TestFile(String name, String path, HashType hashType) {
            this.name = name;
            this.hashType = hashType;
            this.path = getClass().getClassLoader().getResource(path).getFile();

            try {
                this.pathBytes = path.getBytes(Protocol.ENCODING);
                File file = new File(this.path);
                this.length = file.length();
                this.content = new byte[Math.toIntExact(this.length)];
                if (new FileInputStream(file).read(this.content) != this.length) {
                    fail(String.format("Unable to read all data from the file %s", this.path));
                }
            } catch (Exception e) {
                fail(String.format("Unable to load test file %s: %s", this.path, e.getMessage()));
            }

            assertNotEquals(0, this.length, "Read 0 bytes from test file.");
        }

        TestFile(String name, String path) {
            this(name, path, HashType.NONE);
        }
    }
}
