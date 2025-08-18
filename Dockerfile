FROM alpine:latest

RUN apk update
RUN apk add --no-cache --upgrade openjdk21
# Build the jar files
WORKDIR /home/user
COPY src ./src
COPY local-maven-repo local-maven-repo
COPY *gradle.kts ./
COPY gradlew ./
COPY gradle gradle

# Build the evaluation
WORKDIR /home/user
RUN ./gradlew Cherries || exit
RUN ./gradlew Composition || exit

FROM alpine:latest

RUN apk update
# Install dependencies for unix patch
RUN apk add --no-cache --upgrade bash diffutils patch git python3 py3-matplotlib unzip openjdk21

# Install dependencies for patching with matching
RUN apk add --no-cache curl bash gcc musl-dev

ARG GROUP_ID
ARG USER_ID

# Create a group and a user
RUN addgroup -g $GROUP_ID user
RUN adduser --disabled-password -G user -u $USER_ID --home /home/user --gecos '' user
WORKDIR /home/user

# Copy mpatch
COPY mpatch ./mpatch

# Copy the docker resources
COPY docker/* ./

# Copy all relevant files from the previous stage
COPY --from=0 /home/user/build/libs/* ./

# Adjust permissions
RUN chown user:user /home/user -R
RUN chmod +x run-simulation.sh
RUN chmod +x entrypoint.sh

ENTRYPOINT ["./entrypoint.sh", "./run-simulation.sh"]

# Install Rust and mpatch for the new user
USER user

RUN curl https://sh.rustup.rs -sSf | sh -s -- -y \
    && source $HOME/.cargo/env
# Set the PATH
ENV PATH="/home/user/.cargo/bin:${PATH}"
# Set the Rust toolchain to stable (or nightly if preferred)
RUN rustup default stable
# RUN rustup default nightly

RUN cargo install --path /home/user/mpatch
