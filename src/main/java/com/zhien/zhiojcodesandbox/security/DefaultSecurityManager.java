package com.zhien.zhiojcodesandbox.security;

import java.security.Permission;

/**
 * @name MySecurity
 * @description 自定义我的默认安全管理器
 * @author Zhien 
 * @createDate 2024/11/20 15:30
 * @version 1.0
 */
public class DefaultSecurityManager extends SecurityManager{

    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何限制");
        System.out.println(perm);
        // super.checkPermission(perm);  // 不调用就是不做任何限制
    }
}
