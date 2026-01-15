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
        try{
            this.connections.send(connectionId, buildConnectMessage(accept_version));
        }
        catch(Exception e){
            processError(stompFrame, "Failed to connect", "The server failed to connect.");
        }
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

    /**
     * Builds a disconnect message
     * @param receipt
     * @return String representing disconnect message
     */
    private String buildDisconnectMessage(String receipt){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        msgHeaders.put("receipt-id",receipt);
        return buildResponseMessage("RECEIPT", msgHeaders, null);
    }

    /**
     * Builds a connect message
     * @param receipt
     * @return String representing disconnect message
     */
    private String buildConnectMessage(String version){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        msgHeaders.put("version",version);
        return buildResponseMessage("CONNECTED", msgHeaders, null);
    }

    /**
     * Builds a general message based on input.
     * @param msgCommand
     * @param msgHeaders
     * @param msgBody
     * @return String representing a general message to send
     */
    private String buildResponseMessage(String msgCommand, Map<String, String> msgHeaders, String msgBody){
        return new StompFrameParser(msgCommand, msgHeaders, null).toString();
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

        this.connections.send(this.connectionId, buildResponseMessage("ERROR", errorHeaders , errorBody));
    }

    @Override
    public boolean shouldTerminate(){
        return this.shouldTerminate;
    }


    
}
