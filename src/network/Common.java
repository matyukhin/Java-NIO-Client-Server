package network;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Common {
    private final static Logger logger = Logger.getLogger(Common.class.getSimpleName());

    protected Common() {
        logger.setLevel(Level.SEVERE);
    }

    protected void send(Connection connection) {
        try {
            long sentBytesCount = connection.channel.write(connection.sendingBuffer);
            logger.info("Wrote " + sentBytesCount + " bytes");
            if (!connection.sendingBuffer.hasRemaining()) {
                onFinishedSend(connection);
            }
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "", e);
            terminateConnection(connection);
        }
    }

    protected void receive(Connection connection) {
        try {
            long receivedBytesCount = connection.channel.read(connection.receivingBuffer);
            logger.info("Read " + receivedBytesCount + " bytes");
            if (receivedBytesCount == -1) {
                terminateConnection(connection);
            }
            else if (!connection.receivingBuffer.hasRemaining()) {
                onFinishedReceive(connection);
            }
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "", e);
            terminateConnection(connection);
        }
    }

    protected void terminateConnection(Connection connection) {
        connection.key.cancel();
        try {
            connection.channel.close();
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "", e);
        }
    }

    protected abstract void onFinishedSend(Connection connection);

    protected abstract void onFinishedReceive(Connection connection);
}
