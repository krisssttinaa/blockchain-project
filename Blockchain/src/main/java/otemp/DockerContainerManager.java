package otemp;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.PullImageResultCallback;

import java.io.IOException;

public class DockerContainerManager {

    private final DockerClient dockerClient;

    public DockerContainerManager() {
        this.dockerClient = DockerClientBuilder.getInstance().build();
    }

    public void pullImage(String imageName) {
        System.out.println("Pulling Docker image: " + imageName);
        dockerClient.pullImageCmd(imageName).exec(new PullImageResultCallback()).awaitSuccess();
    }

    public void createAndStartContainer(String imageName, String containerName, int hostPort, int containerPort) {
        System.out.println("Creating and starting Docker container with image: " + imageName);
        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withExposedPorts(ExposedPort.tcp(containerPort))
                .withHostConfig(HostConfig.newHostConfig().withPortBindings(PortBinding.parse(String.format("%d:%d", hostPort, containerPort))));

        CreateContainerResponse container = createContainerCmd.exec();
        String containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();

        System.out.println("Docker container started successfully. Container ID: " + containerId);
    }

    public void stopAndRemoveContainer(String containerId) {
        System.out.println("Stopping Docker container: " + containerId);
        dockerClient.stopContainerCmd(containerId).exec();

        System.out.println("Removing Docker container: " + containerId);
        dockerClient.removeContainerCmd(containerId).exec();
    }

    public void close() {
        try {
            dockerClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}