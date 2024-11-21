package com.zhien.zhiojcodesandbox.security;

import java.security.Permission;

/**
 * @name MySecurity
 * @description 自定义安全管理器
 * @author Zhien 
 * @createDate 2024/11/20 15:30
 * @version 1.0
 * 怎么使用？网上的使用方式：在启动类中添加 -Djava.security.manager -Djava.security.policy=security.policy
 * 1. 创建一个MySecurityManager类，继承SecurityManager，重写checkPermission方法
 * 2. 在需要使用安全校验的类中使用 System.setSecurityManager(new MySecurityManager());
 * 3. 为了不让JavaNativeCodeSandbox的executeCode()每次执行代码时，都选择设置一次,将MySecurityManager类提前收到执行命令编译为.class存放在resources目录下的security目录中,移除类的包名
 * 4. 程序运行执行的命令中添加  -Djava.security.manager=MySecurityManager
 * 即：java -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=MySecurityManager Main
 * 第2个 %s : 是MySecurityManager.class 文件所在目录, 即 D:\Projects\java\OJ\zhioj-code-sandbox\src\main\resources\security
 *
 */
public class MySecurityManager extends SecurityManager{

    @Override
    public void checkPermission(Permission perm) {
        //super.checkPermission(perm); //不使用父类的校验规则
    }

    @Override
    public void checkRead(String file) {
        System.out.println(file);
        if(file.contains("D:\\Projects\\java\\OJ\\zhioj-code-sandbox")){
            return;
        }
//        throw new SecurityException("checkRead 权限异常：" + file);
    }

    @Override
    public void checkWrite(String file) {
        throw new SecurityException("checkWrite 权限异常：" + file);
    }

    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("checkExec 权限异常：" + cmd);
    }

    @Override
    public void checkDelete(String file) {
        throw new SecurityException("checkDelete 权限异常：" + file);
    }

    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("checkConnect 权限异常：" + host + ":" + port);
    }
}
