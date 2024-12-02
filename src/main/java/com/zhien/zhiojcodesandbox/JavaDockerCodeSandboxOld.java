package com.zhien.zhiojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
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
import com.zhien.zhiojcodesandbox.model.JudgeInfo;
import com.zhien.zhiojcodesandbox.utils.ProcessUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Zhien
 * @version 1.0
 * @name JavaDockerCodeSandbox
 * @description Java Docker代码沙箱实现
 * @createDate 2024/11/08 16:25
 */
public class JavaDockerCodeSandboxOld implements CodeSandbox {
    // 全局代码存放根目录
    private static final String GLOBAL_CODE_ROOT_DIR_NAME = "src" + File.separator + "main" + File.separator + "resources" + File.separator + "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    //超时时间
    private static final Long TIME_OUT = 5000L;

    //第一次创建容器
    private static final Boolean FIRST_CREATE = true;

    public static void main(String[] args) {
        CodeSandbox codeSandbox = new JavaDockerCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/Main.java", StandardCharsets.UTF_8);
        //测试不安全代码
//        String code = ResourceUtil.readStr("unsafeCode/SleepError.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("unsafeCode/OutOfMemoryError.java", StandardCharsets.UTF_8);
//        String code = ResourceUtil.readStr("unsafeCode/FileLeakageError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setInputList(Arrays.asList("1 2", "2 3"));
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse = " + executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        //1.把用户的代码保存为文件
        String userDir = System.getProperty("user.dir");
        String globalCodeRootFullPath = userDir + File.separator + GLOBAL_CODE_ROOT_DIR_NAME;  // File.separator是文件路径分隔符,可以适配多系统
        // 判断全局代码目录是否存在，没有则新建
        if (!FileUtil.exist(globalCodeRootFullPath)) {
            FileUtil.mkdir(globalCodeRootFullPath);
        }
        // 把用户的代码隔离存放，就是每次提交都是一个新的文件夹存放
        String userCodeParentPath = globalCodeRootFullPath + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        // 把用户的代码写入文件
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        System.out.println(userCodeFile.getAbsolutePath());
        // 2.编译代码，得到 class 文件
        // 使用 Process 类在终端执行命令：
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            // 获取编译执行结果
            ProcessUtils.runProcessAndGetMessage(compileProcess, "Compile");
        } catch (IOException e) {
            return getErrorExecuteCodeResponse(e);
        }

        // 3.创建容器，上传编译文件：创建一个交互式的容器，能接受多次输入并且输出。节省性能，我们不会每个测试用例都单独创建一个容器，每个容器只执行一次 java 命令
        // Docker 守护进程地址 unix://var/run/docker.sock
        String dockerHost = "tcp://192.168.88.128:2375";
        // 配置 Docker 客户端
        DefaultDockerClientConfig config = DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
        String image = "openjdk:8-alpine"; // openjdk:8-alpine
        if (!FIRST_CREATE) {
            PullImageCmd pushImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback resultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pushImageCmd
                        .exec(resultCallback)
                        .awaitCompletion();
                System.out.println("镜像下载完成");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // 创建容器
        String profileConfig = ResourceUtil.readUtf8Str("profile.json");
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

        //4.收集整理输出结果
        List<String> outputList = new ArrayList<>();
        // 记录最大执行时间
        long max_time = 0L;
        //记录最大占用内存
        long max_memory = 0L;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 用户代码执行失败
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            // 记录最大执行时间
            max_time = Math.max(max_time, executeMessage.getExecuteTime());
            // 记录最大占用内存
            max_memory = Math.max(max_memory, executeMessage.getUsageMemory());
        }

        if (executeMessageList.size() == outputList.size()) {
            executeCodeResponse.setStatus(1); //执行成功
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
//        judgeInfo.setMessage();
        judgeInfo.setMemory(max_memory);
        judgeInfo.setTime(max_time);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        //5.文件清理，释放空间
        if (FileUtil.exist(userCodeParentPath)) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
    }

    /**
     * 获取错误执行结果
     *
     * @param e
     * @return
     */
    public ExecuteCodeResponse getErrorExecuteCodeResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatus(2); //编译期间出现的错误
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}

