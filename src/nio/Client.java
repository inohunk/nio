/*
 * MIT License
 *
 * Copyright 2019 Nikolay Amelin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package nio;

import logging.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;

public class Client implements IClient {
    public static void main(String[] args) throws InterruptedException {
        Client client = new Client();
        while (true) {
            client.sendData("hi");
            Thread.sleep(1000);
            client.sendData("it's me");
            Thread.sleep(1000);
            client.sendData("how are u?");
            Thread.sleep(3000);
        }

    }

    private static final Log log = Log.getInstance();
    private static final String DEFAULT_HOST = "localhost";
    private static final String TEST_HOST = "192.168.43.150";
    private static final int DEFAULT_PORT = 2023;

    private static final int SEND_BUFFER_SIZE = 128;
    private static final int RCV_BUFFER_SIZE = 1024;

    private static final int KEY_CONNECT = SelectionKey.OP_CONNECT;
    private static final int KEY_READ = SelectionKey.OP_READ;
    private static final int KEY_WRITE = SelectionKey.OP_WRITE;

    private String host = TEST_HOST;
    private int port = DEFAULT_PORT;
    private InetSocketAddress serverAddress;
    private SocketChannel channel;
    private Selector selector;
    private SelectionKey channelKey;
    private boolean isReady = false;
    private boolean isConnected = false;

    public Client() {
    }

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /*
        GETTERS
           &
        SETTERS
    */
    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    /*
        INTERFACES
    */
    public void sendData(String data) {
        doSendData(data);
    }

    public void sendData(byte[] data) {
        doSendData(data);
    }

    /*
        FUNCTIONS
    */

    /**
     * Initializing address, selector and channel for non-blocking io.
     * Operations - CONNECT, READ, WRITE
     *
     * @return true if initialization was successful and false, if not
     */
    private boolean init() {
        try {
            serverAddress = new InetSocketAddress(host, port);
            selector = Selector.open();
            channel = SocketChannel.open();
            channel.configureBlocking(false);

            int ops = KEY_CONNECT | KEY_WRITE | KEY_READ;
            channelKey = channel.register(selector, ops);
            log.logi("configure finished");
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void connect() {
        if (isConnected) {
            log.logi("Client already connected");
        } else {
            try {
                isReady = init();
                if (isReady) {
                    channel.connect(serverAddress);
                    while (channel.isConnectionPending()) {
                        isConnected = channel.finishConnect();
                    }

                    if (isConnected) {
                        System.out.println("connected to " + channel.socket().getRemoteSocketAddress() + ":" + channel.socket().getPort());
                    } else {
                        System.out.println("connection was interrupted");
                    }
                }
                setConnected(true);

            } catch (IOException e) {
                e.printStackTrace();
                setConnected(false);
            }
        }
    }

    private void setCurrentOperation(int op) {
        if (channelKey != null) {
            channelKey.interestOps(op);
        }
    }

    private void select(ByteBuffer buffer) {
        log.logi("trying to selection");
        try {
            int numConnections = selector.selectNow();

            log.logi("connections count: " + numConnections);

            if (numConnections > 0) {
                SelectionKey key;
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    key = keys.next();
                    keys.remove();

                    if (!key.isValid()) {
                        continue;
                    }
                    log.logi("key state: " + key.isReadable() + ", " + key.isWritable());

                    if (channelKey.isReadable() && channelKey.isValid()) {
                        log.logi("before read");
                        read();
                        log.logi("after read");
                    }
                    if (channelKey.isWritable() && channelKey.isValid()) {
                        log.logi("before write");
                        write(buffer);
                        log.logi("before write");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            selector.selectedKeys().clear();
        }
    }

    private void write(ByteBuffer buffer) {
        try {
            log.logi("trying to send data");
            int bytesWrite = channel.write(buffer);
            log.logi("bytes write " + bytesWrite);
            channelKey.interestOps(channelKey.interestOps() & ~KEY_WRITE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void read() {
        try {
            log.logi("trying to read data");
            ByteBuffer buffer = ByteBuffer.allocate(RCV_BUFFER_SIZE);
            int bytesRead = channel.read(buffer);
            String msg = Arrays.toString(buffer.array());
            System.out.println("server echo: " + msg);

            log.logi("bytes read " + bytesRead);
            channelKey.interestOps(channelKey.interestOps() & ~KEY_READ);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void print(ByteBuffer buffer) {
        String msg = Arrays.toString(buffer.array());
        System.out.println(msg);
    }

    private void doSendData(Object data) {
        ByteBuffer buffer = convertDataToByteBuffer(data);

        if (isConnected) {

            setCurrentOperation(KEY_WRITE | KEY_READ);
            select(buffer);

//            setCurrentOperation(KEY_READ);
//            select(buffer);
        } else {
            setCurrentOperation(KEY_CONNECT);
            connect();
            doSendData(data);
        }
    }

    private ByteBuffer convertDataToByteBuffer(Object data) {
        ByteBuffer buffer = null;

        if (data instanceof String) {
            buffer = ByteBuffer.wrap(((String) data).getBytes());
        } else if (data instanceof Byte[]) {
            try {
                buffer = ByteBuffer.wrap((byte[]) data);
            } catch (ClassCastException cce) {
                log.logi(cce.getMessage());
            }
        }
        return buffer;
    }

}
