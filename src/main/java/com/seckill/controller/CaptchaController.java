package com.seckill.controller;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.seckill.dto.Result;
import com.seckill.service.impl.CaptchaServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 验证码控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class CaptchaController {

    @Autowired
    private DefaultKaptcha captchaProducer;

    @Autowired
    private CaptchaServiceImpl captchaService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${captcha.expiration:60000}")
    private Long captchaExpiration;

    /**
     * 生成验证码图片（返回Base64编码）
     */
    @GetMapping("/captcha")
    public Result<Map<String, String>> generateCaptcha(HttpServletResponse response) {
        try {
            // 生成验证码文本
            String captchaText = captchaProducer.createText();

            // 生成验证码key
            String captchaKey = captchaService.generateCaptchaKey();

            // 将验证码存储到Redis（60秒过期）
            redisTemplate.opsForValue().set(captchaKey, captchaText, captchaExpiration, TimeUnit.MILLISECONDS);

            // 生成验证码图片
            BufferedImage image = captchaProducer.createImage(captchaText);

            // 将图片转换为Base64编码
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", outputStream);
            String base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            // 返回验证码key和Base64图片
            Map<String, String> result = new HashMap<>();
            result.put("captchaKey", captchaKey);
            result.put("captchaImage", "data:image/jpeg;base64," + base64Image);

            log.info("生成验证码成功: key={}, captcha={}", captchaKey, captchaText);
            return Result.success(result);
        } catch (Exception e) {
            log.error("生成验证码失败", e);
            return Result.error("生成验证码失败");
        }
    }
}