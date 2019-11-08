package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.protocol.FileProtocol;
import com.github.jmatss.send.protocol.Protocol;
import com.github.jmatss.send.protocol.TextProtocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO: Fix limitation of only one ip/port(?)
public class Controller {
    private static final Logger LOGGER = Logger.getLogger(Controller.class.getName());
    private MulticastSocket socket;
    private InetAddress ip;
    private int port;

    // PUBLISHING
    private Map<String, ScheduledFuture> publishedTasks;
    private Lock mutexPublishedTasks;
    private ScheduledExecutorService publishExecutor;

    // SUBSCRIBING
    private Set<String> subscribedTopics;
    private Lock mutexSubscribedTopics;

    Controller(String ip, int port) throws IOException {
        init(ip, port);
    }

    Controller(int port, boolean ipv6) throws IOException {
        try {
            init(ipv6 ? Protocol.DEFAULT_MULTICAST_IPV6 : Protocol.DEFAULT_MULTICAST_IPV4, port);
        } catch (UnknownHostException e) {
            // Should never happen since the default group is a hardcoded correct address.
            throw new RuntimeException(e);
        }
    }

    // Defaults to ipv4
    Controller(int port) throws IOException {
        this(port, false);
    }

    // Defaults to ipv4
    Controller() throws IOException {
        this(Protocol.DEFAULT_PORT, false);
    }

    private void init(String ip, int port)
            throws IOException {
        if (port > (1 << 16) - 1 || port < 0)
            throw new IllegalArgumentException("Incorrect port number: " + port);
        else if (!InetAddress.getByName(ip).isMulticastAddress())
            throw new IllegalArgumentException("Specified ip isn't a multicast address: " + ip);

        int processors = Runtime.getRuntime().availableProcessors();

        this.publishExecutor = Executors.newScheduledThreadPool(processors);
        this.publishedTasks = new HashMap<>();
        this.mutexPublishedTasks = new ReentrantLock(true);

        this.subscribedTopics = new TreeSet<>();
        this.mutexSubscribedTopics = new ReentrantLock(true);

        this.ip = InetAddress.getByName(ip);
        this.port = port;
        this.socket = new MulticastSocket(this.port);
        this.socket.joinGroup(this.ip);
    }

    /**
     * Adds and sends publishing messages via the executor.
     *
     * @param protocol is the protocol message to be sent.
     * @param topic    that the sender publishes on and the subscribers can listen on.
     * @param timeout  in seconds for how long the message should be published.
     * @param interval in seconds between every publishing packet sent.
     * @return the topic that can be used to access the created ScheduledFuture if one want's to cancel the
     * publishing before the timeout.
     * @throws IncorrectMessageTypeException thrown if a protocol containing a disallowed MessageType is given.
     */
    public String publish(Protocol protocol, String topic, long timeout, long interval) throws IncorrectMessageTypeException {
        verifyProtocol(protocol);
        if (timeout < 0)
            throw new IllegalArgumentException("Timeout set to less than zero.");
        else if (interval <= 0)
            throw new IllegalArgumentException("Interval set to zero or less.");

        this.mutexPublishedTasks.lock();
        try {
            if (this.publishedTasks.containsKey(topic))
                throw new IllegalArgumentException("Already publishing on this topic.");

            byte[] packet = protocol.getPublishPacket(topic);
            DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, this.ip, this.port);

            ScheduledFuture<?> task = this.publishExecutor.scheduleAtFixedRate(
                    () -> {
                        try {
                            this.socket.send(datagramPacket);
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Unable to send publish packet.");
                        }
                    },
                    0,
                    interval,
                    TimeUnit.SECONDS
            );

            this.publishedTasks.put(topic, task);
        } finally {
            this.mutexPublishedTasks.unlock();
        }

        // Automatically cancel and remove task from this.publishedTasks map after timeout.
        // (but only if timeout is set and no one else has already done it).
        if (timeout != 0) {
            this.publishExecutor.schedule(
                    () -> {
                        cancelPublish(topic);
                    },
                    timeout,
                    TimeUnit.SECONDS
            );
        }

        return topic;
    }

    // FIXME: If someone does a manual cancel, and then re-published on the same topic,
    //  the "timeout-cancel" can cancel the newly published topic.
    private void cancelPublish(String topic) {
        this.mutexPublishedTasks.lock();
        try {
            if (this.publishedTasks.containsKey(topic)) {
                this.publishedTasks.get(topic).cancel(true);
                this.publishedTasks.remove(topic);
            }
        } finally {
            this.mutexPublishedTasks.unlock();
        }
    }

    public String subscribe(String topic) {
        this.mutexSubscribedTopics.lock();
        try {
            if (this.subscribedTopics.contains(topic))
                throw new IllegalArgumentException("Already subscribed to this topic.");
            this.subscribedTopics.add(topic);
        } finally {
            this.mutexSubscribedTopics.unlock();
        }

        return topic;
    }

    private void cancelSubscribe(String topic) {
        this.mutexSubscribedTopics.lock();
        try {
            if (this.subscribedTopics.contains(topic)) {
                this.subscribedTopics.remove(topic);
            }
        } finally {
            this.mutexSubscribedTopics.unlock();
        }
    }

    private Protocol verifyProtocol(Protocol protocol) throws IncorrectMessageTypeException {
        MessageType messageType = protocol.getMessageType();
        switch (messageType) {
            case FILE:
                if (!(protocol instanceof FileProtocol))
                    throw new IncorrectMessageTypeException(errString(messageType, "FileProtocol"));
                break;
            case TEXT:
                if (!(protocol instanceof TextProtocol))
                    throw new IncorrectMessageTypeException(errString(messageType, "TextProtocol"));
                break;
            default:
                LOGGER.log(Level.SEVERE,
                        "Incorrect MessageType received in Sender: " + messageType.toString());
                throw new IncorrectMessageTypeException("Received a disallowed MessageType in Sender");
        }

        return protocol;
    }

    private String errString(MessageType messageType, String protocolClass) {
        return "Received MessageType \"" + messageType.toString() + "\" " +
                "from getMessageType() by a non " + protocolClass + " class.";
    }
}
