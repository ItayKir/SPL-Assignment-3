#pragma once

#include "../include/ConnectionHandler.h"
#include <string>
#include <map>
#include <vector>

class StompProtocol
{
private:
    std::map<std::string, int> topicToSubId;
    int subscriptionId;
    int receiptId;
    std::string userName;
    bool isConnected;
    bool shouldTerminate;
    int disconnectId=-1;

public:
    StompProtocol();

    void setUserName(std::string name);

    std::string getUserName();

    int addChannel(std::string channel);

    int getChannelSubId(std::string channel);
    
    void deleteData();

    std::string createConnectFrame(std::string host, std::string username, std::string password);

    std::string createSendFrame(std::string destination, std::string frameBody);

    std::string createSubscribeFrame(std::string destination);

    std::string createUnsubscribeFrame(std::string destination);

    std::string createDisconnectFrame();

    std::string createStompFrame(std::string command, std::map<std::string,std::string> headers, std::string body);

    bool processServerResponse(std::string frame);
};