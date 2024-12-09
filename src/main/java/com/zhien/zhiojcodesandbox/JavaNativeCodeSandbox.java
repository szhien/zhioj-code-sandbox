package com.zhien.zhiojcodesandbox;


import com.zhien.zhiojcodesandbox.model.ExecuteCodeRequest;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

/**
 * @author Zhien
 * @version 1.0
 * @name JavaNativeCodeSandbox
 * @description Java 原生代码沙箱实现:模版方法实现
 * @createDate 2024/11/29 16:25
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
