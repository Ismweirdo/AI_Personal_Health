package com.health.annotation;

import java.lang.annotation.*;

/**
 * 只读注解
 * 标记此注解的方法将使用从数据库进行查询
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReadOnly {
}