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
import java.nio.channels.*;
import java.util.Arrays;
import java.util.Iterator;

public class Server implements Runnable {

    /*
        VARIABLES
     */
    private static final Log log = Log.getInstance();
    private static final int DEFAULT_PORT = 2023;
    private static final String DEFAULT_HOST = "localhost";
    private static final String TEST_HOST = "192.168.43.150";
    private static final int KEY_ACCEPT = SelectionKey.OP_ACCEPT;
    private static final int KEY_READ = SelectionKey.OP_READ;
    private static final int KEY_WRITE = SelectionKey.OP_WRITE;
    private static final int SEND_BUFFER_SIZE = 128;
    private static final int RCV_BUFFER_SIZE = 1024;
    private static final int MAX_MESSAGES = 20;

    private String host = TEST_HOST;
    private int port = DEFAULT_PORT;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private SelectionKey serverKey;
    private InetSocketAddress address;
    private volatile boolean isActive = false;

    public Server() {
    }

    public Server(int port) {
        this.port = port;
    }

    /*
        GETTERS
           &
        SETTERS
     */
    private void setActive(boolean active) {
        isActive = active;
    }

    /*
        INTERFACES
    */
    public void send(String msg) throws InterruptedException {
//        messages.put(msg.getBytes());
//        selector.wakeup();
    }

    public void setAddress(String host, int port) {
        this.host = host;
        this.port = port;
        address = new InetSocketAddress(host, port);
    }

    /*
        FUNCTIONS
    */
    private void init() {
        try {
            address = new InetSocketAddress(host, port);
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(address);
//            serverSocketChannel.socket().setReceiveBufferSize(RCV_BUFFER_SIZE);
//            int ops = serverSocketChannel.validOps();
            serverKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            log.logi("server listening on " + address);
            setActive(true);

        } catch (IOException e) {
            e.printStackTrace();
            setActive(false);
        }
    }

    @Override
    public void run() {
        init();
        try {
            runProcess();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runProcess() throws IOException {

        SelectionKey serverKey = serverSocketChannel.register(selector, KEY_ACCEPT);
        while (isActive) {
            int nConnections = selector.selectNow();
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();

            if (nConnections == 0) {
//                Thread.sleep(3000);
//                    log.logi("server sleep 3secs but no connections");
                continue;
            }
            selectionProcess(it);
        }
        selector.close();
        serverSocketChannel.close();
    }

    private void selectionProcess(Iterator<SelectionKey> iterator) {
        SelectionKey key;

        while (iterator.hasNext()) {

            key = iterator.next();
            iterator.remove();

            if (key.isAcceptable()) accept();
//            if (key.isWritable() && key.isValid()) write(key);
            if (key.isReadable() && key.isValid()) read(key);
        }
    }

    private void accept() {
        try {
            SocketChannel client = serverSocketChannel.accept();
            client.configureBlocking(false);
            log.logi("New client connected: " + client.getRemoteAddress());
            client.register(selector, KEY_READ, new Attachment());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void read(SelectionKey key) {
        Attachment attachment = (Attachment) key.attachment();
        ReadableByteChannel channelIn = (ReadableByteChannel) key.channel();
        ByteBuffer bufferIn = attachment.readBuffer;
        ByteBuffer buffer = ByteBuffer.allocate(32);

        try {
            log.logi("trying to read");
            int bytesRead = channelIn.read(buffer);
            log.logi("after read");
            print(buffer);
            if (bytesRead == -1) {
                log.logi("disconnecting");
                key.cancel();
                return;
            }
            if (bytesRead == 0) {
                return;
            }
            buffer.flip();
            int limit = buffer.limit();

            log.logi("Read |" + limit + "| bytes from " + ((SocketChannel) channelIn).getRemoteAddress());

            ByteBuffer message = ByteBuffer.allocate(limit + 1);
            message.put((byte) '#');
            message.put(buffer);
            message.flip();
            write(message, key);

        } catch (IOException ex) {
            ex.printStackTrace();
            key.cancel();
            //TODO MAKE ANYTHING WHEN DISCONNECTED
            log.logi("client " + ((SocketChannel) channelIn).socket().getRemoteSocketAddress() + " disconnected");
        }
        bufferIn.clear();
    }

    private void write(ByteBuffer buffer, SelectionKey key) {

        WritableByteChannel socket = (WritableByteChannel) key.channel();

        try {
            int bytesWrite = socket.write(buffer);
            System.out.println("write " + bytesWrite);
        } catch (IOException e) {
            log.logi("IOException  - disconnecting" + e.getMessage());
            key.cancel();
            return;
        }
    }

        /*if (buff.remaining() == 0) {
            state.writeQueue.remove();
        }*/


    private void stop() {
        try {
            selector.close();
            serverSocketChannel.close();
            System.out.println("exit");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void print(ByteBuffer buffer) {
        String msg = Arrays.toString(buffer.array());
        System.out.println(msg);
    }
    /*
        INNER CLASSES
     */

    private static class Attachment {
        ByteBuffer readBuffer = ByteBuffer.allocate(RCV_BUFFER_SIZE);
    }
}
