@echo "Cleaning all related Docker data. This may take a moment..."

@echo "Trying to stop running containers..."
@FOR /f "tokens=*" %%i IN ('docker ps -a -q --filter "ancestor=sync-study"') DO docker stop %%i

@echo "Removing sync-study image..."
docker image rm sync-study

@echo "Removing sync-study containers..."
@FOR /f "tokens=*" %%i IN ('docker ps -a -q --filter "ancestor=sync-study"') DO docker container rm %%i

@echo "...done."
