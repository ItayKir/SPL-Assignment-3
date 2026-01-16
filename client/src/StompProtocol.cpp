#include "../include/StompProtocol.h"
#include <iostream>
#include <sstream>

StompProtocol::StompProtocol() : subscriptionId(0), receiptId(0), userName(""), isConnected(false), shouldTerminate(false) {
}

void StompProtocol::setUserName(std::string name) {
    this->userName = name;
}

std::string StompProtocol::getUserName() {
    return userName;
}

int StompProtocol::addChannel(std::string channel) {
    if (topicToSubId.find(channel) == topicToSubId.end()) {
        topicToSubId[channel] = subscriptionId;
        subscriptionId++;
    }
    return topicToSubId[channel];
}

int StompProtocol::getChannelSubId(std::string channel) {
    if (topicToSubId.find(channel) != topicToSubId.end()) {
        return topicToSubId[channel];
    }
    return -1;
}

std::string StompProtocol::createConnectFrame(std::string host, std::string username, std::string password) {
    std::map<std::string, std::string> headers;
    headers["accept-version"] = "1.2";
    headers["host"] = host;
    headers["login"] = username;
    headers["passcode"] = password;
    return createStompFrame("CONNECT", headers, "");
}

std::string StompProtocol::createSendFrame(std::string destination, std::string frameBody) {
    std::map<std::string, std::string> headers;
    headers["destination"] = destination;
    
    return createStompFrame("SEND", headers, frameBody);
}

std::string StompProtocol::createSubscribeFrame(std::string destination) {
    std::map<std::string, std::string> headers;
    headers["destination"] = destination;
    
    int id = addChannel(destination);
    headers["id"] = std::to_string(id);

    headers["receipt"] = std::to_string(receiptId);
    receiptId++;
    
    return createStompFrame("SUBSCRIBE", headers, "");
}

std::string StompProtocol::createUnsubscribeFrame(std::string destination) {
    std::map<std::string, std::string> headers;
    
    int id = getChannelSubId(destination);
    headers["id"] = std::to_string(id);

    headers["receipt"] = std::to_string(receiptId);
    receiptId++;

    return createStompFrame("UNSUBSCRIBE", headers, "");
}

std::string StompProtocol::createDisconnectFrame() {
    std::map<std::string, std::string> headers;
    
    disconnectId = receiptId;
    headers["receipt"] = std::to_string(receiptId);
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

void StompProtocol::deleteData() {
    topicToSubId.clear();
    subscriptionId = 0;
    receiptId = 0;
    userName = "";
}





bool StompProtocol::processServerResponse(std::string frame) {
    std::stringstream ss(frame);
    std::string line;
    std::string command;
    std::map<std::string, std::string> headers;
    std::string body;

    //Command
    if (std::getline(ss, command)) {
        if (!command.empty() && command.back() == '\r') 
            command.pop_back();
    }

    //Headers
    while (std::getline(ss, line) && !line.empty() && line != "\r") {
        if (line.back() == '\r') 
            line.pop_back();
        
        size_t delimiterPos = line.find(':');
        if (delimiterPos != std::string::npos) {
            std::string key = line.substr(0, delimiterPos);
            std::string value = line.substr(delimiterPos + 1);
            headers[key] = value;
        }
    }

    //Body
    std::stringstream bodyStream;
    bodyStream << ss.rdbuf();
    body = bodyStream.str();
    if (!body.empty() && body.back() == '\0') 
        body.pop_back();


    //Logic

    if (command == "CONNECTED") {
        std::cout << "Login successful" << std::endl;
        return false;
    }

    else if (command == "MESSAGE") {
        std::cout << body << std::endl;
        return false;
    }

    else if (command == "RECEIPT") {
        if (headers.count("receipt-id")) {
            std::string recId = headers["receipt-id"];
            if (this->disconnectId != -1 && recId == std::to_string(this->disconnectId)) {
                return true;
            }
        }
        return false;
    }

    else if (command == "ERROR") {
        std::cout << "Error received from server: " << body << std::endl;
        return true; 
    }

    return false;
}

