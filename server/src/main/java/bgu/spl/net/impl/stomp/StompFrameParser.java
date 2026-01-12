package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;

public class StompFrameParser {
    
    private String stompCommand;
    private Map<String, String> stompHeaders;
    private String stompBody;


    public StompFrameParser(String stompCommand, Map<String, String> stompHeaders, String stompBody){
        this.stompCommand = stompCommand;
        this.stompHeaders = stompHeaders;
        this.stompBody = stompBody;
    }

    public static StompFrameParser pasrse(String rawMessage){
        String[] msgLines = rawMessage.split("\\R");
        String rawCommand = msgLines[0];

        Map<String, String> rawHeaders = new HashMap<String,String>();
        int i = 1;
        while( i < msgLines.length && !msgLines[i].isEmpty()){
            String[] parsedHeader = msgLines[i].split(":");
            rawHeaders.put(parsedHeader[0], parsedHeader[1]);
            i++;
        }
    }
}

// Server Frames:
//ERROR
// CONNECT -> CONNECTED
// MESSAGE
//RECEIPT

// Client frames:
//CONNECT
//SEND
//SUBSCRIBE
//UNSUBSCRIBE
//DISCONNECT