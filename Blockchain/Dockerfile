FROM openjdk:21-jdk

WORKDIR /app

# Create directory if it doesn't exist
RUN mkdir -p /app

# Copy the JAR file into the container
COPY out/artifacts/Blockchain_jar/Blockchain.jar /app/Blockchain.jar

# Set the entry point to run the JAR file
CMD ["java", "-jar", "/app/Blockchain.jar"]