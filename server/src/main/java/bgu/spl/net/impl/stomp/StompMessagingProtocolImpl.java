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
                processError(stompFrame, "Unknown STOMP command provided", "The command \"" + stompFrame.getCommand() + "\" is unknown. Please provide one of the following CONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT." + "\n");
                break;
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
    }
	
    private void processSubscribe(StompFrameParser stompFrame){
        String destination = stompFrame.getHeaderValue("destination");
        String id = stompFrame.getHeaderValue("id");
    }

    private void processUnsubscribe(StompFrameParser stompFrame){
        String id = stompFrame.getHeaderValue("id");
    }

    private void processDisconnect(StompFrameParser stompFrame){
        String receipt = stompFrame.getHeaderValue("receipt");
    }

    private void processError(StompFrameParser stompFrame, String errorHeader, String errorBody){
        this.shouldTerminate = true;

        Map<String,String> errorHeaders = new HashMap<String,String>();
        errorHeaders.put("message", errorHeader);
        String receipt_id = stompFrame.getHeaderValue("receipt");
        if(receipt_id != null){
            errorHeaders.put("receipt", receipt_id);
        }

        connections.send(connectionId, new StompFrameParser("ERROR", errorHeaders , errorBody).toString());
    }

    @Override
    public boolean shouldTerminate(){
        return this.shouldTerminate;
    }


    
}
