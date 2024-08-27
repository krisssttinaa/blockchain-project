FROM openjdk:21-jdk-slim

WORKDIR /app

# Copy the unshaded JAR file into the Docker container
COPY target/Blockchain-1.0-SNAPSHOT.jar /app/Blockchain.jar

# Command to run your application
ENTRYPOINT ["java", "-jar", "/app/Blockchain.jar"]
