package client;

import network.Common;
import network.Connection;
import util.Hashing;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client extends Common implements Runnable {
    private final static Logger logger = Logger.getLogger(Client.class.getSimpleName());
    private static final int RECEIVING_BUFFER_SIZE = Hashing.getHashSize();
    private static final int SENDING_BUFFER_SIZE = 8192;
    private final ClientReporter reporter;
    private final int sendingIntervalMillis;
    private final Random random;
    private final List<String> hashes;
    private final Selector selector;
    private final Connection connection;
    private Instant lastSendingInstant;

    private Client(String serverHost, int serverPort, int sendingRate) throws IOException {
        logger.setLevel(Level.SEVERE);
        reporter = new ClientReporter();
        sendingIntervalMillis = 1000 / sendingRate;
        random = new Random();
        hashes = new LinkedList<>();
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(serverHost, serverPort));
        selector = Selector.open();
        SelectionKey key = socketChannel.register(selector, SelectionKey.OP_CONNECT);
        connection = new Connection(key,
                ByteBuffer.allocate(RECEIVING_BUFFER_SIZE), ByteBuffer.allocate(SENDING_BUFFER_SIZE));
        lastSendingInstant = Instant.EPOCH;
    }

    public static void main(String[] args) {
        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int sendingRate = Integer.parseInt(args[2]);
        try {
            Client client = new Client(serverHost, serverPort, sendingRate);
            logger.info("Starting a client");
            client.run();
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "", e);
        }
    }

    private void connect(Connection connection) {
        try {
            if (connection.channel.finishConnect()) {
                onFinishedConnect(connection);
            }
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "", e);
            shutdown();
        }
    }

    private void onFinishedConnect(Connection connection) {
        connection.key.interestOps(0);
        logger.info("Connected to the server");
    }

    @Override
    protected void onFinishedSend(Connection connection) {
        try {
            reporter.incrementSentCounter();
            hashes.add(Hashing.getHash(connection.sendingBuffer.array()));
            connection.key.interestOps(SelectionKey.OP_READ);
        }
        catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "", e);
            terminateConnection(connection);
        }
    }

    @Override
    protected void onFinishedReceive(Connection connection) {
        reporter.incrementReceivedCounter();
        String hash = new String(connection.receivingBuffer.array());
        if (!hashes.remove(hash)) {
            logger.log(Level.SEVERE, "Received a nonexistent hash");
        }
        connection.receivingBuffer.clear();
    }

    private void initiateSending() {
        boolean hasOpConnect = (connection.key.interestOps() & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT;
        boolean hasOpWrite = (connection.key.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE;
        if (!hasOpConnect && !hasOpWrite) {
            random.nextBytes(connection.sendingBuffer.array());
            connection.sendingBuffer.rewind();
            connection.key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
        }
    }

    private void shutdown() {
        reporter.shutdown();
        terminateConnection(connection);
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
        while (connection.key.isValid()) {
            try {
                Instant now = Instant.now();
                long timeSinceLastSendMillis = now.toEpochMilli() - lastSendingInstant.toEpochMilli();
                if (timeSinceLastSendMillis >= sendingIntervalMillis) {
                    initiateSending();
                    lastSendingInstant = now;
                    selector.selectNow();
                }
                else {
                    selector.select(sendingIntervalMillis - timeSinceLastSendMillis);
                }
                if (connection.key.isValid() && connection.key.isConnectable()) {
                    connect(connection);
                }
                if (connection.key.isValid() && connection.key.isWritable()) {
                    send(connection);
                }
                if (connection.key.isValid() && connection.key.isReadable()) {
                    receive(connection);
                }
            }
            catch (IOException e) {
                logger.log(Level.SEVERE, "", e);
                shutdown();
            }
        }
        shutdown();
    }
}
