package com.zhien.zhiojcodesandbox.controller;

import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/health")
    public String index() {
        return "Hello, World!";
    }
}
