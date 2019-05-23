package nio;

import logging.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Client implements Runnable {

    private static final Log log = Log.getInstance();
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 2023;
    private static final int DEFAULT_TIMEOUT = 3000;
    private static final int SEND_BUFFER_SIZE = 128;
    private static final int RCV_BUFFER_SIZE = 1024;

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private InetSocketAddress serverAddress;
    private SocketChannel channel;
    private Selector selector;
    private boolean isReady = false;
    private boolean isConnected = false;


    public Client(){}

    public Client(String host,int port){

        if(checkHostAvailability(host)){
            this.host = host;
            this.port = port;
        }
    }
    private Boolean checkHostAvailability(String host){
        boolean available = false;

        try {
            available = InetAddress.getByName(host).isReachable(DEFAULT_TIMEOUT);
            System.out.println("host available");

        } catch (IOException e) {
            System.out.println("host unavailable");
            e.printStackTrace();
        }
        return available;
    }
    private boolean init(){
        try {
            serverAddress = new InetSocketAddress(host,port);
            selector = Selector.open();
            channel = SocketChannel.open();
            channel.configureBlocking(false);

            int ops = channel.validOps();
            channel.register(selector,ops);
            log.logi("configure finished");
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    private void connect(){
        try {
            channel.connect(serverAddress);
            while (channel.isConnectionPending()){
                channel.finishConnect();
            }
            log.logi("connected to "+channel.getRemoteAddress());
            setConnected(true);

        } catch (IOException e) {
            e.printStackTrace();

            setConnected(false);
        }
    }

    @Override
    public void run() {
        isReady = init();
        if (isReady){
            connect();
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }
}
