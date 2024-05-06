FROM --platform=linux/amd64 openjdk:19-alpine

# Build the jar files
WORKDIR /home/user
COPY src ./src
COPY local-maven-repo local-maven-repo
COPY *gradle.kts ./
COPY gradlew ./
COPY gradle gradle
RUN ./gradlew Cherries || exit
RUN ./gradlew SyncStudy || exit
RUN ./gradlew SyncStudyAnalysis || exit
RUN ./gradlew CherriesAnalysis || exit

FROM --platform=linux/amd64 openjdk:19-alpine

RUN apk update
RUN apk add --no-cache --upgrade bash diffutils patch git python3 py3-matplotlib unzip

ARG GROUP_ID
ARG USER_ID

# Create a group and a user
RUN addgroup -g $GROUP_ID user
RUN adduser --disabled-password -G user -u $USER_ID --home /home/user --gecos '' user
WORKDIR /home/user

# Copy the docker resources
COPY docker/* ./

# Copy all relevant files from the previous stage
COPY --from=0 /home/user/build/libs/* ./

# Adjust permissions
RUN chown user:user /home/user -R
RUN chmod +x run-simulation.sh
RUN chmod +x entrypoint.sh

ENTRYPOINT ["./entrypoint.sh", "./run-simulation.sh"]
USER user
