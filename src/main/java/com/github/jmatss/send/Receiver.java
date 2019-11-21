package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectHashTypeException;
import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.FileInfoPacket;
import com.github.jmatss.send.packet.PublishPacket;
import com.github.jmatss.send.packet.RequestPacket;
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

    private final ScheduledExecutorService executor;
    private final MulticastSocket multicastSocket;
    private final LockableHashSet<String> subscribedTopics;
    private final Set<ByteBuffer> idCache;  // Caches downloaded ID's so they dont get downloaded again
    private Path downloadPath;

    public Receiver(Path downloadPath, MulticastSocket multicastSocket, LockableHashSet<String> subscribedTopics) {
        this.downloadPath = downloadPath;
        this.executor = ScheduledExecutorServiceSingleton.getInstance();
        this.multicastSocket = multicastSocket;
        this.subscribedTopics = subscribedTopics;
        this.idCache = Collections.synchronizedSet(new HashSet<>());
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
                else if (packet.getData()[packet.getOffset()] != MessageType.PUBLISH.getValue())
                    throw new IncorrectMessageTypeException("Received incorrect MessageType. " +
                            "Expected: " + MessageType.PUBLISH.getValue() +
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
        byte[] content = Arrays.copyOfRange(
                packet.getData(),
                packet.getOffset(),
                packet.getOffset() + packet.getLength()
        );

        SocketWrapper socketWrapper = null;
        try {
            PublishPacket pp = new SocketWrapper(new ByteArrayInputStream(content)).receivePublish();

            try (LockableHashSet l = this.subscribedTopics.lock()) {
                if (!this.subscribedTopics.contains(pp.getTopic()) || this.idCache.contains(ByteBuffer.wrap(pp.getId())))
                    return;
            }

            socketWrapper = new SocketWrapper(new Socket(packet.getAddress(), pp.getPort()));
            socketWrapper.getSocket().setSoTimeout(SOCKET_TIMEOUT);

            // Construct the request packet out of data received from the publish packet.
            RequestPacket rp = new RequestPacket(pp.getTopic(), pp.getId());
            socketWrapper.sendPacket(rp);
            if (pp.getSubMessageType() == MessageType.FILE_PIECE)
                receiveFile(socketWrapper);
            else if (pp.getSubMessageType() == MessageType.TEXT)
                receiveText(socketWrapper);
            else
                throw new RuntimeException("Incorrect subMessageType received: " + pp.getSubMessageType());

            // TODO: Better way to to make sure the idCache doesn't overflow (?)
            if (this.idCache.size() > MAX_ID_CACHE_SIZE)
                this.idCache.clear();
            this.idCache.add(ByteBuffer.wrap(pp.getId()));

        } catch (IOException | RuntimeException | IncorrectHashTypeException | IncorrectMessageTypeException e) {
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
            throws IOException, IncorrectHashTypeException, IncorrectMessageTypeException {
        while (true) {
            if (socketWrapper.isDone() || socketWrapper.isClosed())
                break;

            // If the file already exists on this local host, don't download it again.
            // TODO: More checking, ex see if hash is the same; if not, download with another name.
            FileInfoPacket fileInfoPacket = socketWrapper.receiveFileInfo();
            File file = Paths.get(this.downloadPath.toString(), fileInfoPacket.getName()).toFile();
            if (!file.exists()) {
                if (!file.getParentFile().mkdirs() && !file.getParentFile().exists())
                    throw new IOException("Unable to create folders " + file.getParentFile().toString());

                socketWrapper.sendYes();
                try (OutputStream fileWriter = new FileOutputStream(file)) {
                    int index = 0;
                    while (!socketWrapper.isDone()) {
                        fileWriter.write(socketWrapper.receiveFilePiece(index).getBytes());
                        index++;
                    }

                    if (file.length() != fileInfoPacket.getFileLength()) {
                        // TODO: return custom error(?)
                        LOGGER.log(Level.SEVERE, "Unable to download whole file " + fileInfoPacket.getName() +
                                ". Expected: " + fileInfoPacket.getFileLength() + " bytes, " +
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
            LOGGER.log(Level.INFO, "Received empty/no text message");
    }
}
