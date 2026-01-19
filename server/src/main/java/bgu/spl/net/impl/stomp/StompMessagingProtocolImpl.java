package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String>{
    
    private int connectionId;
    Connections<String> connections;
    private boolean shouldTerminate = false;

    private AtomicLong messageCounter = new AtomicLong(0); 

	@Override
    public void start(int connectionId, Connections<String> connections){
        this.connectionId = connectionId;
        this.connections = connections;
    }
    
    @Override
    public String process(String message){
        StompFrameParser stompFrame = StompFrameParser.parse(message);

        StompClientCommand clientCommand = StompClientCommand.validatedStompCommand(stompFrame.getCommand());
        if(!clientCommand.validate(stompFrame)){
            processError(stompFrame, "Missing mandatory header","The following header is missing for the given command: " + clientCommand.getMissingHeader(stompFrame));
            return null;
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
                return null;
        }
        return null;
    }

    private void processConnect(StompFrameParser stompFrame){
        String login = stompFrame.getHeaderValue("login");
        String accept_version = stompFrame.getHeaderValue("accept-version");
        String host = stompFrame.getHeaderValue("host");
        String passcode = stompFrame.getHeaderValue("passcode");

        if(accept_version != null && accept_version !="1.2" ){
            processError(stompFrame, "Incorrect STOMP version", "accept-version must be 1.2");
            return;
        }
        try{
            LoginStatus loginStatus = Database.getInstance().login(connectionId, login, passcode);
            switch(loginStatus){
                case LOGGED_IN_SUCCESSFULLY:
                case ADDED_NEW_USER:
                    connections.send(connectionId, buildConnectMessage(stompFrame, accept_version));
                    break;
                case WRONG_PASSWORD:
                    processError(stompFrame, "Login Failed", "Wrong password!");
                    break;
                case ALREADY_LOGGED_IN:
                    processError(stompFrame, "Login Failed", "User already logged in!");
                    break;
                case CLIENT_ALREADY_CONNECTED:
                    processError(stompFrame, "Login Failed", "This user is already connected from a differnt client!");
                    break;
                default:
                    processError(stompFrame, "Login Failed", "Unknown error occoured.");
            }
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
            this.connections.send(destination, buildServerMessage(stompFrame, destination, String.valueOf(this.messageCounter.addAndGet(1)), messageBody));
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
        this.connections.send(this.connectionId, buildDisconnectMessage(stompFrame, receipt));
        this.connections.disconnect(connectionId);
    }

    /**
     * Builds a disconnect message
     * @param receipt
     * @return String representing disconnect message
     */
    private String buildDisconnectMessage(StompFrameParser stompFrame,String receipt){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        addReceiptIfExist(stompFrame, msgHeaders);
        return buildResponseMessage("RECEIPT", msgHeaders, null);
    }

    /**
     * Builds a connect message
     * @param stompFrame
     * @param version
     * @return String representing connect message
     */
    private String buildConnectMessage(StompFrameParser stompFrame,String version){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        msgHeaders.put("version",version);
        addReceiptIfExist(stompFrame, msgHeaders);
        return buildResponseMessage("CONNECTED", msgHeaders, null);
    }

    /**
     * Builds a server message (MESSAGE).
     * @param destination
     * @param message_id
     * @param subscriptionId
     * @return String represeting MESSAGE
     * @implNote subscription ID is added by Connections (addSubIdToMessage)
     */
    private String buildServerMessage(StompFrameParser stompFrame,String destination, String message_id, String msgBody){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        msgHeaders.put("destination",destination);
        msgHeaders.put("message-id",message_id);
        addReceiptIfExist(stompFrame, msgHeaders);

        return buildResponseMessage("MESSAGE", msgHeaders, msgBody);
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
        addReceiptIfExist(stompFrame, errorHeaders);

        this.connections.send(this.connectionId, buildResponseMessage("ERROR", errorHeaders , errorBody));
    }

    /**
     * Check if receipt exists in client frame - if yes, adds it to the given map (which will be sent back to the user)
     * @param stompFrame
     * @param responseHeaders
     */
    private void addReceiptIfExist(StompFrameParser stompFrame, Map<String,String> responseHeaders){
        if(stompFrame != null && stompFrame.hasHeader("receipt")){
            responseHeaders.put("receipt", stompFrame.getHeaderValue("receipt"));
        }
    }

    @Override
    public boolean shouldTerminate(){
        return this.shouldTerminate;
    }


    
}
