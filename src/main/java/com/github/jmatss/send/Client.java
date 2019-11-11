package com.github.jmatss.send;

import com.github.jmatss.send.protocol.Protocol;

import java.io.IOException;
import java.net.MulticastSocket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws IOException {
        String path = "";
        String ip = Protocol.DEFAULT_MULTICAST_IPV4;
        int port = Protocol.DEFAULT_PORT;
        MulticastSocket socket = new MulticastSocket(port);
        Controller controller = new Controller(path, socket, ip, port);

        try {
            Scanner in = new Scanner(System.in);
            while (true) {
                System.out.print(">> ");
                String input = in.nextLine();
                if (input.isEmpty()) {
                    System.out.println("Empty command.");
                    continue;
                }

                String[] cmd = input.split(" ");

                switch (cmd[0].getBytes(Protocol.ENCODING)[0]) {
                    case 'q':
                        System.out.println("QUIT");
                        return;
                    case 'l':   // list
                        System.out.println("LIST");
                        break;
                    case 'p':   // publish
                        System.out.println(input);
                        break;
                    case 's':   // subscribe
                        if (cmd.length != 2) {
                            System.out.println("Incorrect input");
                            usage();
                            break;
                        }

                        controller.subscribe(cmd[1]);
                        break;
                    default:
                        usage();
                }
            }
        } finally {
            controller.shutdown();
        }
    }

    static void usage() {
        System.out.println(String.format("%-7s %s", "usage", "list"));
        System.out.println(String.format("%-7s %s", "", "publish <TODO>"));
        System.out.println(String.format("%-7s %s", "", "subscribe <topic>"));
    }

}
