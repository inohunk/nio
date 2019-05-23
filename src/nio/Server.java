package nio;

import logging.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;


public class Server implements Runnable {

    /*
        VARIABLES
     */
    private static final Log log = Log.getInstance();
    private static final int DEFAULT_PORT = 2023;
    private static final String DEFAULT_HOST = "localhost";

    private static final int SEND_BUFFER_SIZE = 128;
    private static final int RCV_BUFFER_SIZE = 1024;

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;

    private ServerSocketChannel socketChannel;
    private Selector selector;
    private SocketAddress socketAddress;
    private boolean isActive = false;
    public Server(){}

    public Server(int port){
        this.port = port;
    }

    /*
        METHODS
     */
    private boolean init(){
        try {
            socketAddress = new InetSocketAddress(host,port);
            selector = Selector.open();
            socketChannel = ServerSocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.bind(socketAddress);
            socketChannel.socket().setReceiveBufferSize(RCV_BUFFER_SIZE);
            int ops = socketChannel.validOps();
            socketChannel.register(selector,ops);

            log.logi("server listening on "+socketAddress);
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void runProcess(){
        try {
            while (true) {
                int nConnections = selector.selectNow();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();

                if(nConnections == 0){
                    Thread.sleep(3000);
                    log.logi("server sleep 3secs but no connections");
                    continue;
                }

                selectionProcess(selectionKeys);
            }
        } catch(IOException | InterruptedException e){
            e.printStackTrace();
        }
    }

    private void selectionProcess(Set<SelectionKey> keys){
        Iterator<SelectionKey> iterator = keys.iterator();
        SelectionKey key;

        while (iterator.hasNext()){
            key = iterator.next();
            iterator.remove();

            if (key.isValid()){
                if (key.isAcceptable()) accept(key);
                if (key.isWritable()) write(key);
                if (key.isReadable()) read(key);
            }
            keys.clear();
        }
    }

    private void accept(SelectionKey key) {
        try {
            ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
            SocketChannel client = serverSocket.accept();
            client.configureBlocking(false);
            log.logi("client "+client.getRemoteAddress()+" connected");

            client.register(selector,SelectionKey.OP_WRITE);
            //TODO ADD MESSAGES QUEUE

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void read(SelectionKey key) {
        ReadableByteChannel channelIn = (ReadableByteChannel) key.channel();

        ByteBuffer bufferIn = ByteBuffer.allocate(RCV_BUFFER_SIZE);

        try {
            int bytesRead = channelIn.read(bufferIn);
            if(bytesRead > 0){
                log.logi("read "+bytesRead);
            }else {
                log.logi("no bytes read");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        key.interestOps(SelectionKey.OP_WRITE);

    }

    private void write(SelectionKey key) {
        WritableByteChannel channelIn = (WritableByteChannel) key.channel();

        ByteBuffer bufferOut = ByteBuffer.allocate(RCV_BUFFER_SIZE);

        try {
            int bytesWrite = channelIn.write(bufferOut);
            if(bytesWrite > 0){
                log.logi("write "+bytesWrite);
            }else {
                log.logi("no bytes write");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        key.interestOps(SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        isActive = init();
        runProcess();
    }

}
