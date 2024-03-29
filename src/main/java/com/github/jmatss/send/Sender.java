package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.FilePiecePacket;
import com.github.jmatss.send.packet.RequestPacket;
import com.github.jmatss.send.packet.TextPacket;
import com.github.jmatss.send.protocol.*;
import com.github.jmatss.send.util.ClosableWrapper;
import com.github.jmatss.send.util.LockableHashMap;
import com.github.jmatss.send.util.ScheduledExecutorServiceSingleton;
import com.github.jmatss.send.util.SocketWrapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sender {
    private static final Logger LOGGER = Logger.getLogger(Sender.class.getName());

    private final ScheduledExecutorService executor;
    private final LockableHashMap<String, ClosableWrapper> publishedTopics;

    public Sender(LockableHashMap<String, ClosableWrapper> publishedTopics) {
        this.executor = ScheduledExecutorServiceSingleton.getInstance();
        this.publishedTopics = publishedTopics;
    }

    // TODO: fix so that the "send" function sends all packets before exiting if the task gets a cancel.
    public void listen(ServerSocket serverSocket, Protocol protocol) throws IOException {
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                SocketWrapper socketWrapper = new SocketWrapper(clientSocket);
                this.executor.submit(() -> send(socketWrapper, protocol));
            }
        } catch (SocketException e) {
            LOGGER.log(Level.INFO, "Listener closed.");
        }
    }

    public void send(SocketWrapper socketWrapper, Protocol protocol) {
        try {
            RequestPacket rp = socketWrapper.receiveRequest();
            try (LockableHashMap l = this.publishedTopics.lock()) {
                if (!this.publishedTopics.containsKey(rp.getTopic()))
                    throw new IllegalArgumentException("Received a request with a non published topic specified: " +
                            rp.getTopic());
            }

            if (protocol instanceof FileProtocol)
                sendFile(socketWrapper, (FileProtocol) protocol);
            else if (protocol instanceof TextProtocol)
                sendText(socketWrapper, (TextProtocol) protocol);
            else
                throw new RuntimeException("Incorrect protocol class");

        } catch (IOException | IncorrectMessageTypeException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
        } finally {
            try {
                socketWrapper.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to close socket: " + e.getMessage());
            }
        }
    }

    private void sendFile(SocketWrapper socketWrapper, FileProtocol fileProtocol) throws IOException {
        for (PFile pfile : fileProtocol.iter()) {
            socketWrapper.sendPacket(pfile.getFileInfoPacket());

            if (socketWrapper.isYes()) {
                for (FilePiecePacket filePiece : pfile.packetIterator())
                    socketWrapper.sendPacket(filePiece);
                socketWrapper.sendDone();
            }
        }
        socketWrapper.sendDone();
    }

    private void sendText(SocketWrapper socketWrapper, TextProtocol textProtocol) throws IOException {
        for (TextPacket textPacket : textProtocol.iter())
            socketWrapper.sendPacket(textPacket);
        socketWrapper.sendDone();
    }
}
