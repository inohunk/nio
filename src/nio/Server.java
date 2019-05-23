package nio;

import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class Server {

    /*
        VARIABLES
     */
    private static final int DEFAULT_PORT = 2023;

    private static final int SEND_BUFFER_SIZE = 128;
    private static final int RCV_BUFFER_SIZE = 1024;

    private int port = DEFAULT_PORT;

    private ServerSocketChannel channel;
    private Selector selector;

    public Server(){}

    public Server(int port){

    }




}
