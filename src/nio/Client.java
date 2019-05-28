package nio;

import logging.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Client implements IClient {
    public static void main(String[] args) {
        Client client = new Client();
        client.sendData("hi");
    }

    private static final Log log = Log.getInstance();
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 2023;
    private static final int DEFAULT_TIMEOUT = 3000;
    private static final int SEND_BUFFER_SIZE = 128;
    private static final int RCV_BUFFER_SIZE = 1024;

    private static final int KEY_CONNECT = SelectionKey.OP_CONNECT;
    private static final int KEY_READ = SelectionKey.OP_READ;
    private static final int KEY_WRITE = SelectionKey.OP_WRITE;

    private String host = DEFAULT_HOST;
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

        if (checkHostAvailability(host)) {
            this.host = host;
            this.port = port;
        }
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
    private Boolean checkHostAvailability(String host) {
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

    private boolean init() {
        try {
            serverAddress = new InetSocketAddress(host, port);
            selector = Selector.open();
            channel = SocketChannel.open();
            channel.configureBlocking(false);

//            int ops = channel.validOps();
            channelKey = channel.register(selector, SelectionKey.OP_CONNECT);
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
//                log.logi("connected to " + channel.getRemoteAddress());
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
            selector.selectNow();
            if (channelKey.isReadable() && channelKey.isValid()) {
                read(buffer);
            }
            if (channelKey.isWritable() && channelKey.isValid()) {
                write(buffer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void write(ByteBuffer buffer) {
        try {
            int bytesWrite = channel.write(buffer);
            log.logi("bytes write " + bytesWrite);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void read(ByteBuffer buffer) {
        log.logi("read");

    }

    private void doSendData(Object data) {
        if (isConnected) {
            log.logi("trying to send data");
            setCurrentOperation(KEY_WRITE);
            ByteBuffer buffer = null;

            if (data instanceof String) {
                System.out.println("String");
                buffer = ByteBuffer.wrap(((String) data).getBytes());
            } else if (data instanceof Byte[]) {
                System.out.println("byte[]");
                try {
                    buffer = ByteBuffer.wrap((byte[]) data);
                }catch (ClassCastException cce){
                    log.logi(cce.getMessage());
                }
            }


            select(buffer);

        } else {
            connect();
            doSendData(data);
        }


    }

    private void doReadData() {

    }

}
