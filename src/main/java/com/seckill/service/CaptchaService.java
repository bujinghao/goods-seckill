package com.seckill.service;

/**
 * 验证码服务接口
 */
public interface CaptchaService {

    /**
     * 验证验证码是否正确
     *
     * @param captchaKey 验证码key（从Redis获取）
     * @param captcha    用户输入的验证码
     * @return true-正确，false-错误
     */
    boolean verifyCaptcha(String captchaKey, String captcha);
}