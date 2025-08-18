#! /bin/bash
echo "Cleaning all related Docker data. This may take a moment..."
echo "Trying to stop running containers..."
docker stop "$(docker ps -a -q --filter "ancestor=mpatch-reproduction")"
echo "Removing mpatch image..."
docker image rm mpatch-reproduction
echo "Removing mpatch containers..."
docker container rm "$(docker ps -a -q --filter "ancestor=mpatch-reproduction")"
echo "...done."
