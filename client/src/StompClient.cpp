#include <iostream>
#include <thread>
#include <vector>
#include <string>
#include <map>
#include "../include/ConnectionHandler.h"
#include "../include/event.h"


void socketListen(ConnectionHandler* handler, bool* shouldTerminate){
	while(!*shouldTerminate){

	}
}

int main(int argc, char *argv[]) {
	std::thread th1(socketListen, &handler, &shouldTerminate)

	std::cout << "Please enter the following to log in: login <host> <port> <username> <password> " << std::endl;
	while(true){
		std::getline(std::cin, userInput);
	}
	return 0;
}

