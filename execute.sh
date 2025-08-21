#! /bin/bash
echo "Starting $1"
docker run --rm -v "$(pwd)/evaluation-workdir/":"/home/user/evaluation-workdir" mpatch-reproduction "$@"

echo "Done."
