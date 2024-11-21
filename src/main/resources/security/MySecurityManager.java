import java.security.Permission;

/**
 * @name MySecurity
 * @description 自定义安全管理器
 * @author Zhien
 * @createDate 2024/11/20 15:30
 * @version 1.0
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

