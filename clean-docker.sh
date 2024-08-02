#! /bin/bash
echo "Cleaning all related Docker data. This may take a moment..."
echo "Trying to stop running containers..."
docker stop "$(docker ps -a -q --filter "ancestor=pwm-eval")"
echo "Removing pwm-eval image..."
docker image rm pwm-eval
echo "Removing pwm-eval containers..."
docker container rm "$(docker ps -a -q --filter "ancestor=pwm-eval")"
echo "...done."
