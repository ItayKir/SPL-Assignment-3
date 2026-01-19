#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include <string>
#include <map>
#include <vector>
#include <algorithm>
#include <mutex>

class StompProtocol
{
private:
    std::map<std::string, int> topicToSubId;
    int subscriptionId;
    int receiptIdCounter;
    std::string userName;
    bool shouldTerminate;
    int disconnectId=-1;
    
    // game_name -> (user_name -> Events)
    std::map<std::string, std::map<std::string, std::vector<Event>>> gameUpdates;

    std::map<int, std::string> receiptCallbacks;
    std::mutex receiptMutex;

public:
    StompProtocol();

    void setUserName(std::string name);

    std::string getUserName();

    int addChannel(std::string channel);

    int getChannelSubId(std::string channel);

    void removeChannel(std::string channel);
    
    void deleteData();

    std::string createConnectFrame(std::string host, std::string username, std::string password);

    std::string createSendFrame(std::string destination, std::string frameBody, std::string filePath ="");

    std::string createSubscribeFrame(std::string destination, int receipt_id);

    std::string createUnsubscribeFrame(std::string destination, int receipt_id);

    std::string createDisconnectFrame();

    std::string createStompFrame(std::string command, std::map<std::string,std::string> headers, std::string body);

    bool processServerResponse(std::string frame);

    std::string summarizeGame(std::string gameName, std::string user);

    void saveEvent(const Event& event, std::string username);

    void addRowToSummary(std::string& body, std::string rowKey, std::string delimiter="", std::string rowValue = "");
    
    int addReceipt(std::string printMessage);

    bool isShouldTerminate(){return shouldTerminate;};
};