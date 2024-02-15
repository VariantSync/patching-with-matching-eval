#! /bin/bash
docker build --network=host --build-arg USER_ID=$(id -u ${SUDO_USER:-$USER}) --build-arg GROUP_ID=$(id -g ${SUDO_USER:-$USER}) -t extraction .
