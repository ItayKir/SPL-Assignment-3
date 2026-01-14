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

    /**
     * Parses and return STOMP frame from the user. If invalid, will return UNKNOWN as the command.
     * @param rawMessage
     * @return StompFrameParser
     */
    public static StompFrameParser parse(String rawMessage){
        String[] msgLines = rawMessage.split("\\R");
        
        String rawCommand ="";
        if(msgLines.length > 0)
            rawCommand = msgLines[0];

        Map<String, String> rawHeaders = new HashMap<String,String>();
        int i = 1;
        while( i < msgLines.length && !msgLines[i].isEmpty()){
            String[] parsedHeader = msgLines[i].split(":", 2);
            if(parsedHeader.length < 2){
                rawHeaders.put(parsedHeader[0], null);
            }
            else{
                rawHeaders.put(parsedHeader[0], parsedHeader[1]);
            }
            i++;
        }

        String stompBody ="";
        while(i < msgLines.length){
            stompBody += msgLines[i] + "\n";
        }
        stompBody = stompBody.trim(); // I added back the new lines but for the last row I need to remove what I added (new lines only between exisiting lines)

        return new StompFrameParser(rawCommand, rawHeaders, rawMessage);

    }

    /**
     * Returns the Receipt value iff Receipt header exist in the frame.
     * @param frame
     * @return
     */
    public static String getReceiptHeader(StompFrameParser frame){
        return frame.stompHeaders.get("receipt");
    }

    /**
     * Checks if the frame has header in its headers.
     * @param header
     * @return true iff header is in stompHeaders map as a key.
     */
    public boolean hasHeader(String header){
        return stompHeaders.containsKey(header);
    }



    @Override
    public String toString(){
        String out="";

        out += getCommand();
        for(Map.Entry<String, String> header: stompHeaders.entrySet()){
            out += header.getKey() + ":" + header.getValue() + "\n" ;
        }

        out += "\n"; // new line between headers and body

        if(stompBody != null && !stompBody.isEmpty()){
            out += stompBody;
        }

        out += "\u0000";

        return out;
    }



    // Getters
    public String getCommand() { 
        return this.stompCommand; 
    }
    
    public String getHeaderValue(String key) { 
        return this.stompHeaders.get(key); 
    }
    
    public String getBody() { 
        return this.stompBody; 
    }



}
