package com.zhien.zhiojcodesandbox;

import com.zhien.zhiojcodesandbox.model.ExecuteCodeRequest;
import com.zhien.zhiojcodesandbox.model.ExecuteCodeResponse;

/**
 * @author Zhien
 * @version 1.0
 * @name CodeSandbox
 * @description 代码沙箱接口
 * @createDate 2024/11/08 16:25
 */
public interface CodeSandbox {
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
