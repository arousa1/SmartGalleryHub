package com.yupi.yupicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // 生效范围，针对方法的注解
@Retention(RetentionPolicy.RUNTIME) // 生效时间，运行时
public @interface AuthCheck {

    /**
     * 必须具有某个角色
     */
    String mustRole() default "";
}
