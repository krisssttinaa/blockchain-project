# Use OpenJDK base image
FROM openjdk:21-jdk-slim

# Set the working directory
WORKDIR /app

# Install essential tools like ps, top, and optionally htop
# - procps: includes ps, top
# - htop: optional, a more visual process viewer
# - curl, net-tools, iproute2: for network diagnostics and testing
RUN apt-get update && \
    apt-get install -y procps htop curl net-tools iproute2 && \
    rm -rf /var/lib/apt/lists/*

# (Optional) Remove any unnecessary data/cache if such directories exist
RUN rm -rf /data/cache/ /var/cache/*

# Copy the JAR file into the Docker container
COPY target/Blockchain-1.0-SNAPSHOT.jar /app/Blockchain.jar

# Command to run your application
ENTRYPOINT ["java", "-jar", "/app/Blockchain.jar"]