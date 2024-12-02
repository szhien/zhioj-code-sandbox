package com.zhien.zhiojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeRequest;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeResponse;
import com.zhien.zhiojcodesandbox.model.ExecuteMessage;
import com.zhien.zhiojcodesandbox.model.JudgeInfo;
import com.zhien.zhiojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Zhien
 * @version 1.0
 * @name JavaCodeSandboxTemplate
 * @description 代码沙箱模板方法实现
 * @createDate 2024/12/02 08:50
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {
    // 全局代码存放根目录
    private static final String GLOBAL_CODE_ROOT_DIR_NAME = "src" + File.separator + "main" + File.separator + "resources" + File.separator + "tempCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    //超时时间
    private static final Long TIME_OUT = 5000L;


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();
        String code = executeCodeRequest.getCode();

        //1.把用户的代码保存为文件
        File userCodeFile = saveCodeFile(code);

        // 2.编译代码，得到 class 文件
        ExecuteMessage compileCodeFileExecuteMessage = compileCodeFile(userCodeFile);
        log.info("编译执行结果: {}", compileCodeFileExecuteMessage);
        if (compileCodeFileExecuteMessage.getExitValue() != 0) {
            log.error("编译执行错误：{}", compileCodeFileExecuteMessage.getErrorMessage());
            return getErrorExecuteCodeResponse(new RuntimeException("编译执行错误：" + compileCodeFileExecuteMessage.getErrorMessage()));
        }

        // 3.执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);
        //4.收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

        //5.文件清理，释放空间
        boolean del = deleteCodeFile(userCodeFile);
        log.warn("删除{},userCodeFilePath = {}", del ? "成功" : "失败", userCodeFile.getAbsolutePath());
        return executeCodeResponse;
    }

    /**
     * 1.保存用户代码文件
     *
     * @param code
     * @return
     */
    public File saveCodeFile(String code) {

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
        return userCodeFile;
    }

    /**
     * 2.编译代码
     *
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileCodeFile(File userCodeFile) {
        // 使用 Process 类在终端执行命令：
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            // 获取编译执行结果
            return ProcessUtils.runProcessAndGetMessage(compileProcess, "Compile");
        } catch (IOException e) {
            throw new RuntimeException("编译代码时系统运行时异常：" + e.getMessage());
        }
    }

    /**
     * 3.执行代码，获取运行结果
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */

    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        // 输出信息集合
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        File userCodeParentPath = userCodeFile.getParentFile().getAbsoluteFile();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                // 在终端开始执行命令：
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("超时了，中断");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                // 等待并获取执行结果
                ExecuteMessage runExecuteMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "Run");
                //  runExecuteMessage = ProcessUtils.runInterProcessAndGetMessage(runProcess, inputArgs); //交互式执行
                executeMessageList.add(runExecuteMessage);
            } catch (IOException e) {
//                return getErrorExecuteCodeResponse(e);
                throw new RuntimeException("执行代码时系统运行时异常：" + e.getMessage());
            }
        }
        return executeMessageList;
    }


    /**
     * 4.收集整理执行输出结果
     *
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
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
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        // judgeInfo.setMessage();
        // judgeInfo.setMemory();
        judgeInfo.setTime(max_time);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        return executeCodeResponse;
    }

    /**
     * 5.文件清理，释放空间
     *
     * @param userCodeFile
     */
    public boolean deleteCodeFile(File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        if (FileUtil.exist(userCodeParentPath)) {
            return FileUtil.del(userCodeParentPath);
        }
        return true;
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
