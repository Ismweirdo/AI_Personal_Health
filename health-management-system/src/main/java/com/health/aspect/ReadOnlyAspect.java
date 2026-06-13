package com.health.aspect;

import com.health.config.DataSourceContextHolder;
import com.health.config.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 读写分离切面
 * 根据方法注解自动切换数据源
 */
@Slf4j
@Aspect
@Component
@Order(2)
public class ReadOnlyAspect {

    /**
     * 环绕通知，对标记了@ReadOnly注解的方法使用从库
     */
    @Around("@annotation(com.health.annotation.ReadOnly)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // 切换到从数据源
            DataSourceContextHolder.setDataSourceType(DataSourceType.SLAVE);
            log.debug("Switch to slave datasource for method: {}", joinPoint.getSignature().getName());
            
            // 执行方法
            return joinPoint.proceed();
        } finally {
            // 清除数据源类型
            DataSourceContextHolder.clearDataSourceType();
            log.debug("Clear datasource context for method: {}", joinPoint.getSignature().getName());
        }
    }
}