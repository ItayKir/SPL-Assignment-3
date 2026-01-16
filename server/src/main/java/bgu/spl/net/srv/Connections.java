package bgu.spl.net.srv;

import java.io.IOException;

public interface Connections<T> {

    /**
     * Sends a message T to client represented by the given connectionId (server -> client)
     * -itay
     * @param connectionId
     * @param msg
     * @return True if message was sent, otherwise false.
     */
    boolean send(int connectionId, T msg);

    /**
     * Sends a message T to clients subscribed to channel (server -> clients in channel). -itay
     * @param channel
     * @param msg
     */
    void send(String channel, T msg);

    /**
     * Removes an active client connectionId from the map. -itayk
     * @param connectionId
     */
    void disconnect(int connectionId);

    
    //Helper Functions
    void addConnection(int connectionId, ConnectionHandler<T> handler);

    void subscribe(String channel, int connectionId, String subscriptionId);

    void unsubscribe(String subscriptionId, int connectionId);

    boolean isUserSubscribed(int connectionId, String channel);

    //String addSubIdToMessage( String subscriptionId, String msg);

}
