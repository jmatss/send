package com.github.jmatss.send;

import com.github.jmatss.send.mock.DummyMulticastSocket;
import com.github.jmatss.send.protocol.Protocol;
import com.github.jmatss.send.protocol.ProtocolActions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class ReceiverTest {

    @Test
    public void testReceiverSendsRequestPacketCorrectlyToSubscribedTopic() throws IOException {
        MessageType subMessageType = MessageType.TEXT;
        ServerSocket serverSocket = new ServerSocket(0);
        String path = "";
        String topic = "test_topic";
        String host = "127.0.0.1";
        byte[] topicBytes = topic.getBytes(Protocol.ENCODING);
        byte[] id = {0, 0, 0, 1};
        Lock mutex = new ReentrantLock();
        Set<String> subscribedTopics = new TreeSet<>();
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
        Socket clientSocket = null;
        ScheduledExecutorService executor = ScheduledExecutorServiceSingleton.getInstance();
        try {
            Receiver receiver = Receiver.getInstance(path, multicastSocket, subscribedTopics, mutex);
            executor.submit(receiver::start);

            clientSocket = serverSocket.accept();
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            MessageType expectedMessageType = MessageType.REQUEST;
            int expectedTopicLength = topicBytes.length;
            byte[] expectedTopic = topicBytes;
            byte[] expectedId = id;

            byte[] receivedRequestPacket = new byte[1 + 1 + expectedTopicLength + 4];
            int n = in.read(receivedRequestPacket);
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
            ProtocolActions.sendDone(out);
        } finally {
            if (clientSocket != null)
                clientSocket.close();
            serverSocket.close();
            multicastSocket.close();
            if (!executor.isShutdown())
                executor.shutdownNow();
        }
    }

}