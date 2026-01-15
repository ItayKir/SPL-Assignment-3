package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this

        if(args.length < 2){
            System.out.println("Need two inputs: port, and either tpc or reactor");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[0];
        if(serverType.equals("reactor")){
            Server.reactor(10, port, () -> new StompMessagingProtocolImpl(), () -> new StompMessageEncoderDecoder());
        }
    }
}
