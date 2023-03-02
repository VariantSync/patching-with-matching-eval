#! /bin/bash
echo "Stopping Docker container. This will take a moment..."
docker stop "$(docker ps -a -q --filter "ancestor=sync-study")"
echo "...done."
