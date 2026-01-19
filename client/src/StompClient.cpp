#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <thread>
#include <mutex>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include "../include/event.h"
#include <fstream>

/**
 * @brief Helper function to split arguments
 * 
 * @param str 
 * @param delimiter 
 * @return std::vector<std::string> 
 */
std::vector<std::string> split(const std::string& str, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(str);
    while (std::getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

/**
 * @brief Thread task that reads from the server and processes 
 * 
 * @param handler 
 * @param protocol 
 * @param shouldTerminate 
 */
void readSocketTask(ConnectionHandler* handler, StompProtocol* protocol, bool* shouldTerminate) {
    while (!*shouldTerminate) {
        std::string answer;
        if (!handler->getFrameAscii(answer, '\0')) { 
            std::cout << "Disconnected. Exiting socket thread." << std::endl;
            *shouldTerminate = true;
            break;
        }

        bool closeConnection = protocol->processServerResponse(answer);
        if (closeConnection) {
            *shouldTerminate = true;
            break;
        }
    }
}

/**
 * @brief Handle join command
 * 
 * @param args 
 * @param protocol 
 * @param handler 
 */
void handleJoin(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler) {
    if (args.size() > 1) {
        std::string channel = args[1];
        if(protocol.getChannelSubId(channel)!=-1){
            std::cout << "You already joined this channel. Ignoring request." << std::endl;
            return;
        }
        int receiptToAdd = protocol.addReceipt("Joined channel " + channel);
        std::string frame = protocol.createSubscribeFrame(channel, receiptToAdd);
        handler.sendBytes(frame.c_str(), frame.length());
    } else {
        std::cout << "Usage: join {channel_name}" << std::endl;
    }
}

/**
 * @brief Handles exit command
 * 
 * @param args 
 * @param protocol 
 * @param handler 
 */
void handleExit(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler) {
    if (args.size() > 1) {
        std::string channel = args[1];

        if(protocol.getChannelSubId(channel)==-1){
            std::cout << "You were already not subscribed to this channel. Ignoring request." << std::endl;
            return;
        }
        protocol.removeChannel(channel);
        int receiptToAdd = protocol.addReceipt("Exited channel " + channel);
        std::string frame = protocol.createUnsubscribeFrame(channel, receiptToAdd);
        handler.sendBytes(frame.c_str(), frame.length());
    } else {
        std::cout << "Usage: exit {channel_name}" << std::endl;
    }
}

/**
 * @brief Handle add command
 * 
 * @param args 
 * @param protocol 
 * @param handler 
 */
void handleAdd(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler) {
    if (args.size() > 2) {
        std::string channel = args[1];
        std::string message = "";
        for (size_t i = 2; i < args.size(); ++i) {
            message += args[i] + (i == args.size() - 1 ? "" : " ");
        }
        std::string frame = protocol.createSendFrame(channel, message);
        handler.sendBytes(frame.c_str(), frame.length());
    } else {
        std::cout << "Usage: add {channel_name} {message}" << std::endl;
    }
}

/**
 * @brief Adds a row in the needed format to the report. Sending by refrence to change the body.
 * 
 * @param body 
 * @param rowKey 
 * @param rowValue 
 */
void addRowToReport(std::string& body, std::string rowKey, std::string rowValue) {
    body += rowKey + ":" + rowValue + "\n";
}

/**
 * @brief Handles report command
 * 
 * @param args 
 * @param protocol 
 * @param handler 
 */
void handleReport(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler) {
    if (args.size() > 1) {
        std::string file = args[1];
        
        names_and_events data = parseEventsFile(file); 
        std::string channel_name = data.team_a_name + "_" + data.team_b_name;

        bool firstEvent = true;
        for (const Event& event : data.events) {
            std::string body = "";
            

            addRowToReport(body, "user", protocol.getUserName());
            addRowToReport(body, "team a", data.team_a_name);
            addRowToReport(body, "team b", data.team_b_name);
            addRowToReport(body, "event name", event.get_name());
            addRowToReport(body, "time", std::to_string(event.get_time()));
            
            body += "general game updates:\n";
            for (const auto& pair : event.get_game_updates()) {
                addRowToReport(body, pair.first, pair.second);
            }

            body += "team a updates:\n";
            for (const auto& pair : event.get_team_a_updates()) {
                addRowToReport(body, pair.first, pair.second);
            }

            body += "team b updates:\n";
            for (const auto& pair : event.get_team_b_updates()) {
                addRowToReport(body, pair.first, pair.second);
            }

            body += "description:\n";
            body += event.get_discription();

            // Only uploading to DB file tracking once
            std::string filePathToUpload = "";
            if(firstEvent){
                filePathToUpload = file;
                firstEvent= false; 
            }

            std::string frame = protocol.createSendFrame(channel_name, body, filePathToUpload);
            handler.sendBytes(frame.c_str(), frame.length());
        }
    } else {
        std::cout << "Usage: report {file_path}" << std::endl;
    }
}

/**
 * @brief Handles summary command
 * 
 * @param args 
 * @param protocol 
 * @param handler 
 */
void handleSummary(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler){
    if (args.size() > 3){
        std::string gameSummary = protocol.summarizeGame(args[1], args[2]);
        std::cout << gameSummary << std::endl;

        std::ofstream outFile(args[3]);
        if(outFile.is_open()){
            outFile << gameSummary;
            outFile.close();
        }
    } else{
        std::cout << "Usage: summary {game_name} {user} {file}" << std::endl;
    }
}


/**
 * @brief Handles logout command 
 * 
 * @param protocol 
 * @param handler 
 * @param socketThread 
 * @param shouldTerminate 
 */
void handleLogout(StompProtocol& protocol, ConnectionHandler& handler, std::thread& socketThread, bool& shouldTerminate) {
    std::string frame = protocol.createDisconnectFrame();
    handler.sendBytes(frame.c_str(), frame.length());

    // Wait for the server to confirm disconnect (via receipt in readSocketTask)
    if (socketThread.joinable()) {
        socketThread.join();
    }
    shouldTerminate = true; 
    
    // Cleanup
    handler.close();
    protocol.deleteData();
    std::cout << "Logged out. Ready for new login." << std::endl;
}


/**
 * @brief The main loop, executes the needed function based on user command
 * 
 * @param handler 
 * @param protocol 
 * @param socketThread 
 */
void runCommandLoop(ConnectionHandler& handler, StompProtocol& protocol, std::thread& socketThread) {
    bool shouldTerminate = false;
    const short bufsize = 1024;
    char buf[bufsize];

    while (!shouldTerminate) {
        std::cin.getline(buf, bufsize);
        std::string line(buf);
        std::vector<std::string> args = split(line, ' ');

        if (args.empty()) continue;
        std::string cmd = args[0];

        if (cmd == "join") {
            handleJoin(args, protocol, handler);
        } 
        else if (cmd == "exit") {
            handleExit(args, protocol, handler);
        } 
        else if (cmd == "add") {
            handleAdd(args, protocol, handler);
        } 
        else if (cmd == "report") {
            handleReport(args, protocol, handler);
        } 
        else if (cmd == "logout") {
            handleLogout(protocol, handler, socketThread, shouldTerminate);
        }
        else if (cmd == "summary") {
            handleSummary(args, protocol, handler);
        }
        else {
            std::cout << "Unknown command: " << cmd << std::endl;
        }
    }
}

int main(int argc, char *argv[]) {
    while (true) {
        const short bufsize = 1024;
        char buf[bufsize];
        
        // Wait for login command
        std::cin.getline(buf, bufsize);
        std::string line(buf);
        std::vector<std::string> args = split(line, ' ');

        if (args.empty()) continue;

        if (args[0] == "login") {
            if (args.size() < 5) {
                std::cout << "Usage: login {host} {port} {username} {password}" << std::endl;
                continue;
            }

            std::string host = args[1];
            short port = std::stoi(args[2]);
            std::string username = args[3];
            std::string password = args[4];

            ConnectionHandler handler(host, port);
            if (!handler.connect()) {
                std::cout << "Could not connect to server " << host << ":" << port << std::endl;
                continue;
            }

            StompProtocol protocol;
            protocol.setUserName(username);

            // Send Connect Frame
            std::string loginFrame = protocol.createConnectFrame(host, username, password);
            if (!handler.sendBytes(loginFrame.c_str(), loginFrame.length())) {
                std::cout << "Disconnected before login complete" << std::endl;
                continue;
            }

            // Start Thread and Enter Command Loop
            bool shouldTerminate = protocol.isShouldTerminate();
            std::thread socketThread(readSocketTask, &handler, &protocol, &shouldTerminate);

            runCommandLoop(handler, protocol, socketThread);
        } else {
            std::cout << "Please login first." << std::endl;
        }
    }
    return 0;
}