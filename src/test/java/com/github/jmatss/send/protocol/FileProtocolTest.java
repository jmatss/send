package com.github.jmatss.send.protocol;

import com.github.jmatss.send.type.HashType;
import com.github.jmatss.send.type.MessageType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class FileProtocolTest {
    @Test
    public void test_OneFile_OnePacket_DefaultFileHash_NoPieceHash() {
        String path = "test_data1.txt";
        String name = path;
        TestFile t = new TestFile(name, path, Protocol.DEFAULT_HASH_TYPE, HashType.NONE);

        byte[] file_info_expected = ByteBuffer
                .allocate(1 + 4 + t.pathBytes.length + 8 + 1 + 20)
                .put((byte) MessageType.FILE_INFO.value())
                .putInt(t.pathBytes.length)
                .put(t.pathBytes)
                .putLong(t.length)
                .put((byte) t.fileHashType.value())
                .put(toDigest("1FE1AE26BF167A668B9EBE0BCC70291146AC7957"))
                .array();

        byte[] file_piece_expected = ByteBuffer
                .allocate(1 + 4 + 4 + t.content.length + 1)
                .put((byte) MessageType.FILE_PIECE.value())
                .putInt(0)
                .putInt(t.content.length)
                .put(t.content)
                .put((byte) t.pieceHashType.value())
                .array();

        TestFile[] ts = {t};
        runTest(ts, file_info_expected, file_piece_expected);
    }

    @Test
    public void test_OneFile_TwoPackets_DefaultFileHash_NoPieceHash() {
        String path = "test_data1.txt";
        String name = path;
        TestFile t = new TestFile(name, path, Protocol.DEFAULT_HASH_TYPE, HashType.NONE);
        int pieceSize = (int) (t.length / 2);

        byte[] file_info_expected = ByteBuffer
                .allocate(1 + 4 + t.pathBytes.length + 8 + 1 + 20)
                .put((byte) MessageType.FILE_INFO.value())
                .putInt(t.pathBytes.length)
                .put(t.pathBytes)
                .putLong(t.length)
                .put((byte) t.fileHashType.value())
                .put(toDigest("1FE1AE26BF167A668B9EBE0BCC70291146AC7957"))
                .array();

        List<byte[]> file_pieces_expected = new ArrayList<>(2);
        for (int i = 0; i < 2; i++) {
            file_pieces_expected.add(ByteBuffer
                    .allocate(1 + 4 + 4 + pieceSize + 1)
                    .put((byte) MessageType.FILE_PIECE.value())
                    .putInt(i)
                    .putInt(pieceSize)
                    .put(Arrays.copyOfRange(t.content, i * pieceSize, (i + 1) * pieceSize))
                    .put((byte) t.pieceHashType.value())
                    .array());
        }

        TestFile[] ts = {t};
        runTest(ts, file_info_expected, file_pieces_expected, pieceSize);
    }

    @Test
    public void testExceptionIfFileDoesntExist() {
        String[] paths = {"this_file_doesnt_exist.abc"};
        String[] names = paths;

        assertThrows(Exception.class, () -> new FileProtocol(names, paths));
    }

    private byte[] toDigest(String s) {
        byte[] res = new byte[s.length() / 2];
        for (int i = 0; i < s.length(); i += 2) {
            res[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return res;
    }

    private void runTest(TestFile[] ts, List<byte[]> file_infos_expected, List<byte[]> file_pieces_expected,
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

        // TODO: do this better
        HashType fileHashType = ts[0].fileHashType;
        HashType pieceHashType = ts[0].pieceHashType;

        try {
            int i = 0, j = 0;
            for (PFile pFile : new FileProtocol(names, paths, fileHashType, pieceHashType, pieceSize).iter()) {
                if (i >= file_infos_expected.size()) break;
                assertArrayEquals(file_infos_expected.get(i), pFile.getFileInfoPacket());
                j = 0;
                for (byte[] packet_got : pFile.packetIterator()) {
                    if (j >= file_pieces_expected.size()) break;
                    assertArrayEquals(file_pieces_expected.get(j), packet_got);
                    j++;
                }
                i++;
            }

            assertEquals(file_infos_expected.size(), i,
                    String.format("Expected %d file(s), got %d files(s).", file_infos_expected.size(), i));
            assertEquals(file_pieces_expected.size(), j,
                    String.format("Expected %d packet(s), got %d packet(s).", file_pieces_expected.size(), j));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runTest(TestFile[] ts, byte[] file_info_expected, List<byte[]> file_pieces_expected, int pieceSize) {
        List<byte[]> file_infos_expected = new ArrayList<>();
        file_infos_expected.add(file_info_expected);

        runTest(ts, file_infos_expected, file_pieces_expected, pieceSize);
    }

    private void runTest(TestFile[] ts, byte[] file_info_expected, List<byte[]> file_pieces_expected) {
        runTest(ts, file_info_expected, file_pieces_expected, Protocol.DEFAULT_PIECE_SIZE);
    }

    private void runTest(TestFile[] ts, byte[] file_info_expected, byte[] file_piece_expected, int pieceSize) {
        List<byte[]> file_infos_expected = new ArrayList<>();
        file_infos_expected.add(file_info_expected);
        List<byte[]> packets_expected = new ArrayList<>();
        packets_expected.add(file_piece_expected);

        runTest(ts, file_infos_expected, packets_expected, pieceSize);
    }

    private void runTest(TestFile[] ts, byte[] file_info_expected, byte[] file_piece_expected) {
        runTest(ts, file_info_expected, file_piece_expected, Protocol.DEFAULT_PIECE_SIZE);
    }

    // Helper class that contains info of a file that is to be used in a test.
    // this.path == path relative to test/java/resources
    private class TestFile {
        HashType fileHashType;
        HashType pieceHashType;
        String name;
        String path;
        byte[] pathBytes;
        byte[] content;
        long length;

        TestFile(String name, String path, HashType fileHashType, HashType pieceHashType) {
            this.name = name;
            this.path = Objects.requireNonNull(getClass().getClassLoader().getResource(path)).getFile();
            this.fileHashType = fileHashType;
            this.pieceHashType = pieceHashType;

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
    }
}
