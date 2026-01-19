#include "../include/StompProtocol.h"
#include "../include/event.h"
#include <iostream>
#include <sstream>
#include <map>
#include <vector>
#include <algorithm>
#include <mutex>

StompProtocol::StompProtocol() :topicToSubId(), subscriptionId(0), receiptIdCounter(0), userName(""), shouldTerminate(false), gameUpdates(),receiptCallbacks(), receiptMutex() {
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

/**
 * @brief Returns the subId of a given channel. -1 if does not exist.
 * 
 * @param channel 
 * @return int 
 */
int StompProtocol::getChannelSubId(std::string channel) {
    if (topicToSubId.find(channel) != topicToSubId.end()) {
        return topicToSubId[channel];
    }
    return -1;
}

/**
 * @brief Removing the channel from the (Channel, SubId) map
 * 
 * @param channel 
 */
void StompProtocol::removeChannel(std::string channel){
    topicToSubId.erase(channel);
}

/**
 * @brief Create connect frame
 * 
 * @param host 
 * @param username 
 * @param password 
 * @return std::string 
 */
std::string StompProtocol::createConnectFrame(std::string host, std::string username, std::string password) {
    std::map<std::string, std::string> headers;
    headers["accept-version"] = "1.2";
    headers["host"] = host;
    headers["login"] = username;
    headers["passcode"] = password;
    return createStompFrame("CONNECT", headers, "");
}

/**
 * @brief Create send frame
 * 
 * @param destination 
 * @param frameBody 
 * @return std::string 
 */
std::string StompProtocol::createSendFrame(std::string destination, std::string frameBody, std::string filePath) {
    std::map<std::string, std::string> headers;
    headers["destination"] = destination;
    if(filePath != "")
        headers["file path"] = filePath;

    return createStompFrame("SEND", headers, frameBody);
}

/**
 * @brief create subscribe frame
 * 
 * @param destination 
 * @return std::string 
 */
std::string StompProtocol::createSubscribeFrame(std::string destination, int receipt_id) {
    std::map<std::string, std::string> headers;
    headers["destination"] = destination;
    
    int id = addChannel(destination);
    headers["id"] = std::to_string(id);

    headers["receipt"] = std::to_string(receipt_id);
    
    return createStompFrame("SUBSCRIBE", headers, "");
}

/**
 * @brief Create unsubscribe frame
 * 
 * @param destination 
 * @return std::string 
 */
std::string StompProtocol::createUnsubscribeFrame(std::string destination, int receipt_id) {
    std::map<std::string, std::string> headers;
    
    int id = getChannelSubId(destination);
    headers["id"] = std::to_string(id);

    headers["receipt"] = std::to_string(receipt_id);

    return createStompFrame("UNSUBSCRIBE", headers, "");
}

/**
 * @brief Create disconnecte frame
 * 
 * @return std::string 
 */
std::string StompProtocol::createDisconnectFrame() {
    std::map<std::string, std::string> headers;
    
    disconnectId = receiptIdCounter;
    headers["receipt"] = std::to_string(receiptIdCounter);
    receiptIdCounter++;
    
    return createStompFrame("DISCONNECT", headers, "");
}

/**
 * @brief Create stomp frame
 * 
 * @param command 
 * @param headers 
 * @param body 
 * @return std::string 
 */
std::string StompProtocol::createStompFrame(std::string command, std::map<std::string, std::string> headers, std::string body) {
    std::string frame = command + "\n";

    for (const auto& pair : headers) {
        frame += pair.first + ":" + pair.second + "\n";
    }

    frame += "\n"; 
    frame += body;
    frame += '\0';

    return frame;
}

/**
 * @brief Deletes currently saved data
 * 
 */
void StompProtocol::deleteData() {
    topicToSubId.clear();
    subscriptionId = 0;
    receiptIdCounter = 0;
    userName = "";
}




/**
 * @brief Process server responses 
 * 
 * @param frame 
 * @return true 
 * @return false 
 */
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

        while (!body.empty() && body.back() == '\0') {
            body.pop_back();
        }

        std::stringstream ss(body);
        std::string line;
        std::string user = "";

        while(std::getline(ss, line)){
            if(!line.empty() && line.back() =='\r')
                line.pop_back();

            if (line.find("user:") == 0) {
                user = line.substr(5);
                if (!user.empty() && user.back() == '\r') 
                    user.pop_back();
                break;
            }
        }
        Event event(body);
        
        // save event
        if (!user.empty()) {
            saveEvent(event, user);
        }

        // print
        std::cout << "Displaying new event from user: " << user << std::endl;
        std::cout << body << std::endl;
        return false;
    }

    else if (command == "RECEIPT") {
        if (headers.count("receipt-id")) {
            int recId = std::stoi(headers["receipt-id"]);
            std::lock_guard<std::mutex> lock(receiptMutex);
            if(receiptCallbacks.count(recId)){
                std::cout << receiptCallbacks[recId] << std::endl;
                receiptCallbacks.erase(recId);
            }
            if (this->disconnectId != -1 && recId == this->disconnectId) {
                return true;
            }
        }
        return false;
    }

    else if (command == "ERROR") {
        std::cout << "Error received from server: " << body << std::endl;
        terminateConnection();
        return true; 
    }

    return false;
}


