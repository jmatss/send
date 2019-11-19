package com.github.jmatss.send;

import com.github.jmatss.send.protocol.Protocol;

import java.io.IOException;
import java.net.MulticastSocket;
import java.util.List;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws IOException {
        // Use first argument as download path if specified.
        String path = "";
        if (args.length == 1)
            path = args[0];

        String ip = Protocol.DEFAULT_MULTICAST_IPV4;
        int port = Protocol.DEFAULT_PORT;
        MulticastSocket socket = new MulticastSocket(port);
        Controller controller = new Controller(path, socket, ip, port);

        try {
            Scanner in = new Scanner(System.in);
            while (true) {
                try {
                    System.out.print(">> ");
                    String input = in.nextLine();
                    if (input.isEmpty()) {
                        usage();
                        continue;
                    }

                    String[] cmd = input.split(" ");
                    switch (cmd[0]) {
                        case "q":
                        case "quit":
                            return;
                        case "l":
                        case "ls":
                        case "list":
                            System.out.println("LIST:");
                            List<String> l = controller.list();
                            for (String s : l)
                                System.out.println(s);
                            break;
                        case "p":
                        case "pub":
                        case "publish":
                            boolean text = true;
                            long timeout = Controller.DEFAULT_PUBLISH_TIMEOUT;
                            long interval = Controller.DEFAULT_PUBLISH_INTERVAL;
                            String textOrPath;
                            String topic;

                            if (cmd.length >= 2) {
                                topic = cmd[1];
                            } else {
                                System.out.print("Topic: ");
                                topic = in.nextLine();
                            }
                            if (topic.isEmpty())
                                throw new IllegalArgumentException("Empty topic not allowed.");

                            System.out.print("Send t[ext]/f[iles] (default: text): ");
                            input = in.nextLine();
                            if (!input.isEmpty() && input.charAt(0) == 'f')
                                text = false;

                            if (text)
                                System.out.print("Text: ");
                            else
                                System.out.print("Path: ");
                            textOrPath = in.nextLine();
                            if (textOrPath.isEmpty())
                                throw new IllegalArgumentException("Empty input not allowed.");

                            System.out.print("Timeout (default: " + timeout + " s, 0 = infinite): ");
                            input = in.nextLine();
                            if (!input.isEmpty())
                                timeout = Long.parseLong(input);

                            System.out.print("Interval (default: " + interval + " s): ");
                            input = in.nextLine();
                            if (!input.isEmpty())
                                interval = Long.parseLong(input);

                            if (text)
                                controller.publishText(topic, textOrPath, timeout, interval);
                            else
                                controller.publishFile(topic, textOrPath, timeout, interval);

                            System.out.println("PUBLISHED to " + topic);
                            break;
                        case "up":
                        case "unpublish":
                            if (cmd.length != 2) {
                                System.out.println("Incorrect input");
                                usage();
                                break;
                            }

                            controller.cancelPublish(cmd[1]);
                            System.out.println("UNPUBLISHED " + cmd[1]);
                            break;
                        case "s":
                        case "sub":
                        case "subscribe":
                            if (cmd.length != 2) {
                                System.out.println("Incorrect input");
                                usage();
                                break;
                            }

                            controller.subscribe(cmd[1]);
                            System.out.println("SUBSCRIBED to " + cmd[1]);
                            break;
                        case "us":
                        case "unsubscribe":
                            if (cmd.length != 2) {
                                System.out.println("Incorrect input");
                                usage();
                                break;
                            }

                            controller.cancelSubscribe(cmd[1]);
                            System.out.println("UNSUBSCRIBED " + cmd[1]);
                            break;
                        case "o":
                        case "path":
                            if (cmd.length != 2) {
                                System.out.println("Incorrect input");
                                usage();
                                break;
                            }

                            controller.setPath(cmd[1]);
                        default:
                            System.out.println("Invalid command.");
                            usage();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } finally {
            controller.shutdown();
        }
    }

    public static void usage() {
        System.out.println("Commands:\n" +
                "\tl/ls/list\n" +
                "\tp/pub/publish [<TOPIC>]\n" +
                "\tup/unpublish <TOPIC>\n" +
                "\ts/sub/subscribe <TOPIC>\n" +
                "\tus/unsubscribe <TOPIC>\n" +
                "\to/path <DOWNLOAD_PATH>\n" +
                "\tq/quit"
        );
    }
}
