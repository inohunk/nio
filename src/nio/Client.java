package nio;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 2023;
    private static final int DEFAULT_TIMEOUT = 3000;
    private static final int SEND_BUFFER_SIZE = 128;
    private static final int RCV_BUFFER_SIZE = 1024;

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;

    private SocketChannel channel;
    private Selector selector;

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
}
