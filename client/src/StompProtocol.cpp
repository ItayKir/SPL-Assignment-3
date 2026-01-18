#include "../include/StompProtocol.h"
#include "../include/event.h"
#include <iostream>
#include <sstream>
#include <map>
#include <vector>
#include <algorithm>

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
std::string StompProtocol::createSendFrame(std::string destination, std::string frameBody) {
    std::map<std::string, std::string> headers;
    headers["destination"] = destination;
    
    return createStompFrame("SEND", headers, frameBody);
}

/**
 * @brief create subscribe frame
 * 
 * @param destination 
 * @return std::string 
 */
std::string StompProtocol::createSubscribeFrame(std::string destination) {
    std::map<std::string, std::string> headers;
    headers["destination"] = destination;
    
    int id = addChannel(destination);
    headers["id"] = std::to_string(id);

    headers["receipt"] = std::to_string(receiptId);
    receiptId++;
    
    return createStompFrame("SUBSCRIBE", headers, "");
}

/**
 * @brief Create unsubscribe frame
 * 
 * @param destination 
 * @return std::string 
 */
std::string StompProtocol::createUnsubscribeFrame(std::string destination) {
    std::map<std::string, std::string> headers;
    
    int id = getChannelSubId(destination);
    headers["id"] = std::to_string(id);

    headers["receipt"] = std::to_string(receiptId);
    receiptId++;

    return createStompFrame("UNSUBSCRIBE", headers, "");
}

/**
 * @brief Create disconnecte frame
 * 
 * @return std::string 
 */
std::string StompProtocol::createDisconnectFrame() {
    std::map<std::string, std::string> headers;
    
    disconnectId = receiptId;
    headers["receipt"] = std::to_string(receiptId);
    receiptId++;
    
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

/**
 * @brief Deletes currently saved data
 * 
 */
void StompProtocol::deleteData() {
    topicToSubId.clear();
    subscriptionId = 0;
    receiptId = 0;
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



/**
 * @brief Save event per user
 * 
 * @param event 
 * @param username 
 */
void StompProtocol::saveEvent(const Event& event, std::string username) {
    // Construct the unique game name (Topic)
    std::string gameName = event.get_team_a_name() + "_" + event.get_team_b_name();
    
    // Store the event
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
void addRowToSummary(std::string& body, std::string rowKey, std::string rowValue="", std::string delimiter = ""){
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
    // 1. Check if we have data
    if (gameUpdates.find(gameName) == gameUpdates.end() || 
        gameUpdates[gameName].find(user) == gameUpdates[gameName].end()) {
        std::cout << "No updates found for " << gameName << " from user " << user << std::endl;
        return;
    }

    // 2. Get the user's events (Make a copy so we can sort without messing up the original order if needed)
    std::vector<Event> events = gameUpdates[gameName][user];
    std::string team_a_name = events[0].get_team_a_name();
    std::string team_b_name = events[0].get_team_b_name();

    // 3. SORT by time (Requirement: "ordered in the order that they happened")
    // If times are equal, you might want a secondary sort, but time is usually sufficient.
    std::sort(events.begin(), events.end(), [](const Event& a, const Event& b) {
        bool a_before = true;
        bool b_before = true;
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

    // 4. Aggregate Stats (Requirement: "stats... ordered lexicographically")
    // std::map automatically sorts keys alphabetically.
    std::map<std::string, std::string> general_stats;
    std::map<std::string, std::string> team_a_stats;
    std::map<std::string, std::string> team_b_stats;

    for (const auto& event : events) {
        // Since we iterate through sorted events, these maps will hold the LATEST value for each key.
        for (const auto& pair : event.get_game_updates()) general_stats[pair.first] = pair.second;
        for (const auto& pair : event.get_team_a_updates()) team_a_stats[pair.first] = pair.second;
        for (const auto& pair : event.get_team_b_updates()) team_b_stats[pair.first] = pair.second;
    }

    std::string summaryString = "";

    // 5. Print the Report
    addRowToSummary(summaryString, team_a_name + " vs " + team_b_name);
    addRowToSummary(summaryString, "General stats", ":");

    for (const auto& pair : general_stats)
        addRowToSummary(summaryString, pair.first, ": ", pair.second);

    // team a stats
    addRowToSummary(summaryString, team_a_name + "stats", ":");
    for (const auto& pair : team_a_stats)
        addRowToSummary(summaryString, pair.first, ": ", pair.second);

    //team b stats
    addRowToSummary(summaryString, team_b_name + "stats", ":");
    for (const auto& pair : team_b_stats) 
        addRowToSummary(summaryString, pair.first, ": ", pair.second);

    addRowToSummary(summaryString, "Game event reports", ":");
    for (const auto& event : events) {
        // Print the short report format (Time - Name: Description)
        addRowToSummary(summaryString, event.get_time() + "-" + event.get_name(), ":");
        addRowToSummary(summaryString, event.get_discription());
    }

    return summaryString;
}

