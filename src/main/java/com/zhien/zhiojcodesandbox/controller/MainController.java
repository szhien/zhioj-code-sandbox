package com.zhien.zhiojcodesandbox.controller;

import com.zhien.zhiojcodesandbox.JavaNativeCodeSandbox;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeRequest;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Zhien
 * @version 1.0
 * @name MainController
 * @description
 * @createDate 2024/11/12 08:46
 */
@RestController("/")
public class MainController {

    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @GetMapping("/health")
    public String index() {
        return "Hello, World!";
    }

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest) {
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
