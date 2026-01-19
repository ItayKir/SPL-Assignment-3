#include "../include/event.h"
#include "../include/json.hpp"
#include <iostream>
#include <fstream>
#include <string>
#include <map>
#include <vector>
#include <sstream>
using json = nlohmann::json;

Event::Event(std::string team_a_name, std::string team_b_name, std::string name, int time,
             std::map<std::string, std::string> game_updates, std::map<std::string, std::string> team_a_updates,
             std::map<std::string, std::string> team_b_updates, std::string discription)
    : team_a_name(team_a_name), team_b_name(team_b_name), name(name),
      time(time), game_updates(game_updates), team_a_updates(team_a_updates),
      team_b_updates(team_b_updates), description(discription)
{
}

Event::~Event()
{
}

const std::string &Event::get_team_a_name() const
{
    return this->team_a_name;
}

const std::string &Event::get_team_b_name() const
{
    return this->team_b_name;
}

const std::string &Event::get_name() const
{
    return this->name;
}

int Event::get_time() const
{
    return this->time;
}

const std::map<std::string, std::string> &Event::get_game_updates() const
{
    return this->game_updates;
}

const std::map<std::string, std::string> &Event::get_team_a_updates() const
{
    return this->team_a_updates;
}

const std::map<std::string, std::string> &Event::get_team_b_updates() const
{
    return this->team_b_updates;
}

const std::string &Event::get_discription() const
{
    return this->description;
}

Event::Event(const std::string & frame_body) : team_a_name(""), team_b_name(""), name(""), 
                                               time(0), game_updates(), team_a_updates(), 
                                               team_b_updates(), description("") 
{
    std::stringstream ss(frame_body);
    std::string line;

    std::map<std::string, std::string>* current_map_ptr = nullptr;
    bool parsing_description = false;

    while(std::getline(ss, line)) {
        // switch
        if (line == "general game updates:") {
            current_map_ptr = &game_updates;
            parsing_description = false;
            continue;
        }
        else if (line == "team a updates:") {
            current_map_ptr = &team_a_updates;
            parsing_description = false;
            continue;
        }
        else if (line == "team b updates:") {
            current_map_ptr = &team_b_updates;
            parsing_description = false;
            continue;
        }
        else if (line == "description:") {
            parsing_description = true;
            current_map_ptr = nullptr; 
            continue;
        }

        // when parsing_description becomes true, it means that same line and next lines are part of the description, so they will be add here and that is it.
        if (parsing_description) {
            description += line + "\n";
            continue;
        }

        // find colon
        size_t colonPos = line.find(':');
        if (colonPos != std::string::npos) {
            std::string key = line.substr(0, colonPos);
            std::string value = line.substr(colonPos + 1);

            // if we need to save to a map
            if (current_map_ptr != nullptr) {
                (*current_map_ptr)[key] = value;
            } 
            // Otherwise, we are parsing the main headers (team names, time, event name)
            else {
                if (key == "team a") {
                    team_a_name = value;
                }
                else if (key == "team b") {
                    team_b_name = value;
                }
                else if (key == "event name") {
                    name = value;
                }
                else if (key == "time") {
                    try {
                        time = std::stoi(value);
                    } catch (...) {
                        time = 0; // if missing
                    }
                }

            }
        }
    }
}

names_and_events parseEventsFile(std::string json_path)
{
    std::ifstream f(json_path);
    json data = json::parse(f);

    std::string team_a_name = data["team a"];
    std::string team_b_name = data["team b"];

    // run over all the events and convert them to Event objects
    std::vector<Event> events;
    for (auto &event : data["events"])
    {
        std::string name = event["event name"];
        int time = event["time"];
        std::string description = event["description"];
        std::map<std::string, std::string> game_updates;
        std::map<std::string, std::string> team_a_updates;
        std::map<std::string, std::string> team_b_updates;
        for (auto &update : event["general game updates"].items())
        {
            if (update.value().is_string())
                game_updates[update.key()] = update.value();
            else
                game_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team a updates"].items())
        {
            if (update.value().is_string())
                team_a_updates[update.key()] = update.value();
            else
                team_a_updates[update.key()] = update.value().dump();
        }

        for (auto &update : event["team b updates"].items())
        {
            if (update.value().is_string())
                team_b_updates[update.key()] = update.value();
            else
                team_b_updates[update.key()] = update.value().dump();
        }
        
        events.push_back(Event(team_a_name, team_b_name, name, time, game_updates, team_a_updates, team_b_updates, description));
    }
    names_and_events events_and_names{team_a_name, team_b_name, events};

    return events_and_names;
}