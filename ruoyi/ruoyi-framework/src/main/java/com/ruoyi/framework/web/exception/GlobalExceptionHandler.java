package com.ruoyi.framework.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.DemoModeException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.html.EscapeUtil;

/**
 * 全局异常处理器
 * 
 * @author ruoyi
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 权限校验异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public AjaxResult handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',权限校验失败'{}'", requestURI, e.getMessage());
        return AjaxResult.error(HttpStatus.FORBIDDEN, "没有权限，请联系管理员授权");
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public AjaxResult handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return AjaxResult.error(e.getMessage());
    }

    /**
     * 缺少上传文件。
     * 例：POST /admin/attendee/import 未带 file 表单域。
     *
     * RuoYi 默认没有这个分支，会落到通用 Exception 处理器，用户只能看到
     * 「系统繁忙，请稍后重试」，无法判断是自己少传了东西还是服务端出了问题。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public AjaxResult handleMissingPart(MissingServletRequestPartException e, HttpServletRequest request)
    {
        String part = e.getRequestPartName();
        log.warn("请求地址'{}',缺少上传内容'{}'", request.getRequestURI(), part);
        return AjaxResult.error("file".equals(part) ? "请选择要上传的文件" : "缺少必要的上传内容：" + part);
    }

    /**
     * 缺少必填的请求参数。例：GET /app/screen/status 未带 version。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public AjaxResult handleMissingParameter(MissingServletRequestParameterException e, HttpServletRequest request)
    {
        log.warn("请求地址'{}',缺少请求参数'{}'", request.getRequestURI(), e.getParameterName());
        return AjaxResult.error("缺少必要参数：" + e.getParameterName());
    }

    /**
     * 请求体无法解析：JSON 语法错误，或 POST/PUT 根本没有携带 body。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public AjaxResult handleUnreadableBody(HttpMessageNotReadableException e, HttpServletRequest request)
    {
        log.warn("请求地址'{}',请求体无法解析：{}", request.getRequestURI(), e.getMessage());
        return AjaxResult.error("请求体格式错误：请确认提交的是合法 JSON，且 POST/PUT 请求带有 body");
    }

    /**
     * 把非 multipart 请求打到了需要上传文件的接口
     * （例如 Content-Type 写成了 application/json）。
     */
    @ExceptionHandler(MultipartException.class)
    public AjaxResult handleMultipart(MultipartException e, HttpServletRequest request)
    {
        log.warn("请求地址'{}',非 multipart 请求：{}", request.getRequestURI(), e.getMessage());
        return AjaxResult.error("请以 multipart/form-data 方式上传文件");
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e, HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        Integer code = e.getCode();
        return StringUtils.isNotNull(code) ? AjaxResult.error(code, e.getMessage()) : AjaxResult.error(e.getMessage());
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public AjaxResult handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求路径中缺少必需的路径变量'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        String value = Convert.toStr(e.getValue());
        if (StringUtils.isNotEmpty(value))
        {
            value = EscapeUtil.clean(value);
        }
        log.error("请求参数类型不匹配'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error(String.format("请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'", e.getName(), e.getRequiredType().getName(), value));
    }

    /**
     * 拦截未知的运行时异常
     */
    /**
     * 没有匹配到任何接口或静态资源。
     * Spring 6 起这类请求抛 NoResourceFoundException，若不单独处理会被下面的
     * RuntimeException/Exception 兜底成「系统繁忙」（500），
     * 前端把 URL 拼错会被误判成服务端故障，排查时白绕一大圈。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public AjaxResult handleNoResourceFound(NoResourceFoundException e)
    {
        return AjaxResult.error(HttpStatus.NOT_FOUND, "接口不存在：" + e.getResourcePath());
    }

    /**
     * 未知运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        // 不回传 e.getMessage()：其中可能带 SQL 片段、表名、内部类名等实现细节，
        // 等于给探测者免费提供情报。细节只保留在上面的服务端日志（含堆栈）。
        return AjaxResult.error("系统繁忙，请稍后重试");
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        return AjaxResult.error("系统繁忙，请稍后重试");
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e)
    {
        log.error(e.getMessage(), e);
        String message = e.getAllErrors().get(0).getDefaultMessage();
        return AjaxResult.error(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        log.error(e.getMessage(), e);
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        return AjaxResult.error(message);
    }

    /**
     * 演示模式异常
     */
    @ExceptionHandler(DemoModeException.class)
    public AjaxResult handleDemoModeException(DemoModeException e)
    {
        return AjaxResult.error("演示模式，不允许操作");
    }
}
