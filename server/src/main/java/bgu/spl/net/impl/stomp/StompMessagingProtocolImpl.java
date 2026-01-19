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
    private String username = null;

    private AtomicLong messageCounter = new AtomicLong(0); 

	@Override
    public void start(int connectionId, Connections<String> connections){
        this.connectionId = connectionId;
        this.connections = connections;
    }
    
    @Override
    public String process(String message){
        System.out.println("--------------");
        System.out.println("[DEBUG] Received the following message from user: "+ this.connectionId + "\n" + message);
        try{
            StompFrameParser stompFrame = StompFrameParser.parse(message);
            StompClientCommand clientCommand = StompClientCommand.validatedStompCommand(stompFrame.getCommand());
            if(!clientCommand.validate(stompFrame)){
                this.processError(stompFrame, "Missing mandatory header","The following header is missing for the given command: " + clientCommand.getMissingHeader(stompFrame));
                return null;
            }
            switch (clientCommand){
                case CONNECT:
                    this.processConnect(stompFrame);
                    break;
                case SEND:
                    this.processSend(stompFrame);
                    break;
                case SUBSCRIBE:
                    this.processSubscribe(stompFrame);
                    break;
                case UNSUBSCRIBE:
                    this.processUnsubscribe(stompFrame);
                    break;
                case DISCONNECT:
                    this.processDisconnect(stompFrame);
                    break;
                case UNKNOWN:
                    this.processError(stompFrame, "Unknown STOMP command provided", "The command \"" + stompFrame.getCommand() + "\" is unknown. Please provide one of the following CONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT.");
                    return null;
            }
        } catch(Exception e){
                e.printStackTrace();
                this.processError(null, "Could not process request", "Unkown issue occured: " + e);
        }
        return null;
    }

    /**
     * process for connect
     * @param stompFrame
     */
    private void processConnect(StompFrameParser stompFrame){
        String login = stompFrame.getHeaderValue("login");
        String accept_version = stompFrame.getHeaderValue("accept-version").trim();
        String passcode = stompFrame.getHeaderValue("passcode");

        if(accept_version == null || !accept_version.equals("1.2") ){
            this.processError(stompFrame, "Incorrect STOMP version", "accept-version must be 1.2.");
            return;
        }
        try{
            LoginStatus loginStatus = Database.getInstance().login(this.connectionId, login, passcode);
            switch(loginStatus){
                case LOGGED_IN_SUCCESSFULLY:
                case ADDED_NEW_USER:
                    this.connections.send(this.connectionId, buildConnectMessage(stompFrame, accept_version));
                    this.username = login;
                    break;
                case WRONG_PASSWORD:
                    this.processError(stompFrame, "Login Failed", "Wrong password!");
                    break;
                case ALREADY_LOGGED_IN:
                    this.processError(stompFrame, "Login Failed", "User already logged in!");
                    break;
                case CLIENT_ALREADY_CONNECTED:
                    this.processError(stompFrame, "Login Failed", "This user is already connected from a differnt client!");
                    break;
                default:
                    this.processError(stompFrame, "Login Failed", "Unknown error occoured.");
            }
        }
        catch(Exception e){
            this.processError(stompFrame, "Failed to connect", "The server failed to connect.");
        }
    }

    /**
     * process for send
     * @param stompFrame
     */
    private void processSend(StompFrameParser stompFrame){
        System.out.println("--------------");
        System.out.println("[DEBUG] Inside processSend()");
        System.out.println("--------------");
        String destination = stompFrame.getHeaderValue("destination");
        if(!this.connections.isUserSubscribed(this.connectionId, destination))
            this.processError(stompFrame, "Not subscibed to topic", "Must be subscribed to the topic in order to send it a message!");
        
        String messageBody = stompFrame.getBody();
        
        if(messageBody== null || messageBody.isEmpty())
            this.processError(stompFrame, "Empty message", "Can't send an empty message to the topic.");

        if(stompFrame.hasHeader("file path")){
            Database.getInstance().trackFileUpload(this.username, stompFrame.getHeaderValue("file path") , destination);
        }
        
        try{
            this.connections.send(destination, this.buildServerMessage(stompFrame, destination, String.valueOf(this.messageCounter.addAndGet(1)), messageBody));

            sendReceiptIfRequested(stompFrame);
        }
        catch(Exception e){
            this.processError(stompFrame, "Failed SEND", "Server failed while sending");
        }
    }
	
    /**
     * process for subscribe
     * @param stompFrame
     */
    private void processSubscribe(StompFrameParser stompFrame){
        String destination = stompFrame.getHeaderValue("destination");
        String id = stompFrame.getHeaderValue("id");
        
        this.connections.subscribe(destination, this.connectionId, id);
        
        sendReceiptIfRequested(stompFrame);
    }

    /**
     * If the client sent a receipt, send him a receipt-id frame back.
     * @param stompFrame
     */
    private void sendReceiptIfRequested(StompFrameParser stompFrame){
        if(stompFrame.hasHeader("receipt")) {
            Map<String, String> msgHeaders = new HashMap<>();
            this.addReceiptIfExist(stompFrame, msgHeaders);
            this.connections.send(this.connectionId, this.buildReceiptMessage(stompFrame));
        }              
    }

    /**
     * process for unsubscribe
     * @param stompFrame
     */
    private void processUnsubscribe(StompFrameParser stompFrame){
        String id = stompFrame.getHeaderValue("id");
        this.connections.unsubscribe(id, this.connectionId);

        sendReceiptIfRequested(stompFrame);
    }

    /**
     * Process for disconnect
     * @param stompFrame
     */
    private void processDisconnect(StompFrameParser stompFrame){
        String receipt = stompFrame.getHeaderValue("receipt");
        this.connections.send(this.connectionId, buildDisconnectMessage(stompFrame, receipt));
        close();
    }

    /**
     * Builds a disconnect message
     * @param receipt
     * @return String representing disconnect message
     */
    private String buildDisconnectMessage(StompFrameParser stompFrame,String receipt){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        this.addReceiptIfExist(stompFrame, msgHeaders);
        return this.buildReceiptMessage(stompFrame);
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
        this.addReceiptIfExist(stompFrame, msgHeaders);
        return this.buildResponseMessage("CONNECTED", msgHeaders, null);
    }

    /**
     * Builds a server message (MESSAGE).
     * @param stompFrame
     * @param destination 
     * @param message_id
     * @param msgBody
     * @return String represeting MESSAGE
     * @implNote subscription ID is added by Connections (addSubIdToMessage)
     */
    private String buildServerMessage(StompFrameParser stompFrame,String destination, String message_id, String msgBody){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        msgHeaders.put("destination",destination);
        msgHeaders.put("message-id",message_id);
        this.addReceiptIfExist(stompFrame, msgHeaders);

        return this.buildResponseMessage("MESSAGE", msgHeaders, msgBody);
    }

    /**
     * Builds Receipt message
     * @param stompFrame
     * @return
     */
    private String buildReceiptMessage(StompFrameParser stompFrame){
        Map<String, String> msgHeaders = new HashMap<String,String>();
        addReceiptIfExist(stompFrame, msgHeaders);
        return this.buildResponseMessage("RECEIPT", msgHeaders, null);
    }


    /**
     * Builds a general message based on input.
     * @param msgCommand
     * @param msgHeaders
     * @param msgBody
     * @return String representing a general message to send
     */
    private String buildResponseMessage(String msgCommand, Map<String, String> msgHeaders, String msgBody){
        return new StompFrameParser(msgCommand, msgHeaders, msgBody).toString();
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
        this.addReceiptIfExist(stompFrame, errorHeaders);

        this.connections.send(this.connectionId, buildResponseMessage("ERROR", errorHeaders , errorBody));
        this.close();
    }

    /**
     * Check if receipt exists in client frame - if yes, adds it to the given map (which will be sent back to the user)
     * @param stompFrame
     * @param responseHeaders
     */
    private void addReceiptIfExist(StompFrameParser stompFrame, Map<String,String> responseHeaders){
        if(stompFrame != null && stompFrame.hasHeader("receipt")){
            responseHeaders.put("receipt-id", stompFrame.getHeaderValue("receipt"));
        }
    }

    @Override
    public boolean shouldTerminate(){
        return this.shouldTerminate;
    }

    /**
     * Closing calling disconnect of connections and logging the logout in DB
     */
    public void close() {
        if (connections != null) {
            connections.disconnect(this.connectionId);
        }
        Database.getInstance().logout(this.connectionId);
        System.out.println("[DEBUG] Connection closed for ID " + this.connectionId + ". User logged out.");
    }
    
}
