package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl<T> implements StompMessagingProtocol<T>{
    
	@Override
    public void start(int connectionId, Connections<T> connections){

    }
    
    @Override
    public void process(T message){

    }
	
    @Override
    public boolean shouldTerminate(){
        return false;
    }
}
