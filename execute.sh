#! /bin/bash
echo "Starting $1"
docker run --rm -v "$(pwd)/results/":"/home/user/results" sync-study "$@"

echo "Done."
