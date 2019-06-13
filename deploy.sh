#!/bin/bash

# install my three applications
cd ~/Desktop/SDN-final-project/project6-0413335/ && ./deploy.sh
cd ~/Desktop/SDN-final-project/project7-0413335/ && ./deploy.sh
cd ~/Desktop/SDN-final-project/project8-0413335/ && ./deploy.sh

# send configurations
cd ~/Desktop/SDN-final-project/project6-0413335/ && ./send.sh
