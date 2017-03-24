package network;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

class Connection {
    final SelectionKey key;
    final SocketChannel channel;
    final ByteBuffer receivingBuffer;
    final ByteBuffer sendingBuffer;

    Connection(SelectionKey key, ByteBuffer receivingBuffer, ByteBuffer sendingBuffer) {
        this.key = key;
        this.channel = (SocketChannel) key.channel();
        this.receivingBuffer = receivingBuffer;
        this.sendingBuffer = sendingBuffer;
    }
}
