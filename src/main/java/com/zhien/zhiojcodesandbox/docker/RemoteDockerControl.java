package com.zhien.zhiojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;


/**
 * @author Zhien
 * @version 1.0
 * @name RemoteDockerControl
 * @description 远程控制Docker
 * @createDate 2024/11/21 20:27
 */
public class RemoteDockerControl {
    /**
     * @param args
     * @return void
     * @name main
     * @description TODO
     * @author Zhien
     * @createDate 2024/11/21 20:27
     */
    public static void main(String[] args) {
        // Docker 守护进程地址 unix://var/run/docker.sock
        String dockerHost = "tcp://192.168.88.128:2375";
        // 配置 Docker 客户端
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                // 默认
//                .withRegistryUrl("https://index.docker.io/v1/")
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
        String image = "mysql:latest"; // Nginx:latest
//        PullImageCmd pushImageCmd = dockerClient.pullImageCmd(image);
//        PullImageResultCallback resultCallback = new PullImageResultCallback() {
//
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("下载镜像：" + item.getStatus());
//                super.onNext(item);
//            }
//        };
//        try {
//            pushImageCmd
//                    .exec(resultCallback)
//                    .awaitCompletion();
//            System.out.println("镜像下载完成");
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }

        // 创建容器
        CreateContainerResponse exec = dockerClient.createContainerCmd(image)
                .withCmd("echo", "Hello, Docker!")
                .exec();
        System.out.println("容器创建成功：" + exec.getId());

        // 启动容器
        dockerClient.startContainerCmd(exec.getId()).exec();

        //查看容器状态
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
        for (Container container : containerList) {
            System.out.println(container);
        }

        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println(item.getStreamType());
                System.out.println("日志：" + new String(item.getPayload()));
                super.onNext(item);
            }
        };
        // 查看日志
        try {
            dockerClient.logContainerCmd(exec.getId())
                    .withStdErr(true)
                    .withStdOut(true)
                    .exec(logContainerResultCallback)
                    .awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 停止容器
        dockerClient.stopContainerCmd(exec.getId()).exec();


        // 删除容器 withForce强制删除
        dockerClient.removeContainerCmd(exec.getId()).withForce(true).exec();


    }
}
