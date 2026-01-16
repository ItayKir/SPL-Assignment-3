#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <thread>
#include <mutex>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include "../include/event.h"

//helper functions

std::vector<std::string> split(const std::string& str, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(str);
    while (std::getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

//thread task that reads from server
void readSocketTask(ConnectionHandler* handler, StompProtocol* protocol, bool* shouldTerminate) {
    while (!*shouldTerminate) {
        std::string answer;
        if (!handler->getLine(answer)) {
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

// every handle command is responsible for handling user input
void handleJoin(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler) {
    if (args.size() > 1) {
        std::string channel = args[1];
        std::string frame = protocol.createSubscribeFrame(channel);
        handler.sendLine(frame);
    } else {
        std::cout << "Usage: join {channel_name}" << std::endl;
    }
}

void handleExit(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler) {
    if (args.size() > 1) {
        std::string channel = args[1];
        std::string frame = protocol.createUnsubscribeFrame(channel);
        handler.sendLine(frame);
    } else {
        std::cout << "Usage: exit {channel_name}" << std::endl;
    }
}

void handleAdd(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler) {
    if (args.size() > 2) {
        std::string channel = args[1];
        std::string message = "";
        for (size_t i = 2; i < args.size(); ++i) {
            message += args[i] + (i == args.size() - 1 ? "" : " ");
        }
        std::string frame = protocol.createSendFrame(channel, message);
        handler.sendLine(frame);
    } else {
        std::cout << "Usage: add {channel_name} {message}" << std::endl;
    }
}

void handleReport(const std::vector<std::string>& args, StompProtocol& protocol, ConnectionHandler& handler) {
    if (args.size() > 1) {
        std::string file = args[1];
        names_and_events data = parseEventsFile(file); // From event.h

        for (const Event& event : data.events) {
            // Format body per assignment specs
            std::string body = "user:" + protocol.getUserName() + "\n" +
                               "city:" + event.get_city() + "\n" +
                               "event name:" + event.get_name() + "\n" +
                               "date_time:" + std::to_string(event.get_date_time()) + "\n" +
                               "general information:\n" +
                               "active:" + (event.get_general_information().get_active() ? "true" : "false") + "\n" +
                               "forces_arrival_at_scene:" + (event.get_general_information().get_forces_arrival_at_scene() ? "true" : "false") + "\n" +
                               "description:" + event.get_description();

            // Note: Assuming 'some_destination' is replaced by actual logic or channel name
            handler.sendLine(protocol.createSendFrame("some_destination", body));
        }
    } else {
        std::cout << "Usage: report {file_path}" << std::endl;
    }
}

// Returns TRUE if logout was initiated
void handleLogout(StompProtocol& protocol, ConnectionHandler& handler, std::thread& socketThread, bool& shouldTerminate) {
    std::string frame = protocol.createDisconnectFrame();
    handler.sendLine(frame);

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

// ---------------------------------------------------------------------------------
//                                MAIN LOOPS
// ---------------------------------------------------------------------------------

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
            if (!handler.sendLine(loginFrame)) {
                std::cout << "Disconnected before login complete" << std::endl;
                continue;
            }

            // Start Thread and Enter Command Loop
            bool shouldTerminate = false;
            std::thread socketThread(readSocketTask, &handler, &protocol, &shouldTerminate);

            runCommandLoop(handler, protocol, socketThread);
        } else {
            std::cout << "Please login first." << std::endl;
        }
    }
    return 0;
}