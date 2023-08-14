FROM --platform=linux/amd64 openjdk:19-alpine

# Build the jar files
WORKDIR /home/user
COPY src ./src
COPY local-maven-repo local-maven-repo
COPY *gradle.kts ./
COPY gradlew ./
COPY gradle gradle
RUN ./gradlew Experiment || exit
RUN ./gradlew Evaluation || exit

FROM --platform=linux/amd64 openjdk:19-alpine

RUN apk update
RUN apk add --no-cache --upgrade bash diffutils patch git python3 py3-matplotlib unzip
# Create a user
RUN adduser --disabled-password  --home /home/user --gecos '' user
WORKDIR /home/user

# Copy the docker resources
COPY docker/* ./
COPY plots ./plots

# Copy all relevant files from the previous stage
COPY --from=0 /home/user/build/libs/* ./

# Adjust permissions
RUN chown user:user /home/user -R
RUN chmod +x run-simulation.sh
RUN chmod +x entrypoint.sh

ENTRYPOINT ["./entrypoint.sh", "./run-simulation.sh"]
USER user
