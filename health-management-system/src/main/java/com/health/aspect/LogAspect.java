package com.health.aspect;

import com.health.annotation.Log;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    @Around("@annotation(com.health.annotation.Log)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        
        // Execute method
        Object result = point.proceed();
        
        // Save log
        saveLog(point, System.currentTimeMillis() - beginTime);
        
        return result;
    }

    private void saveLog(ProceedingJoinPoint joinPoint, long time) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        Object[] args = joinPoint.getArgs();
        
        log.info("Operation: {} | Time: {}ms | Method: {}.{} | Args: {} | IP: {}", 
                logAnnotation != null ? logAnnotation.value() : "unknown", 
                time, className, methodName, Arrays.toString(args), 
                request != null ? request.getRemoteAddr() : "unknown");
    }
}
