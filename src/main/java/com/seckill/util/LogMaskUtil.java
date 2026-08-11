package com.seckill.util;

import java.util.regex.Pattern;

/**
 * 日志脱敏工具类
 */
public class LogMaskUtil {

    // 手机号正则：匹配11位手机号
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d{9})");
    // 身份证号正则：匹配18位身份证号
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("([1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx])");
    // 密码正则：匹配"password":"xxx"格式
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(\"password\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE);
    // token正则：匹配"token":"xxx"格式
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\"token\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE);

    /**
     * 对日志内容进行脱敏处理
     *
     * @param content 原始内容
     * @return 脱敏后的内容
     */
    public static String mask(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String masked = content;

        // 1. 手机号脱敏：保留前3位和后4位，中间用*代替
        masked = PHONE_PATTERN.matcher(masked).replaceAll(match -> {
            String phone = match.group(1);
            return phone.substring(0, 3) + "****" + phone.substring(7);
        });

        // 2. 身份证号脱敏：保留前6位和后4位，中间用*代替
        masked = ID_CARD_PATTERN.matcher(masked).replaceAll(match -> {
            String idCard = match.group(1);
            return idCard.substring(0, 6) + "********" + idCard.substring(14);
        });

        // 3. 密码脱敏：替换为******
        masked = PASSWORD_PATTERN.matcher(masked).replaceAll("$1******$2");

        // 4. token脱敏：只保留前10位
        masked = TOKEN_PATTERN.matcher(masked).replaceAll(match -> {
            String token = match.group(2);
            if (token.length() > 10) {
                return "\"token\":\"" + token.substring(0, 10) + "...\"";
            }
            return "\"token\":\"" + token + "\"";
        });

        return masked;
    }
}