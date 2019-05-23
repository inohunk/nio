package nio;

import logging.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

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

    private boolean init(){
        try {
            socketAddress = new InetSocketAddress(host,port);
            selector = Selector.open();
            socketChannel = ServerSocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.bind(socketAddress);

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
                if(nConnections == 0){
                    Thread.sleep(3000);
                    log.logi("server sleep 3secs but no connections");
                    continue;
                }

            }
        } catch(IOException | InterruptedException e){
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        isActive = init();
        runProcess();
    }

}
