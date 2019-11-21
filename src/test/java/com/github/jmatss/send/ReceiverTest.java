package com.github.jmatss.send;

import com.github.jmatss.send.mock.DummyMulticastSocket;
import com.github.jmatss.send.util.SocketWrapper;
import com.github.jmatss.send.type.MessageType;
import com.github.jmatss.send.util.LockableHashSet;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;

public class ReceiverTest {
    private static ScheduledExecutorService executor;

    @BeforeEach
    public static void setUp() {
        int processors = Runtime.getRuntime().availableProcessors();
        executor = Executors.newScheduledThreadPool(processors);
    }

    @AfterEach
    public static void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void testReceiverSendsRequestPacketCorrectlyToSubscribedTopic() throws IOException {
        MessageType subMessageType = MessageType.TEXT;
        ServerSocket serverSocket = new ServerSocket(0);
        Path path = Paths.get("");
        String host = "127.0.0.1";
        String topic = "test_topic";
        byte[] topicBytes = topic.getBytes(Controller.ENCODING);
        byte[] id = {0, 0, 0, 1};
        LockableHashSet<String> subscribedTopics = new LockableHashSet<>();
        subscribedTopics.add(topic);

        byte[] publish_packet = ByteBuffer
                .allocate(1 + 1 + topicBytes.length + 1 + 4 + 4)
                .put((byte) MessageType.PUBLISH.getValue())
                .put((byte) topicBytes.length)
                .put(topicBytes)
                .put((byte) subMessageType.getValue())
                .putInt(serverSocket.getLocalPort())
                .put(id)
                .array();

        MulticastSocket multicastSocket = new DummyMulticastSocket(host, new byte[][]{publish_packet});
        SocketWrapper socketWrapper = null;
        try {
            Receiver receiver = new Receiver(path, multicastSocket, subscribedTopics);
            executor.submit(receiver::start);

            socketWrapper = new SocketWrapper(serverSocket.accept());

            byte[] receivedPacketData = new byte[1 + 1 + topicBytes.length + 4];
            int n = socketWrapper.getInputStream().read(receivedPacketData);
            if (n == -1)
                fail("Received EOF while reading request packet.");
            else if (n != receivedPacketData.length)
                fail("Received incorrect amount of bytes reading request packet. " +
                        "Expected: " + receivedPacketData.length + ", got: " + n);

            ByteBuffer receivedPacketBuffer = ByteBuffer.allocate(receivedPacketData.length + 11)
                    .put(receivedPacketData);
            receivedPacketBuffer.rewind();

            /*
                Expected values
             */
            byte expectedMessageType = (byte) MessageType.REQUEST.getValue();
            byte expectedTopicLength = (byte) topicBytes.length;
            String expectedTopic = topic;
            int expectedId = ByteBuffer.allocate(4).put(id).getInt(0);

            /*
                Actual values
             */
            byte actualMessageType = receivedPacketBuffer.get();
            byte actualTopicLength = receivedPacketBuffer.get();
            byte[] actualTopicBytes = new byte[actualTopicLength];
            receivedPacketBuffer.get(actualTopicBytes);
            String actualTopic = new String(actualTopicBytes, Controller.ENCODING);
            int actualId = receivedPacketBuffer.getInt();

            /*
                Verify that received Request packet is correct
             */
            assertEquals(expectedMessageType, actualMessageType);
            assertEquals(expectedTopicLength, actualTopicLength);
            assertEquals(expectedTopic, actualTopic);
            assertEquals(expectedId, actualId);

            // Send done to finish test before sending any text.
            socketWrapper.sendDone();
        } finally {
            if (socketWrapper != null)
                socketWrapper.close();
            serverSocket.close();
            multicastSocket.close();
        }
    }
}