#! /bin/bash
echo "Starting $1"
docker run --rm -v "$(pwd)/simulation-files/":"/home/user/simulation-files" sync-study "$@"

echo "Done."
