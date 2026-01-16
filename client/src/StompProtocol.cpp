#include <iostream>
#include <thread>
#include <vector>
#include <string>
#include <map>
#include "../include/ConnectionHandler.h"
#include "../include/event.h"

class StompProtocol{
    private:
        std::map<std::string, int> topicToSubId;
        int subscriptionId;
        int receiptId;
        std::string userName;    

    public:
        int StompProtocol::addChannel(std::string channel){
            topicToSubId[channel] = subscriptionId;
            subscriptionId++;
        }

        int StompProtocol::getChannelSubId(std::string channel){
            return topicToSubId[channel]; 
        }
        
        std::string StompProtocol::getUserName(){
            return userName;
        }

        std::string createConnectFrame(std::string host, std::string username, std::string password){
            std::map<std::string, std::string> headers;
            headers["accept-version"] = "1.2";
            headers["host"] = host;
            headers["login"] = username;
            headers["passcode"] = password;
            return createStompFrame("CONNECT", headers,"");
        };

        std::string StompProtocol::createSendFrame(std::string destination, std::string frameBody){
            std::map<std::string, std::string> headers;  
            headers["destination"] = destination;

            return createStompFrame("SEND", headers, "user:"+ userName + "\n" + frameBody);
        }

        std::string StompProtocol::createSubscribeFrame(std::string destination){
            std::map<std::string, std::string> headers;
            headers["destination"] = destination;
            headers["id"] = topicToSubId[destination];

            headers["receipt"] = receiptId;
            receiptId++;
            return createStompFrame("SUBSCRIBE", headers, "");
        }

        std::string StompProtocol::createUnsubscribeFrame(std::string destination){
            std::map<std::string, std::string> headers;
            headers["id"] = topicToSubId[destination];

            headers["receipt"] = receiptId;
            receiptId++;
            return createStompFrame("SUBSCRIBE", headers, "");
        }

        std::string StompProtocol::createDisconnectFrame(){
            std::map<std::string, std::string> headers;
            headers["receipt"] = receiptId;
            receiptId++;
            return createStompFrame("DISCONNECT", headers, "");
        }


        std::string StompProtocol::createStompFrame(std::string command, std::map<std::string, std::string> headers, std::string body) {
            std::string frame = "";

            frame += command + "\n";

            for (const auto& pair : headers) {
                frame += pair.first + ":" + pair.second + "\n";
            }

            frame += "\n";

            frame += body;

            frame += '\0';

            return frame;
        }


        
        void StompProtocol::deteleData(){
            topicToSubId.clear();
            subscriptionId = 0;
            receiptId = 0;
            userName = nullptr;
        }
};
