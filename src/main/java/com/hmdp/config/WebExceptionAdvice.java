package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
/*
自动捕获控制器层（@Controller/@RestController）抛出的异常，集中处理所有异常，避免重复的 try-catch 代码。
自动将返回值序列化为 JSON/XML，确保所有响应格式一致。
 */
public class WebExceptionAdvice {
    //使用了@ExceptionHandler注解，指定处理RuntimeException类型的异常
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e){
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
    /*
    <Result>
      <code>500</code>
      <msg>服务器异常</msg>
      <data/>
    </Result>
     */
}
