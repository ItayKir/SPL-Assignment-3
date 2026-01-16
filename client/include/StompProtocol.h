#pragma once

#include "../include/ConnectionHandler.h"

// TODO: implement the STOMP protocol
class StompProtocol
{
private:
    std::map<std::string, int> topicToSubId;
	int subscriptionId;
	int receiptId;
    std::string userName;
public:
    StompProtocol(std::string userName){
        subscriptionId = 0;
        receiptId = 0;
        userName = userName;
    };

    int addChannel(std::string channel);

    int getChannelSubId(std::string channel);

    int getAndAddReceiptId();

    std::string getUserName();

    void deleteData();

    std::string createConnectFrame(std::string host, std::string port, std::string username, std::string password);

    std::string createSendFrame(std::string destination);

    std::string createSubscribeFrame(std::string channel);

    std::string createUnsubscribeFrame(std::string channel);

    std::string StompProtocol::createDisconnectFrame();

    std::string createStompFrame(std::string command, std::map<std::string,std::string> headers, std::string body);
};
