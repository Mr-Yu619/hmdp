package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

/*
里面有一个ThreadLocal对象
我可以调用这个类来实现存取和删除
这个value存放在一个map里了，根据线程可以拿到entry对象，然后这个entry的value
就是value值
 */

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
