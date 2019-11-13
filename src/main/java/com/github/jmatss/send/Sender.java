package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.RequestPacket;
import com.github.jmatss.send.protocol.*;
import com.github.jmatss.send.util.ClosableWrapper;
import com.github.jmatss.send.util.ScheduledExecutorServiceSingleton;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sender {
    private static final Logger LOGGER = Logger.getLogger(Sender.class.getName());
    private static Sender instance;

    private final ScheduledExecutorService executor;
    private final Map<String, ClosableWrapper> publishedTopics;
    private final Lock mutexPublishedTopics;

    private Sender(Map<String, ClosableWrapper> publishedTopics, Lock mutexPublishedTopics) {
        this.executor = ScheduledExecutorServiceSingleton.getInstance();
        this.publishedTopics = publishedTopics;
        this.mutexPublishedTopics = mutexPublishedTopics;
    }

    public static Sender getInstance(Map<String, ClosableWrapper> publishedTopics, Lock mutexPublishedTopics) {
        if (Sender.instance == null)
            Sender.instance = new Sender(publishedTopics, mutexPublishedTopics);
        return Sender.instance;
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
            this.mutexPublishedTopics.lock();
            try {
                if (!this.publishedTopics.containsKey(rp.topic))
                    throw new IllegalArgumentException("Received a request with a non published topic specified : " + rp.topic);
            } finally {
                this.mutexPublishedTopics.unlock();
            }

            if (protocol instanceof FileProtocol)
                sendFile(socketWrapper, (FileProtocol) protocol);
            else if (protocol instanceof TextProtocol)
                sendText(socketWrapper, (TextProtocol) protocol);
            else
                throw new RuntimeException("Incorrect protocol class");

        } catch (IOException | IncorrectMessageTypeException | NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
        } finally {
            try {
                socketWrapper.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to close socket: " + e.getMessage());
            }
        }
    }

    private void sendFile(SocketWrapper socketWrapper, FileProtocol fileProtocol)
            throws IOException, NoSuchAlgorithmException {
        for (PFile pfile : fileProtocol.iter()) {
            socketWrapper.sendFileInfo(pfile.getFileInfoPacket());

            if (socketWrapper.isYes()) {
                for (byte[] filePiece : pfile.packetIterator())
                    socketWrapper.sendFilePiece(filePiece);
                socketWrapper.sendDone();
            }
        }
        socketWrapper.sendDone();
    }

    private void sendText(SocketWrapper socketWrapper, TextProtocol textProtocol) throws IOException {
        for (byte[] textPacket : textProtocol.iter())
            socketWrapper.sendText(textPacket);
        socketWrapper.sendDone();
    }
}
