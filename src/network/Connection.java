package network;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class Connection {
    public final SelectionKey key;
    public final SocketChannel channel;
    public final ByteBuffer receivingBuffer;
    public final ByteBuffer sendingBuffer;

    public Connection(SelectionKey key, ByteBuffer receivingBuffer, ByteBuffer sendingBuffer) {
        this.key = key;
        this.channel = (SocketChannel) key.channel();
        this.receivingBuffer = receivingBuffer;
        this.sendingBuffer = sendingBuffer;
    }
}
