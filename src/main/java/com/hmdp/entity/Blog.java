package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;


/*
entity对象呢，里面存放的就是数据库里的表，完完整整的表，全部字段必须包含
 */
@Data
@EqualsAndHashCode(callSuper = false) //仅使用当前类的字段进行比较
@Accessors(chain = true)//生成 ​链式调用的 Setter 方法（每个 setXxx() 返回 this）。
@TableName("tb_blog")//(mybatis-plus) 指定该类的数据库表名，默认情况下，MyBatis-Plus 会将类名转换为下划线形式做为表名,此处是显示指定

public class Blog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long userId;

    //这里加了三个不存在于表中的数据项
    @TableField(exist = false)
    private String icon;

    @TableField(exist = false)
    private String name;

    @TableField(exist = false)
    private Boolean isLike;

    private String title;

    private String images;

    private String content;

    private Integer liked;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
