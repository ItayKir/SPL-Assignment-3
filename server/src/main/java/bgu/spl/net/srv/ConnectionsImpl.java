package bgu.spl.net.srv;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConnectionsImpl<T> implements Connections<T>{

    private final ConcurrentMap<Integer, ConnectionHandler<T>> connectionsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Integer>> channelSubscibers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Set<String>> clientsSubscribedChannels = new ConcurrentHashMap<>();

    @Override
    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> connectionHandler = connectionsMap.get(connectionId);

        if (connectionHandler == null) {
            return false; 
        }

        connectionHandler.send(msg);
        return true;        
    }

    @Override
    public void send(String channel, T msg){
        Set<Integer> channelSubs = channelSubscibers.get(channel);

        if(channelSubs != null){
            for(Integer connectionId: channelSubs){
                send(connectionId, msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId){
        ConnectionHandler<T> removedHandler = connectionsMap.remove(connectionId);
        if(removedHandler != null){
            try{
                removedHandler.close();
            }
            catch(IOException e){
                e.printStackTrace();
            }
        }

        Set<String> connectionSubscribedChannels = clientsSubscribedChannels.get(connectionId);
        if(connectionSubscribedChannels != null){
            for(String channel: connectionSubscribedChannels){
                Set<Integer> channelSubscribers = channelSubscibers.get(channel);
                if(channelSubscribers != null){
                    channelSubscribers.remove(connectionId);
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
    public void addConnection(int connectionId, ConnectionHandler<T> handler){
        connectionsMap.put(connectionId, handler);
        clientsSubscribedChannels.put(connectionId, ConcurrentHashMap.newKeySet());
    }

    /**
     * Make connectionId a subscriber of channel
     * @param channel
     * @param connectionId
     */
    public void subscribe(String channel, int connectionId){
        channelSubscibers.computeIfAbsent(channel, k-> ConcurrentHashMap.newKeySet()).add(connectionId);
        Set<String> userChannels =clientsSubscribedChannels.get(connectionId);
        if(userChannels != null){
            userChannels.add(channel);
        }
    }

    /**
     * Remove connectionId a subscriber of channel
     * @param channel
     * @param connectionId
     */
    public void unsubscribe(String channel, int connectionId){
        Set<Integer> subs = channelSubscibers.get(channel);
        if(subs != null){
            subs.remove(connectionId);
        }

        Set<String> clientChannels = clientsSubscribedChannels.get(connectionId);
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
    public boolean isUserSubscribed(int connectionId, String channel){
        Set<String> userSubscriptions = clientsSubscribedChannels.get(connectionId);
        return userSubscriptions!= null && userSubscriptions.contains(channel);
    }

}