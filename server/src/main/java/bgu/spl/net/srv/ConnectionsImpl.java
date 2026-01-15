package bgu.spl.net.srv;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConnectionsImpl<T> implements Connections<T> {

    // ID -> Handler
    private final ConcurrentMap<Integer, ConnectionHandler<T>> connectionsMap = new ConcurrentHashMap<>();

    // Channel -> Connection IDs
    private final ConcurrentMap<String, Set<Integer>> channelSubscribers = new ConcurrentHashMap<>();

    //  Connection ID -> (Subscription ID -> Channel)
    private final ConcurrentMap<Integer, ConcurrentMap<String, String>> clientSubscriptionIdToChannel = new ConcurrentHashMap<>();

    //Connection ID -> (Channel Name -> Subscription ID)
    private final ConcurrentMap<Integer, ConcurrentMap<String, String>> clientChannelToSubscriptionId = new ConcurrentHashMap<>();

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = this.connectionsMap.get(connectionId);
        if (handler == null) {
            return false;
        }
        handler.send(msg);
        return true;
    }

    @Override
    public void send(String channel, T msg) {
        // Get all users subscribed to this channel
        Set<Integer> subscribers = channelSubscribers.get(channel);
        
        if (subscribers != null) {
            for (Integer connId : subscribers) {
                ConcurrentMap<String, String> channelToSub = this.clientChannelToSubscriptionId.get(connId);
                
                if (channelToSub != null) {
                    String subscriptionId = channelToSub.get(channel);
                    
                    if (subscriptionId != null) {
                        if (msg instanceof String) {
                            send(connId, (T) addSubIdToMessage(subscriptionId, (String) msg));
                        }
                        else{
                            send(connId, msg);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        ConnectionHandler<T> handler = this.connectionsMap.remove(connectionId);
        if (handler != null) {
            try {
                handler.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ConcurrentMap<String, String> userChannels = this.clientChannelToSubscriptionId.remove(connectionId);
        if (userChannels != null) {
            for (String channel : userChannels.keySet()) {
                Set<Integer> subscribers = this.channelSubscribers.get(channel);
                if (subscribers != null) {
                    subscribers.remove(connectionId);
                }
            }
        }

        // 3. Clean up the reverse mapping
        this.clientSubscriptionIdToChannel.remove(connectionId);
    }

    /**
     * Helper: Adds a new client connection.
     */
    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        this.connectionsMap.put(connectionId, handler);
        this.clientChannelToSubscriptionId.put(connectionId, new ConcurrentHashMap<>());
        this.clientSubscriptionIdToChannel.put(connectionId, new ConcurrentHashMap<>());
    }

    /**
     * Helper: Subscribes a user to a channel with a specific Subscription ID.
     */
    public void subscribe(String channel, int connectionId, String subscriptionId) {
        channelSubscribers.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(connectionId);

        this.clientChannelToSubscriptionId.computeIfAbsent(connectionId, k -> new ConcurrentHashMap<>())
                                     .put(channel, subscriptionId);

        this.clientSubscriptionIdToChannel.computeIfAbsent(connectionId, k -> new ConcurrentHashMap<>())
                                     .put(subscriptionId, channel);
    }

    /**
     * Helper: Unsubscribes a user based on Subscription ID.
     */
    public void unsubscribe(String subscriptionId, int connectionId) {
        ConcurrentMap<String, String> subToChannel = this.clientSubscriptionIdToChannel.get(connectionId);
        if (subToChannel == null) return;
        
        String channel = subToChannel.remove(subscriptionId);
        
        if (channel != null) {
            Set<Integer> subscribers = this.channelSubscribers.get(channel);
            if (subscribers != null) {
                subscribers.remove(connectionId);
            }
            
            ConcurrentMap<String, String> channelToSub = this.clientChannelToSubscriptionId.get(connectionId);
            if (channelToSub != null) {
                channelToSub.remove(channel);
            }
        }
    }

    /**
     * Helper: Checks if a user is subscribed to a channel.
     */
    public boolean isUserSubscribed(int connectionId, String channel) {
        ConcurrentMap<String, String> userChannels = this.clientChannelToSubscriptionId.get(connectionId);
        return userChannels != null && userChannels.containsKey(channel);
    }

    /**
     * Helper: Adding subscsription ID to a message
     */
    private String addSubIdToMessage(String subscriptionId, String msg){
        return msg.replaceFirst("\n", "\nsubscription:" + subscriptionId + "\n");
    }
}