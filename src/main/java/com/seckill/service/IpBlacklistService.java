package com.seckill.service;

/**
 * IP黑名单服务接口
 */
public interface IpBlacklistService {

    /**
     * 检查IP是否在黑名单中
     *
     * @param ip IP地址
     * @return true-在黑名单中，false-不在黑名单中
     */
    boolean isBlacklisted(String ip);

    /**
     * 将IP添加到黑名单
     *
     * @param ip IP地址
     * @param reason 加入黑名单的原因
     */
    void addToBlacklist(String ip, String reason);

    /**
     * 从黑名单中移除IP
     *
     * @param ip IP地址
     */
    void removeFromBlacklist(String ip);

    /**
     * 记录IP的恶意行为（多次恶意行为后自动加入黑名单）
     *
     * @param ip     IP地址
     * @param action 恶意行为类型
     */
    void recordMaliciousAction(String ip, String action);
}