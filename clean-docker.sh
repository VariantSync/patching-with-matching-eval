#! /bin/bash
echo "Cleaning all related Docker data. This may take a moment..."
echo "Trying to stop running containers..."
docker stop "$(docker ps -a -q --filter "ancestor=sync-study")"
echo "Removing sync-study image..."
docker image rm sync-study
echo "Removing sync-study containers..."
docker container rm "$(docker ps -a -q --filter "ancestor=sync-study")"
echo "...done."
