package com.github.jmatss.send;

import com.github.jmatss.send.mock.DummyMulticastSocket;
import com.github.jmatss.send.protocol.Protocol;
import com.github.jmatss.send.protocol.SocketWrapper;
import com.github.jmatss.send.type.MessageType;
import com.github.jmatss.send.util.LockableHashSet;
import com.github.jmatss.send.util.ScheduledExecutorServiceSingleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class ReceiverTest {

    @Test
    public void testReceiverSendsRequestPacketCorrectlyToSubscribedTopic() throws IOException {
        MessageType subMessageType = MessageType.TEXT;
        ServerSocket serverSocket = new ServerSocket(0);
        Path path = Paths.get("");
        String topic = "test_topic";
        String host = "127.0.0.1";
        byte[] topicBytes = topic.getBytes(Protocol.ENCODING);
        byte[] id = {0, 0, 0, 1};
        Lock mutex = new ReentrantLock();
        LockableHashSet<String> subscribedTopics = new LockableHashSet<>();
        subscribedTopics.add(topic);

        byte[] packet_expected = ByteBuffer
                .allocate(1 + 1 + topicBytes.length + 1 + 4 + 4)
                .put((byte) MessageType.PUBLISH.value())
                .put((byte) topicBytes.length)
                .put(topicBytes)
                .put((byte) subMessageType.value())
                .putInt(serverSocket.getLocalPort())
                .put(id)
                .array();

        MulticastSocket multicastSocket = new DummyMulticastSocket(packet_expected, host, 1);
        SocketWrapper socketWrapper = null;
        ScheduledExecutorService executor = ScheduledExecutorServiceSingleton.getInstance();
        try {
            Receiver receiver = Receiver.initInstance(path, multicastSocket, subscribedTopics);
            executor.submit(receiver::start);

            socketWrapper = new SocketWrapper(serverSocket.accept());

            MessageType expectedMessageType = MessageType.REQUEST;
            int expectedTopicLength = topicBytes.length;
            byte[] expectedTopic = topicBytes;
            byte[] expectedId = id;

            byte[] receivedRequestPacket = new byte[1 + 1 + expectedTopicLength + 4];
            int n = socketWrapper.getInputStream().read(receivedRequestPacket);
            if (n == -1)
                fail("Received EOF while reading request packet.");
            else if (n != receivedRequestPacket.length)
                fail("Received incorrect amount of bytes reading request packet. " +
                        "Expected: " + receivedRequestPacket.length + ", got: " + n);

            /*
                Verify that received Request packet is correct
             */
            assertEquals(expectedMessageType.value(), receivedRequestPacket[0]);
            assertEquals(expectedTopicLength, receivedRequestPacket[1]);
            assertArrayEquals(expectedTopic, Arrays.copyOfRange(receivedRequestPacket, 2, expectedTopicLength + 2));
            assertArrayEquals(expectedId, Arrays.copyOfRange(receivedRequestPacket, expectedTopicLength + 2,
                    expectedTopicLength + 6));

            // Send done message immediately before sending text.
            socketWrapper.sendDone();
        } finally {
            if (socketWrapper != null)
                socketWrapper.close();
            serverSocket.close();
            multicastSocket.close();
            if (!executor.isShutdown())
                executor.shutdownNow();
        }
    }

}