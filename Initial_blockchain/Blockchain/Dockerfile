# Use the official Corretto 17 base image
FROM amazoncorretto:17

# Set the working directory inside the container
WORKDIR /app

# Copy the source files to the container
COPY src /app/src

# Compile Java code
RUN javac -d /app/out /app/src/*.java

# Run the application
CMD ["java", "-cp", "/app/out", "Main"]
