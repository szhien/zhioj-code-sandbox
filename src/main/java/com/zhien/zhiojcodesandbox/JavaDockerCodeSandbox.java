package com.zhien.zhiojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeRequest;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeResponse;
import com.zhien.zhiojcodesandbox.model.ExecuteMessage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Zhien
 * @version 1.0
 * @name JavaDockerCodeSandbox
 * @description Java Docker代码沙箱实现:模版方法实现
 * @createDate 2024/11/29 16:59
 */
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {
    //超时时间
    private static final Long TIME_OUT = 5000L;

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // Docker 守护进程地址 unix://var/run/docker.sock
        String dockerHost = "tcp://192.168.88.128:2375"; //在Linux服务器上运行其实不用指定，默认就是使用自己的docker
        // 配置 Docker 客户端
        DefaultDockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
        String image = "openjdk:8-alpine"; // openjdk:8-alpine
//        if (!FIRST_CREATE) {
//            PullImageCmd pushImageCmd = dockerClient.pullImageCmd(image);
//            PullImageResultCallback resultCallback = new PullImageResultCallback() {
//                @Override
//                public void onNext(PullResponseItem item) {
//                    System.out.println("下载镜像：" + item.getStatus());
//                    super.onNext(item);
//                }
//            };
//            try {
//                pushImageCmd
//                        .exec(resultCallback)
//                        .awaitCompletion();
//                System.out.println("镜像下载完成");
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }
        // 创建容器
        String profileConfig = ResourceUtil.readUtf8Str("profile.json");  // 读取自定义的Linux的内核安全功能配置的json文件，用于在创建容器时在hostConfig中引入
        HostConfig hostConfig = new HostConfig();
        hostConfig.withCpuCount(1L)
                .withMemory(100 * 1024 * 1024L) // 512MB
                .withMemorySwap(0L)  // 内存和磁盘之间交换
                .withCpuCount(1L)
                .withSecurityOpts(Arrays.asList("seccomp=" + profileConfig))  //Linux 自带的一些安全管理措施，seccomp（Secure Computing Mode）是一个用于 Linux 内核的安全功能
                .setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        // 绑定宿主机目录到容器目录
        CreateContainerResponse containerResponse = dockerClient.createContainerCmd(image)
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true) //不开启网络
                .withReadonlyRootfs(true) //限制不能再根目录写文件，只能读
                .withAttachStdin(true)  // 附加标准输入
                .withAttachStdout(true) // 附加标准输出
                .withAttachStderr(true) // 附加标准错误输出
                .withTty(true)  // 交互式
                .exec();
        String containerId = containerResponse.getId();
        System.out.println("容器创建成功：" + containerId);
        //开启容器
        dockerClient.startContainerCmd(containerId).exec();

        // 输出信息集合
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        // 在容器中执行命令 docker exec containerId[containerName] java -cp /app Main 1 3
        for (String inputArg : inputList) {
            String[] inputArgArray = inputArg.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgArray);
            ExecCreateCmdResponse cmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdout(true)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .exec();
            String execId = cmdResponse.getId();
            if (execId == null) {
                throw new RuntimeException("执行命令失败");
            }

            //在执行命令之前就先启动，统计当前容器的执行状态信息，例如cpu占用，内存占用等
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> resultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    Long usageMemory = statistics.getMemoryStats().getUsage();
                    System.out.println("内存占用：" + usageMemory);
                    maxMemory[0] = Math.max(maxMemory[0], usageMemory == null ? 0L : usageMemory);
                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            };
            statsCmd.exec(resultCallback);

            // 计时器，计时程序执行时间
            StopWatch stopWatch = new StopWatch();
            ExecuteMessage executeMessage = new ExecuteMessage();
            // 设置超时标记位，记录表示，程序执行是否超时,默认超时
            final boolean[] time_out = {true};
            // 执行命令，获取执行结果
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {

                // 在程序执行完成的时候会触发执行此方法，如果在规定的超时时间中完成，说明没超时，可以将time_out置为false，否则此方法不会执行，time_out还是为true
                @Override
                public void onComplete() {
                    time_out[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    String executeResult = new String(frame.getPayload());
                    if (StreamType.STDERR.equals(streamType)) {
                        executeMessage.setErrorMessage(executeResult);
                        System.out.println("输出错误结果：" + executeResult);

                    } else {
                        executeMessage.setMessage(executeResult);
                        System.out.println("输出结果：" + executeResult);
                    }
                    super.onNext(frame);
                }
            };
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS); //设置超时时间，超过超时时间，程序跳过，继续执行后面的步骤。TODO 问题：怎么识别到是否超时，毕竟不管超时不超时都会继续执行
                stopWatch.stop();
                statsCmd.close(); //记得关闭监控统计，在程序执行结束的时候
                long taskTimeMillis = stopWatch.getLastTaskTimeMillis();  //获取程序执行耗时
                executeMessage.setExecuteTime(taskTimeMillis);  // 设置执行耗时
                executeMessage.setUsageMemory(maxMemory[0]);   // 设置占用内存
            } catch (InterruptedException e) {
                System.out.println("执行命令失败：" + e.getMessage());
                throw new RuntimeException(e);
            }
            //输出结果信息添加到列表中
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}

