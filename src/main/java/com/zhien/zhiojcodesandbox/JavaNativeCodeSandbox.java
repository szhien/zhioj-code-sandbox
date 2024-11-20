package com.zhien.zhiojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeRequest;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeResponse;
import com.zhien.zhiojcodesandbox.model.ExecuteMessage;
import com.zhien.zhiojcodesandbox.model.JudgeInfo;
import com.zhien.zhiojcodesandbox.utils.ProcessUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @author Zhien
 * @version 1.0
 * @name JavaNativeCodeSandbox
 * @description Java 原生代码沙箱实现
 * @createDate 2024/11/08 16:25
 * 实现原理：将原代码使用javac编译工具进行编译成.class字节码文件，然后使用java命令执行该字节码文件，获得最终的结果。
 * 其中，除了可以拿到最终的运行结果，还能获取成功与否，运行信息，错误信息等
 * 核心依赖：Java 进程类 Process
 * 1.把用户的代码保存为文件
 * 2.编译代码，得到 class 文件
 * 3.执行代码，得到输出结果
 * 4.收集整理输出结果
 * 5.文件清理，释放空间
 * 6.错误处理，提升程序健壮性
 * 先编写示例代码，注意要去掉包名，放到 resources 目录下：
 */
public class JavaNativeCodeSandbox implements CodeSandbox {
    public static void main(String[] args) {
        CodeSandbox codeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
//        String code = ResourceUtil.readStr("testCode/InterMain.java", StandardCharsets.UTF_8);
        //测试不安全代码
//        String code = ResourceUtil.readStr("unsafeCode/SleepError.java", StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("unsafeCode/OutOfMemoryError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setInputList(Arrays.asList("1 2", "2 3"));
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse = " + executeCodeResponse);
    }


    // 全局代码存放根目录
    private static final String GLOBAL_CODE_ROOT_DIR_NAME = "src/main/resources/tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

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
        Process compileProcess = null;
        try {
            compileProcess = Runtime.getRuntime().exec(compileCmd);
            // 获取编译执行结果
            ProcessUtils.runProcessAndGetMessage(compileProcess, "Compile");

        } catch (IOException e) {
            return getErrorExecuteCodeResponse(e);
        }

        // 3.执行代码，得到输出结果
        Process runProcess = null;
        ExecuteMessage runExecuteMessage = null;
        // 输出信息集合
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            // String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main", userCodeParentPath); //交互式执行
            try {
                // 在终端开始执行命令：
                runProcess = Runtime.getRuntime().exec(runCmd);
                // 等待并获取执行结果
                runExecuteMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "Run");
                //  runExecuteMessage = ProcessUtils.runInterProcessAndGetMessage(runProcess, inputArgs); //交互式执行
                executeMessageList.add(runExecuteMessage);
            } catch (IOException e) {
                return getErrorExecuteCodeResponse(e);
            }
        }
        //4.收集整理输出结果
        List<String> outputList = new ArrayList<>();
        // 记录最大执行时间
        long max_time = 0L;
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
        }

        if (executeMessageList.size() == outputList.size()) {
            executeCodeResponse.setStatus(1); //执行成功
        }
        JudgeInfo judgeInfo = new JudgeInfo();
        // judgeInfo.setMessage();
        // judgeInfo.setMemory();
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
