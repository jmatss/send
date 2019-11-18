package com.github.jmatss.send.mock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DummyMulticastSocket extends MulticastSocket {
    // The "send" and "receive" functions will go through this.packets in order from 0 to packets.length-1.
    private final byte[][] packets;
    private final String host;
    private int index;
    private boolean isClosed;

    // Stores the packets sent via the "send" function and is received via the "receiveTest" function.
    private BlockingQueue<DatagramPacket> sentPackets;

    public DummyMulticastSocket(String host, byte[][] packets) throws IOException {
        this.packets = packets;
        this.host = host;
        this.index = 0;
        this.isClosed = false;
        this.sentPackets = new LinkedBlockingQueue<>();
    }

    public DummyMulticastSocket(String host) throws IOException {
        this(host, new byte[][]{});
    }

    @Override
    public void receive(DatagramPacket p) throws IOException {
        // Block indefinitely if all packets have been read.
        if (this.index == this.packets.length) {
                while(true) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }

        p.setData(this.packets[this.index].clone());
        p.setLength(this.packets[this.index].length);
        p.setAddress(InetAddress.getByName(this.host));

        this.index++;
    }

    @Override
    public void send(DatagramPacket p) {
        this.sentPackets.add(p);
    }

    public DatagramPacket receiveTest() throws InterruptedException {
        return this.sentPackets.take();
    }

    @Override
    public synchronized void close() {
        this.isClosed = true;
    }

    @Override
    public synchronized boolean isClosed() {
        return this.isClosed;
    }
}
