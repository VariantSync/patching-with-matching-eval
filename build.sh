#! /bin/bash
docker build --network=host --build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) -t sync-study .