/**
 * @brief Save event per user
 * 
 * @param event 
 * @param username 
 */
void StompProtocol::saveEvent(const Event& event, std::string username) {
    std::string gameName = event.get_team_a_name() + "_" + event.get_team_b_name();
    
    gameUpdates[gameName][username].push_back(event);
}

/**
 * @brief Helper function to add rows to summary
 * 
 * @param body 
 * @param rowKey 
 * @param rowValue 
 * @param delimiter 
 */
void StompProtocol::addRowToSummary(std::string& body, std::string rowKey, std::string delimiter, std::string rowValue){
    body += rowKey + delimiter + rowValue + "\n";
}

/**
 * @brief Returns a string representing the summary of the game by the user
 * 
 * @param gameName 
 * @param user 
 * @return std::string 
 */
std::string StompProtocol::summarizeGame(std::string gameName, std::string user) {
    // Check if we have data
    std::string summaryString = "";
    if (gameUpdates.find(gameName) == gameUpdates.end() || 
        gameUpdates[gameName].find(user) == gameUpdates[gameName].end()) {
        summaryString += "No updates found for " + gameName + " from user " + user;
        std::cout << summaryString  << std::endl;
        return summaryString;
    }

    // Get the user events 
    std::vector<Event> events = gameUpdates[gameName][user];
    std::string team_a_name = events[0].get_team_a_name();
    std::string team_b_name = events[0].get_team_b_name();

    // SORT (by half (true/false) then by time) 
    std::sort(events.begin(), events.end(), [](const Event& a, const Event& b) {
        bool a_before = (a.get_time() < 2700);
        bool b_before = (b.get_time() < 2700);
        if (a.get_game_updates().count("before halftime")) {
            a_before = (a.get_game_updates().at("before halftime") == "true");
        }
        if (b.get_game_updates().count("before halftime")) {
            b_before = (b.get_game_updates().at("before halftime") == "true");
        }

        if(a_before != b_before){
            return a_before;
        }
        return a.get_time() < b.get_time();
    });

    std::map<std::string, std::string> general_stats;
    std::map<std::string, std::string> team_a_stats;
    std::map<std::string, std::string> team_b_stats;

    for (const auto& event : events) {
        for (const auto& pair : event.get_game_updates()) 
            general_stats[pair.first] = pair.second;
        for (const auto& pair : event.get_team_a_updates()) 
            team_a_stats[pair.first] = pair.second;
        for (const auto& pair : event.get_team_b_updates()) 
            team_b_stats[pair.first] = pair.second;
    }

    
    addRowToSummary(summaryString, team_a_name + " vs " + team_b_name);
    addRowToSummary(summaryString, "Game stats", ":");
    addRowToSummary(summaryString, "General stats", ":");

    for (const auto& pair : general_stats)
        addRowToSummary(summaryString, pair.first, ": ", pair.second);

    // team a stats
    addRowToSummary(summaryString, team_a_name + " stats", ":");
    for (const auto& pair : team_a_stats)
        addRowToSummary(summaryString, pair.first, ": ", pair.second);

    //team b stats
    addRowToSummary(summaryString, team_b_name + " stats", ":");
    for (const auto& pair : team_b_stats) 
        addRowToSummary(summaryString, pair.first, ": ", pair.second);

    addRowToSummary(summaryString, "Game event reports", ":");
    for (const auto& event : events) {
        addRowToSummary(summaryString, std::to_string(event.get_time()) + " - " + event.get_name(), ":");
        summaryString += "\n";
        addRowToSummary(summaryString, event.get_discription());
        summaryString += "\n";
    }

    return summaryString;
}

int StompProtocol::addReceipt(std::string printMessage) {
    std::lock_guard<std::mutex> lock(receiptMutex);
    int id = ++receiptIdCounter;
    receiptCallbacks[id] = printMessage;
    return id;
}