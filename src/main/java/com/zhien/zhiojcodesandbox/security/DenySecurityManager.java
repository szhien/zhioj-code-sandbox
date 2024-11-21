package com.zhien.zhiojcodesandbox.security;

import java.security.Permission;

/**
 * @name MySecurity
 * @description 所有权限拒绝
 * @author Zhien 
 * @createDate 2024/11/20 15:30
 * @version 1.0
 */
public class DenySecurityManager extends SecurityManager{

    @Override
    public void checkPermission(Permission perm) {
        throw new SecurityException("权限异常：" + perm.toString());
    }
}
