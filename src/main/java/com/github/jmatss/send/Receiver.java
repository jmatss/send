package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectHashTypeException;
import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.FileInfoPacket;
import com.github.jmatss.send.packet.PublishPacket;
import com.github.jmatss.send.protocol.Protocol;
import com.github.jmatss.send.util.SocketWrapper;
import com.github.jmatss.send.type.MessageType;
import com.github.jmatss.send.util.LockableHashSet;
import com.github.jmatss.send.util.ScheduledExecutorServiceSingleton;

import java.io.*;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Receiver {
    public static final int SOCKET_TIMEOUT = 5000; // ms
    public static final int MAX_ID_CACHE_SIZE = 1 << 20;
    private static final Logger LOGGER = Logger.getLogger(Receiver.class.getName());
    private static Receiver instance;

    private final ScheduledExecutorService executor;
    private final MulticastSocket multicastSocket;
    private final LockableHashSet<String> subscribedTopics;
    private final Set<ByteBuffer> idCache;  // Caches downloaded ID's so they dont get downloaded again
    private Path downloadPath;

    private Receiver(Path downloadPath, MulticastSocket multicastSocket, LockableHashSet<String> subscribedTopics) {
        this.downloadPath = downloadPath;
        this.executor = ScheduledExecutorServiceSingleton.getInstance();
        this.multicastSocket = multicastSocket;
        this.subscribedTopics = subscribedTopics;
        this.idCache = Collections.synchronizedSet(new HashSet<>());
    }

    public static Receiver initInstance(Path downloadPath, MulticastSocket socket,
                                        LockableHashSet<String> subscribedTopics) {
        if (Receiver.instance != null)
            throw new ExceptionInInitializerError("Receiver already initialized.");
        Receiver.instance = new Receiver(downloadPath, socket, subscribedTopics);
        return Receiver.instance;
    }

    public static Receiver getInstance() {
        if (Receiver.instance == null)
            throw new NullPointerException("Receiver instance is null.");
        return Receiver.instance;
    }

    public void setPath(Path downloadPath) {
        this.downloadPath = downloadPath;
    }

    public void start() {
        byte[] buffer = new byte[Protocol.MAX_PUBLISH_PACKET_SIZE];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                this.multicastSocket.receive(packet);

                if (packet.getData().length == 0)
                    throw new IOException("Received empty packet");
                else if (packet.getData()[packet.getOffset()] != MessageType.PUBLISH.value())
                    throw new IncorrectMessageTypeException("Received incorrect MessageType. " +
                            "Expected: " + MessageType.PUBLISH.value() +
                            ", got: " + packet.getData()[packet.getOffset()]);
                else if (packet.getLength() < Protocol.MIN_PUBLISH_PACKET_SIZE)
                    throw new IOException("Received to few byte: " + packet.getLength());

                this.executor.submit(() -> receive(packet));
            } catch (IOException | IncorrectMessageTypeException e) {
                LOGGER.log(Level.SEVERE, "Exception when receiving packet in Receiver: " + e.getMessage());
            }
            // TODO: Fix a better way to exit the receiver
            if (this.multicastSocket.isClosed())
                return;
        }

    }

    private void receive(DatagramPacket packet) {
        byte[] content = Arrays.copyOfRange(packet.getData(), packet.getOffset(),
                packet.getOffset() + packet.getLength());

        SocketWrapper socketWrapper = null;
        try {
            PublishPacket pp = new SocketWrapper(new ByteArrayInputStream(content)).receivePublish();

            try (LockableHashSet l = this.subscribedTopics.lock()) {
                if (!this.subscribedTopics.contains(pp.topic) || this.idCache.contains(ByteBuffer.wrap(pp.id)))
                    return;
            }

            socketWrapper = new SocketWrapper(new Socket(packet.getAddress(), pp.port));
            socketWrapper.getSocket().setSoTimeout(SOCKET_TIMEOUT);

            socketWrapper.sendRequest(pp);
            if (pp.subMessageType == MessageType.FILE_PIECE.value())
                receiveFile(socketWrapper);
            else if (pp.subMessageType == MessageType.TEXT.value())
                receiveText(socketWrapper);
            else
                throw new RuntimeException("Incorrect subMessageType received: " + pp.subMessageType);

            // TODO: Better way to to make sure the idCache doesn't overflow (?)
            if (this.idCache.size() > MAX_ID_CACHE_SIZE)
                this.idCache.clear();
            this.idCache.add(ByteBuffer.wrap(pp.id));
        } catch (IOException | RuntimeException | IncorrectHashTypeException
                | IncorrectMessageTypeException | NoSuchAlgorithmException e) {
            // TODO: Implement better exception handling.
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, e.getMessage());

            throw new RuntimeException(e);
        } finally {
            try {
                if (socketWrapper != null)
                    socketWrapper.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage());
            }
        }
    }

    private void receiveFile(SocketWrapper socketWrapper)
            throws IOException, IncorrectHashTypeException, IncorrectMessageTypeException, NoSuchAlgorithmException {
        while (true) {
            if (socketWrapper.isDone())
                break;

            FileInfoPacket fileInfoPacket = socketWrapper.receiveFileInfo();

            // If the file already exists on this local host, don't download it again.
            // TODO: More checking, ex see if hash is the same; if not, download with another name.
            File file = Paths.get(this.downloadPath.toString(), fileInfoPacket.name).toFile();
            if (!file.exists()) {
                if (!file.getParentFile().mkdirs() && !file.getParentFile().exists())
                    throw new IOException("Unable to create folders " + file.getParentFile().toString());

                socketWrapper.sendYes();
                try (OutputStream fileWriter = new FileOutputStream(file)) {
                    int index = 0;
                    while (!socketWrapper.isDone()) {
                        fileWriter.write(socketWrapper.receiveFilePiece(index));
                        index++;
                    }

                    if (file.length() != fileInfoPacket.fileLength) {
                        // TODO: return custom error(?)
                        LOGGER.log(Level.SEVERE, "Unable to download whole file " + fileInfoPacket.name +
                                ". Expected: " + fileInfoPacket.fileLength + " bytes, " +
                                "got: " + file.length() + " bytes");
                    }
                }
            } else {
                socketWrapper.sendNo();
            }
        }
        LOGGER.log(Level.INFO, "Downloaded file(s) successfully.");
    }

    // TODO: Make a local "out" where the received text is to be written.
    private void receiveText(SocketWrapper socketWrapper) throws IOException, IncorrectMessageTypeException {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (!socketWrapper.isDone()) {
            String text = socketWrapper.receiveText(index);
            // FIXME: temp out
            sb.append(text);
            index++;
        }
        if (sb.length() != 0)
            LOGGER.log(Level.INFO, "Received text message:\n" + sb.toString());
        else
            LOGGER.log(Level.INFO, "Received empty text message");
    }

    // FIXME: Ugly hack for testing, find another way to do this.
    public static void clear() {
        Receiver.instance = null;
    }
}
