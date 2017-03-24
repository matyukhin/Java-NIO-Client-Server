package network;

import util.Hashing;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

public class Server extends Common implements Runnable {
    private final static Logger logger = Logger.getLogger(Server.class.getSimpleName());
    private static final int RECEIVING_BUFFER_SIZE = 8192;
    private static final int SENDING_BUFFER_SIZE = Hashing.getHashSize();
    private static final int RESPONSES_QUEUE_SIZE = 10;
    private final Reporter reporter;
    private final Map<SelectionKey, Connection> connections;
    private final Map<SelectionKey, Queue<String>> responses;
    private final Selector selector;

    private Server(int port) throws IOException {
        logger.setLevel(Level.SEVERE);
        reporter = new Reporter(this);
        connections = new HashMap<>();
        responses = new HashMap<>();
        selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }


    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        try {
            Server server = new Server(port);
            logger.info("Starting the server");
            server.run();
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "", e);
        }
    }

    private void accept(SelectionKey key) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            SelectionKey clientKey = socketChannel.register(selector, SelectionKey.OP_READ);
            Connection connection = new Connection(clientKey,
                    ByteBuffer.allocate(RECEIVING_BUFFER_SIZE), ByteBuffer.allocate(SENDING_BUFFER_SIZE));
            connections.put(clientKey, connection);
            responses.put(clientKey, new ArrayDeque<>(RESPONSES_QUEUE_SIZE));
            logger.info("Accepted a connection");
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "", e);
        }
    }

    @Override
    protected void onFinishedReceive(Connection connection) {
        try {
            String hash = Hashing.getHash(connection.receivingBuffer.array());
            connection.receivingBuffer.clear();
            responses.get(connection.key).add(hash);
            if (responses.get(connection.key).size() == RESPONSES_QUEUE_SIZE) {
                connection.key.interestOps(0);
            }
            initiateSending(connection);
            reporter.incrementMessageCounter();
        }
        catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "", e);
            terminateConnection(connection);
        }
    }

    @Override
    protected void onFinishedSend(Connection connection) {
        connection.key.interestOps(SelectionKey.OP_READ);
        initiateSending(connection);
    }

    private void initiateSending(Connection connection) {
        boolean hasOpConnect = (connection.key.interestOps() & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT;
        if (!responses.get(connection.key).isEmpty() && !hasOpConnect) {
            connection.sendingBuffer.clear();
            connection.sendingBuffer.put(responses.get(connection.key).remove().getBytes());
            connection.sendingBuffer.rewind();
            connection.key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        }
    }

    @Override
    void terminateConnection(Connection connection) {
        super.terminateConnection(connection);
        connections.remove(connection.key);
        responses.remove(connection.key);
    }

    private void shutdown() {
        for (Connection connection : connections.values()) {
            terminateConnection(connection);
        }
        try {
            selector.close();
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "", e);
        }
    }

    @Override
    public void run() {
        new Thread(reporter).start();
        while (selector.isOpen()) {
            try {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (key.isValid() && key.isAcceptable()) {
                        accept(key);
                    }
                    if (key.isValid() && key.isReadable()) {
                        receive(connections.get(key));
                    }
                    if (key.isValid() && key.isWritable()) {
                        send(connections.get(key));
                    }
                }
            }
            catch (IOException e) {
                logger.log(Level.SEVERE, "", e);
                shutdown();
            }
        }
    }

    private static class Reporter implements Runnable {
        private static final int REPORTING_INTERVAL_MILLIS = 5000;
        private final Server server;
        private int messageCount;

        Reporter(Server server) {
            this.server = server;
            messageCount = 0;
        }

        synchronized void incrementMessageCounter() {
            ++messageCount;
        }

        synchronized private void resetMessageCounter() {
            messageCount = 0;
        }

        private void printSummary() {
            LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
            System.out.printf("[%02d:%02d:%02d] Throughput: %.2f messages/s, Number of active clients: %d\n",
                    ldt.getHour(), ldt.getMinute(), ldt.getSecond(),
                    (double) messageCount / (REPORTING_INTERVAL_MILLIS / 1000), server.connections.size());
        }

        @Override
        public void run() {
            System.out.println("Starting reports.");
            while (server.selector.isOpen()) {
                try {
                    sleep(REPORTING_INTERVAL_MILLIS);
                }
                catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                }
                printSummary();
                resetMessageCounter();
            }
            System.out.println("The server has shutdown. Stopping reports.");
        }
    }
}
