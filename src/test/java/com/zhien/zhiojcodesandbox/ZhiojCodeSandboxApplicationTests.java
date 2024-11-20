package com.zhien.zhiojcodesandbox;

import com.zhien.zhiojcodesandbox.model.ExecuteCodeRequest;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ZhiojCodeSandboxApplicationTests {

    @Test
    void executeCode() {
        String userDir = System.getProperty("user.dir");  //项目所在根目录: D:\Projects\java\OJ\zhioj-code-sandbox
        System.out.println(userDir);
    }

}
