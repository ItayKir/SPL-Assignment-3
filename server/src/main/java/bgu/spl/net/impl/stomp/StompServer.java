package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this

        if(args.length != 2){
            System.out.println("Need exactly two inputs: port, and either \"tpc\" or \"reactor\"");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[1];
        if(serverType.equals("reactor")){
            Server.reactor(
                Runtime.getRuntime().availableProcessors(), 
                port, 
                () -> new StompMessagingProtocolImpl(), 
                () -> new StompMessageEncoderDecoder()
            ).serve();
        }
        else if(serverType.equals("tpc")){
            Server.threadPerClient(
                port, 
                () -> new StompMessagingProtocolImpl(), 
                () -> new StompMessageEncoderDecoder()
            ).serve();
        } else{
            System.out.println("Not a supported server type (" + serverType + "). Supported server types are \"tpc\" or \"reactor\".");
        }
        Database.getInstance().printReport();
    }
}
