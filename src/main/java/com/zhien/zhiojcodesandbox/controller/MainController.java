package com.zhien.zhiojcodesandbox.controller;

import com.zhien.zhiojcodesandbox.JavaNativeCodeSandbox;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeRequest;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Zhien
 * @version 1.0
 * @name MainController
 * @description
 * @createDate 2024/11/12 08:46
 */
@RestController("/")
public class MainController {

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";


    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @GetMapping("/health")
    public String index() {
        return "Hello, World!";
    }

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        //如果请求秘钥不满足，响应异常
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }

}
