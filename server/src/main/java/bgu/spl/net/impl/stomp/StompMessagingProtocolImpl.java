package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String>{
    
    private int connectionId;
    Connections<String> connections;
    private boolean shouldTerminate = false;

	@Override
    public void start(int connectionId, Connections<String> connections){
        this.connectionId = connectionId;
        this.connections = connections;
    }
    
    @Override
    public void process(String message){
        StompFrameParser stompFrame = StompFrameParser.parse(message);

        StompClientCommand clientCommand = StompClientCommand.validatedStompCommand(stompFrame.getCommand());
        if(!clientCommand.validate(stompFrame)){
            processError(stompFrame, "Missing mandatory header","The following header is missing for the given command: " + clientCommand.getMissingHeader(stompFrame));
        }
        switch (clientCommand){
            case CONNECT:
                processConnect(stompFrame);
                break;
            case SEND:
                processSend(stompFrame);
                break;
            case SUBSCRIBE:
                processSubscribe(stompFrame);
                break;
            case UNSUBSCRIBE:
                processUnsubscribe(stompFrame);
                break;
            case DISCONNECT:
                processDisconnect(stompFrame);
                break;
            case UNKNOWN:
                processError(stompFrame, "Unknown STOMP command provided", "The command \"" + stompFrame.getCommand() + "\" is unknown. Please provide one of the following CONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT.");
                return;
        }
    }

    private void processConnect(StompFrameParser stompFrame){
        String login = stompFrame.getHeaderValue("login");
        String accept_version = stompFrame.getHeaderValue("accept-version");
        String host = stompFrame.getHeaderValue("host");
        String passcode = stompFrame.getHeaderValue("passcode");
    }

    private void processSend(StompFrameParser stompFrame){
        String destination = stompFrame.getHeaderValue("destination");
        if(!this.connections.isUserSubscribed(this.connectionId, destination))
            processError(stompFrame, "Not subscibed to topic", "Must be subscribed to the topic in order to send it a message!");
        
        String messageBody = stompFrame.getBody();
        
        if(messageBody== null || messageBody.isEmpty())
            processError(stompFrame, "Empty message", "Can't send an empty message to the topic.");
        
        try{
            this.connections.send(destination, messageBody);
        }
        catch(Exception e){
            processError(stompFrame, "Failed SEND", "Server failed while sending");
        }
    }
	
    private void processSubscribe(StompFrameParser stompFrame){
        String destination = stompFrame.getHeaderValue("destination");
        String id = stompFrame.getHeaderValue("id");
        this.connections.subscribe(destination, this.connectionId, id);
        
    }

    private void processUnsubscribe(StompFrameParser stompFrame){
        String id = stompFrame.getHeaderValue("id");
        this.connections.unsubscribe(id, this.connectionId);
    }

    private void processDisconnect(StompFrameParser stompFrame){
        String receipt = stompFrame.getHeaderValue("receipt");
        this.connections.send(this.connectionId, buildDisconnectMessage(receipt));
        this.connections.disconnect(connectionId);
    }

    private String buildDisconnectMessage(String receipt){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        msgHeaders.put("receipt-id",receipt);
        return new StompFrameParser("RECEIPT", msgHeaders, null).toString();
    }

    /**
     * Logic when an error occours - sets as should terminate true and send message to user with provided error details. Adds receipt if available.
     * @param stompFrame - provided by the user
     * @param errorHeader
     * @param errorBody
     */
    private void processError(StompFrameParser stompFrame, String errorHeader, String errorBody){
        this.shouldTerminate = true;

        Map<String,String> errorHeaders = new HashMap<String,String>();
        errorHeaders.put("message", errorHeader);
        String receipt_id = stompFrame.getHeaderValue("receipt");
        if(receipt_id != null){
            errorHeaders.put("receipt", receipt_id);
        }

        this.connections.send(this.connectionId, new StompFrameParser("ERROR", errorHeaders , errorBody).toString());
    }

    @Override
    public boolean shouldTerminate(){
        return this.shouldTerminate;
    }


    
}
