package com.hmdp.dto;

import lombok.Data;

/*
关于DTO对象，这个东西包括即将返回给前端的，也包括从前端获得的，总之是要传输的对象
 */
/*
登陆的时候，前端传回来的json数据对应的类对象
 */

/*
自动生成getter setter方法，自动生成无参数构造器（需配合 @NoArgsConstructor）。自动生成equals和hashcode方法，自动生成字符串表示类名(字段1=值, 字段2=值...)
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
