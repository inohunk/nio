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
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


public class Server implements Runnable {

    /*
        VARIABLES
     */
    private static final Log log = Log.getInstance();
    private static final int DEFAULT_PORT = 2023;
    private static final String DEFAULT_HOST = "localhost";
    private static final String TEST_HOST = "192.168.43.150";

    private static final int SEND_BUFFER_SIZE = 128;
    private static final int RCV_BUFFER_SIZE = 1024;

    private String host = TEST_HOST;
    private int port = DEFAULT_PORT;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private SelectionKey serverKey;
    private InetSocketAddress address;


    private final BlockingQueue<byte[]> messages = new ArrayBlockingQueue<>(SEND_BUFFER_SIZE);

    private volatile boolean isActive = false;

    public Server(){}

    public Server(int port){
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
        INTERFACE

     */

    public void send(String msg) throws InterruptedException {
        messages.put(msg.getBytes());
        selector.wakeup();

    }
    public void setAddress(String host, int port){
        this.host = host;
        this.port = port;
        address = new InetSocketAddress(host,port);
    }

    /*
        METHODS
     */

    private void init(){
        try {

            address = new InetSocketAddress(host, port);
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(address);
//            serverSocketChannel.socket().setReceiveBufferSize(RCV_BUFFER_SIZE);
//            int ops = serverSocketChannel.validOps();
            serverKey = serverSocketChannel.register(selector,SelectionKey.OP_ACCEPT);
            log.logi("server listening on "+ address);
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

        SelectionKey serverKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (isActive) {
            int nConnections = selector.selectNow();
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();

            if(nConnections == 0){
//                Thread.sleep(3000);
//                    log.logi("server sleep 3secs but no connections");
                continue;
            }
            selectionProcess(it);
        }
        selector.close();
        serverSocketChannel.close();
    }

    private void selectionProcess(Iterator<SelectionKey> iterator){
        SelectionKey key;

        while (iterator.hasNext()){

            key = iterator.next();
            iterator.remove();

            if (key.isAcceptable()) accept();
            if (key.isWritable() && key.isValid()) write(key);
            if (key.isReadable() && key.isValid()) read(key);
        }
        while (messages.peek()!=null){
            for (SelectionKey selectionKey : selector.keys()){
                if(selectionKey!=serverKey){
                    Attachment attachment = (Attachment) selectionKey.attachment();
                    attachment.writeQueue.add(ByteBuffer.wrap(Objects.requireNonNull(messages.poll())));
                    selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);

                }
            }
        }
    }

    private void accept() {
        try {
            SocketChannel client = serverSocketChannel.accept();
            client.configureBlocking(false);
            log.logi("New client connected: "+client.getRemoteAddress());
            client.register(selector,SelectionKey.OP_READ,new Attachment());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void read(SelectionKey key) {
        Attachment attachment = (Attachment) key.attachment();
        ReadableByteChannel channelIn = (ReadableByteChannel) key.channel();
        ByteBuffer bufferIn = attachment.readBuffer;

        try{
            int bytesRead = channelIn.read(bufferIn);

            if (bytesRead==-1){
                log.logi("disconnecting");
                key.cancel();
                return;
            }
            if (bytesRead==0){
                return;
            }
            bufferIn.flip();
            int limit = bufferIn.limit();

            log.logi("Scanning |"+limit+"| bytes from "+((SocketChannel)channelIn).getRemoteAddress());

            ByteBuffer command = ByteBuffer.allocate(limit);
            int endOfLastCommand = 0;
            for (int i = 0; i < limit; i++) {
                byte b = bufferIn.get();
                if (b == '\n') {
                    command.flip();
                    log.logi("Complete message from client, ["+new String(command.array(), Charset.defaultCharset())+"]");
                    command.clear();
                    endOfLastCommand = i;
                } else {
                    command.put(b);
                }
            }
            bufferIn.clear();
            if (endOfLastCommand > 0 && endOfLastCommand != (limit - 1)) {
                bufferIn.position(endOfLastCommand + 1);
                bufferIn.compact();
                bufferIn.position(limit - endOfLastCommand);
            }

        } catch (IOException e) {
            key.cancel();
            //TODO MAKE ANYTHING WHEN DISCONNECTED
            log.logi("client "+((SocketChannel)channelIn).socket().getRemoteSocketAddress()+" disconnected");
//            e.printStackTrace();
        }

    }

    private void write(SelectionKey key) {

        Attachment state = (Attachment) key.attachment();
        ByteBuffer buff = state.writeQueue.peek();

        if (buff==null){
            key.interestOps(key.interestOps()&~SelectionKey.OP_WRITE);
            return;
        }
        if (buff.hasRemaining()) {
            try {
                WritableByteChannel socket = (WritableByteChannel) key.channel();
                int bytesWrite = socket.write(buff);
                System.out.println("write "+bytesWrite);
            } catch (IOException e) {
                log.logi("IOException [{}] - disconnecting"+ e.getMessage());
                key.cancel();
                return;
            }
        }
        if (buff.remaining() == 0) {
            state.writeQueue.remove();
        }
    }

    private void stop(){
        try {
            selector.close();
            serverSocketChannel.close();
            System.out.println("exit");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
        INNER CLASSES
     */

    private static class Attachment {
        final Queue<ByteBuffer> writeQueue = new LinkedList<>();
        ByteBuffer readBuffer = ByteBuffer.allocate(RCV_BUFFER_SIZE);
    }
}
