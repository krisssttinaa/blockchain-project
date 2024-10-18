# Blockchain Project

## Overview

This project is a basic blockchain implementation where multiple nodes communicate over a peer-to-peer (P2P) network. Each node maintains its own copy of the blockchain, and nodes exchange blocks and transactions to stay synchronized. The project supports mining, transaction validation, and consensus resolution through fork detection.

---

## Prerequisites

To build and run the project, ensure you have the following installed:

- **Java 8+**: Required to compile the Java code.
- **Maven**: To build the project and manage dependencies.
- **Docker**: To containerize and run multiple blockchain nodes.

---

## Project Structure

The repository is organized as follows:

```plaintext
blockchain-project/Blockchain
├── Dockerfile                   # Docker configuration for the blockchain node
├── pom.xml                      # Maven configuration file for project dependencies
├── src/                         # Source code of the blockchain node
├── target/                      # Compiled files and outputs from Maven
└── dependency-reduced-pom.xml    # Auto-generated POM file (after Maven build)
```
---

## Build Instructions

### Clone the repository:
```plaintext
git clone <repository_url>
cd blockchain-project
cd Blockchain
```
### Build the project with Maven:
Run the following command to build the project and generate the necessary JAR files:
```plaintext
mvn clean package
```
### Build the Docker image:
```plaintext
docker build -t blockchain-node .
```
### Running the Blockchain Nodes

Step 1: Create a Docker Network
Create a Docker network to allow nodes to communicate with each other:
```plaintext
docker network create blockchain-network
```

Step 2: Start the First Node
Run the first blockchain node (node0) on port 7777:
```plaintext
docker run -it --name node0 -p 7777:7777 --network blockchain-network blockchain-node
```

Step 3: Start Additional Nodes
Open a new terminal and navigate to the Blockchain directory
To start more nodes, you can repeat the docker run command, changing the container name and exposed port for each new node. For example, for node1, use port 7778:
```plaintext
docker run -it --name node1 -p 7778:7777 --network blockchain-network blockchain-node
```
You can continue this process for as many nodes as you'd like, incrementing the port number for each new node.

### Stopping and Cleaning Up
To stop and remove a running container, you can use the following commands:

Stop a container:
```plaintext
docker stop node0
```
Remove a container:
```plaintext
docker rm node0
```
