package com.github.jmatss.send.util;

import com.github.jmatss.send.Controller;
import com.github.jmatss.send.packet.*;
import com.github.jmatss.send.protocol.Protocol;
import com.github.jmatss.send.type.HashType;
import com.github.jmatss.send.type.MessageType;
import com.github.jmatss.send.exception.IncorrectHashTypeException;
import com.github.jmatss.send.exception.IncorrectMessageTypeException;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class SocketWrapper {
    private final Socket socket;
    private final PushbackInputStream in;
    private final OutputStream out;

    private SocketWrapper(Socket socket, InputStream in, OutputStream out) {
        this.socket = socket;
        this.in = new PushbackInputStream(in);
        this.out = out;
    }

    public SocketWrapper(Socket socket) throws IOException {
        this(socket, socket.getInputStream(), socket.getOutputStream());
    }

    public SocketWrapper(InputStream in) {
        this(null, in, null);
    }

    public SocketWrapper(OutputStream out) {
        this(null, null, out);
    }

    public void close() throws IOException {
        if (this.in != null) this.in.close();
        if (this.out != null) this.out.close();
        if (this.socket != null) this.socket.close();
    }

    public boolean isClosed() throws IOException {
        nullGuard(this.socket);
        return this.socket.isClosed();
    }

    public Socket getSocket() {
        return this.socket;
    }

    public InputStream getInputStream() {
        return this.in;
    }

    public OutputStream getOutputStream() {
        return this.out;
    }

    private boolean isByte(byte b) throws IOException {
        return readByte() == b;
    }

    private boolean isByteUnreadIfIncorrect(byte b) throws IOException {
        // Read and remove the first byte from the input stream.
        // If the read byte isn't equal "b", put it back into the stream.
        byte rb = readByte();
        if (rb == b) {
            return true;
        } else {
            this.in.unread(rb);
            return false;
        }
    }

    public boolean isDone() throws IOException {
        return isByteUnreadIfIncorrect((byte) MessageType.DONE.getValue());
    }

    // TODO: Some sort of check that it is either a yes or no packet, throw exception otherwise
    public boolean isYes() throws IOException {
        return isByte((byte) MessageType.YES.getValue());
    }

    public boolean isNo() throws IOException {
        return isByte((byte) MessageType.NO.getValue());
    }

    public void sendByte(byte b) throws IOException {
        nullGuard(this.out);
        this.out.write(b);
    }

    public void sendPacket(Packet packet) throws IOException {
        nullGuard(this.out);
        this.out.write(packet.getBytes());
    }

    public void sendDone() throws IOException {
        sendByte((byte) MessageType.DONE.getValue());
    }

    public void sendYes() throws IOException {
        sendByte((byte) MessageType.YES.getValue());
    }

    public void sendNo() throws IOException {
        sendByte((byte) MessageType.NO.getValue());
    }

    public String receiveText(int localIndex)
    throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.TEXT;
        if (!isByte((byte) messageType.getValue()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int remoteIndex = readInt();
        if (localIndex != remoteIndex)
            throw new IOException("Index received from remote packet is different from the local index." +
                    " Local index: " + localIndex + ", remote index: " + remoteIndex);

        int textLength = readInt();
        if (textLength > Protocol.MAX_PIECE_SIZE)
            throw new IOException("textLength > Protocol.MAX_PIECE_SIZE (" +
                    textLength + " > " + Protocol.MAX_PIECE_SIZE);

        return new String(readN(textLength), Controller.ENCODING);
    }

    public RequestPacket receiveRequest() throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.REQUEST;
        if (!isByte((byte) messageType.getValue()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int topicLength = readByte();
        String topic = new String(readN(topicLength), Controller.ENCODING);
        byte[] id = readN(4);

        return new RequestPacket(topic, id);
    }

    /**
     * Received a file info packet.
     *
     * @return a FileInfoPacket containing the data from the received packet.
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulates EOFExceptions thrown if the input stream
     *                                       reaches EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     */
    public FileInfoPacket receiveFileInfo()
    throws IOException, IncorrectMessageTypeException, IncorrectHashTypeException {
        MessageType messageType = MessageType.FILE_INFO;
        if (!isByte((byte) messageType.getValue()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int nameLength = readInt();
        String name = new String(readN(nameLength), Controller.ENCODING);
        long fileLength = readLong();
        HashType hashType = HashType.valueOf(readByte());
        byte[] digest = readN(hashType.getSize());

        return new FileInfoPacket(name, fileLength, hashType, digest);
    }

    public PublishPacket receivePublish()
    throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.PUBLISH;
        if (!isByte((byte) messageType.getValue()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int topicLength = readByte();
        String topic = new String(readN(topicLength), Controller.ENCODING);
        MessageType subMessageType = MessageType.valueOf(readByte());
        int port = readInt();
        byte[] id = readN(4);

        return new PublishPacket(topic, subMessageType, port, id);
    }

    /**
     * Receives a file piece.
     *
     * @param localIndex the current piece index.
     * @return the piece data.
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulatesEOFExceptions thrown if the input stream reaches
     *                                       EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     */
    public FilePiecePacket receiveFilePiece(int localIndex)
    throws IOException, IncorrectMessageTypeException, IncorrectHashTypeException {
        MessageType messageType = MessageType.FILE_PIECE;
        if (!isByte((byte) messageType.getValue()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int remoteIndex = readInt();
        if (localIndex != remoteIndex)
            throw new IOException("Index received from remote packet is different from the local index." +
                    " local index: " + localIndex + ", remote done index: " + remoteIndex);

        int pieceLength = readInt();
        byte[] pieceData = readN(pieceLength);
        HashType hashType = HashType.valueOf(readByte());

        byte[] packetDigest = readN(hashType.getSize());
        byte[] actualDigest = hashType.getMessageDigest().digest(pieceData);
        if (!Arrays.equals(actualDigest, packetDigest))
            throw new IOException("Received packet digest is incorrect. " +
                    "Calculated digest of received piece data: " + Arrays.toString(actualDigest) +
                    ", digest received from remote: " + Arrays.toString(packetDigest));

        return new FilePiecePacket(remoteIndex, pieceData, hashType);
    }

    private byte[] readN(int n) throws IOException {
        nullGuard(this.in);
        byte[] buf = new byte[n];
        int readBytes = this.in.read(buf);
        if (readBytes == -1)
            throw new EOFException("End of file reached while reading bytes from the input stream.");
        else if (n != readBytes)
            throw new IOException("Unable to read all bytes from input stream. " +
                    "Expected: " + n + " bytes, got: " + readBytes + " bytes.");
        return buf;
    }

    private byte readByte() throws IOException {
        nullGuard(this.in);
        byte res = (byte) this.in.read();
        if (res == -1)
            throw new EOFException("End of file reached while reading one byte.");
        return res;
    }

    private int readInt() throws IOException {
        byte[] buf = readN(4);
        return ByteBuffer.allocate(4).put(buf).getInt(0);
    }

    private long readLong() throws IOException {
        byte[] buf = readN(8);
        return ByteBuffer.allocate(8).put(buf).getLong(0);
    }

    private void nullGuard(Object... os) throws IOException {
        for (Object o : os) {
            if (o == null)
                throw new IOException("The given stream is null.");
        }
    }
}
