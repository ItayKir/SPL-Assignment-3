package bgu.spl.net.srv;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConnectionsImpl<T> implements Connections<T>{

    //TODO: Need to check if this is enough to support subscribtion ID checking.
    private final ConcurrentMap<Integer, ConnectionHandler<T>> connectionsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Integer>> channelSubscribers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Set<String>> clientsSubscribedChannels = new ConcurrentHashMap<>();

    @Override
    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> connectionHandler = this.connectionsMap.get(connectionId);

        if (connectionHandler == null) {
            return false; 
        }

        connectionHandler.send(msg);
        return true;        
    }

    @Override
    public void send(String channel, T msg){
        Set<Integer> channelSubs = this.channelSubscribers.get(channel);

        if(channelSubs != null){
            for(Integer connectionId: channelSubs){
                send(connectionId, msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId){
        ConnectionHandler<T> removedHandler = this.connectionsMap.remove(connectionId);
        if(removedHandler != null){
            try{
                removedHandler.close();
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }

        Set<String> connectionSubscribedChannels = this.clientsSubscribedChannels.remove(connectionId);
        if(connectionSubscribedChannels != null){
            for(String channel: connectionSubscribedChannels){
                Set<Integer> channelSubs = this.channelSubscribers.get(channel);
                if(channelSubs != null){
                    channelSubs.remove(connectionId);
                }
            }
        }
    }

    // Helper functions:
    /**
     * Saves given connection with the handler.
     * @param connectionId
     * @param handler
     */
    @Override
    public void addConnection(int connectionId, ConnectionHandler<T> handler){
        this.connectionsMap.put(connectionId, handler);
        this.clientsSubscribedChannels.put(connectionId, ConcurrentHashMap.newKeySet());
    }

    /**
     * Make connectionId a subscriber of channel
     * @param channel
     * @param connectionId
     */
    @Override
    public void subscribe(String channel, int connectionId){
        channelSubscribers.computeIfAbsent(channel, k-> ConcurrentHashMap.newKeySet()).add(connectionId);
        Set<String> userChannels =this.clientsSubscribedChannels.get(connectionId);
        if(userChannels != null){
            userChannels.add(channel);
        }
    }

    /**
     * Remove connectionId a subscriber of channel
     * @param channel
     * @param connectionId
     */
    @Override
    public void unsubscribe(String channel, int connectionId){
        Set<Integer> subs = this.channelSubscribers.get(channel);
        if(subs != null){
            subs.remove(connectionId);
        }

        Set<String> clientChannels = this.clientsSubscribedChannels.get(connectionId);
        if(clientChannels != null){
            clientChannels.remove(channel);
        }
    }

    /**
     * Returns true iff user with connectionId is subscribed to channel. Will return false otherwise, including if connectionid does not exist.
     * @param connectionId
     * @param channel
     * @return
     */
    @Override
    public boolean isUserSubscribed(int connectionId, String channel){
        Set<String> userSubscriptions = this.clientsSubscribedChannels.get(connectionId);
        return userSubscriptions!= null && userSubscriptions.contains(channel);
    }

}