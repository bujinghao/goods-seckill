package com.seckill.controller;

import com.seckill.annotation.OperationLog;
import com.seckill.annotation.RateLimit;
import com.seckill.dto.Result;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.service.CaptchaService;
import com.seckill.service.SeckillPathService;
import com.seckill.service.SeckillService;
import com.seckill.util.SignatureUtil;
import com.seckill.util.UserContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀控制器
 */
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private static final Logger log = LoggerFactory.getLogger(SeckillController.class);

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillPathService seckillPathService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 查询所有秒杀商品
     */
    @OperationLog(operation = "QUERY_GOODS", description = "查询所有秒杀商品")
    @GetMapping("/query")
    public Result<List<SeckillGoods>> listGoods() {
        List<SeckillGoods> goodsList = seckillService.listGoods();
        return Result.success(goodsList);
    }

    /**
     * 查询秒杀商品详情
     */
    @OperationLog(operation = "QUERY_GOODS_DETAIL", description = "查询秒杀商品详情")
    @GetMapping("/{id}")
    public Result<SeckillGoods> getGoods(@PathVariable Long id) {
        SeckillGoods goods = seckillService.getGoodsById(id);
        if (goods == null) {
            return Result.error("商品不存在");
        }
        return Result.success(goods);
    }

    /**
     * 执行秒杀
     * 
     * @param userId  用户ID（阶段一暂用参数传入，后续阶段改用JWT解析）
     * @param goodsId 商品ID
     */
    @OperationLog(operation = "DO_SECKILL", description = "执行秒杀")
    @PostMapping("/{goodsId}/do")
    public Result<SeckillOrder> doSeckill(
            @RequestParam Long userId,
            @PathVariable Long goodsId) {
        try {
            SeckillOrder order = seckillService.doSeckill(userId, goodsId);
            return Result.success(order);
        } catch (Exception e) {
            log.error("秒杀失败: {}", goodsId, e);
            return Result.error(e.getMessage());
        }
    }

    // /**
    // * 执行秒杀（使用Redis原子预减库存）
    // */
    // @PostMapping("/{goodsId}/do/redis")
    // public Result<SeckillOrder> doSeckillWithRedis(
    // @RequestParam Long userId,
    // @PathVariable Long goodsId) {
    // try {
    // SeckillOrder order = seckillService.doSeckillWithRedis(userId, goodsId);
    // return Result.success(order);
    // } catch (Exception e) {
    // log.error("Redis秒杀失败，秒杀失败: {}", goodsId, e);
    // return Result.error(e.getMessage());
    // }
    // }

    /**
     * 执行秒杀（使用Lua脚本原子预减库存）
     */
    @OperationLog(operation = "DO_SECKILL_LUA", description = "执行秒杀（使用Lua脚本原子预减库存）")
    @RateLimit(globalQps = 1000, userQps = 1, message = "系统繁忙，请稍后再试")
    @PostMapping("/{goodsId}/do/lua")
    public Result<SeckillOrder> doSeckillWithLua(
            @RequestParam Long userId,
            @PathVariable Long goodsId) {
        try {
            SeckillOrder order = seckillService.doSeckillWithLua(userId, goodsId);
            return Result.success(order);
        } catch (Exception e) {
            log.error("Lua秒杀失败: {}", goodsId, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 执行秒杀（异步处理，MQ削峰）
     * 推荐用于高并发场景
     *
     * 流程：
     * 1. Redis Lua脚本原子扣减库存
     * 2. 发送消息到RabbitMQ队列
     * 3. 立即返回"排队中"
     * 4. MQ消费者异步处理数据库操作
     *
     * 限流配置：
     * - 全局限流：200 QPS（匹配数据库连接池大小）
     * - 用户限流：1 QPS（防止刷单）
     */
    @OperationLog(operation = "DO_SECKILL_ASYNC", description = "执行秒杀（异步处理，MQ削峰）")
    @RateLimit(globalQps = 1000, userQps = 1, message = "系统繁忙，请稍后再试")
    @PostMapping("/{goodsId}/do/async")
    public Result<String> doSeckillAsync(
            @RequestParam Long userId,
            @PathVariable Long goodsId) {
        try {
            String message = seckillService.doSeckillAsync(userId, goodsId);
            return Result.success(message);
        } catch (Exception e) {
            log.error("异步秒杀失败: {}", goodsId, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询订单状态（用于前端轮询）
     * 用户提交秒杀请求后，前端轮询此接口检查订单是否已生成
     */
    @GetMapping("/order/check")
    public Result<SeckillOrder> checkOrder(@RequestParam Long userId, @RequestParam Long goodsId) {
        try {
            SeckillOrder order = seckillService.getOrderByUserAndGoods(userId, goodsId);
            if (order != null) {
                return Result.success(order);
            }
            return Result.error("订单尚未生成");
        } catch (Exception e) {
            log.error("查询订单状态失败: userId={}, goodsId={}", userId, goodsId, e);
            return Result.error("查询订单失败");
        }
    }

    /**
     * 清除商品缓存（用于解决序列化配置变更后的类型转换问题）
     * 可使用定时任务在秒杀完成后自动清除缓存
     */
    @DeleteMapping("/cache")
    public Result<String> clearCache() {
        // var keys = redisTemplate.keys("goods:*");
        // 清除所有seckill开头的缓存，包括秒杀库存、订单状态、动态URL、限购数量、用户消息等； 
        // 注意：提前清除缓存，会影响秒杀性能
        var keys = redisTemplate.keys("seckill:*"); // 以后只是用来登录就不需要清除或者修改缓存key格式
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            return Result.success("已清除 " + keys.size() + " 个缓存");
        }
        return Result.success("无缓存需要清除");
    }

    /**
     * 获取秒杀动态URL
     * 用户必须先通过验证码验证后才能获取动态URL
     * 
     * @param goodsId 商品ID
     * @return 动态URL path部分
     */
    @GetMapping("/path")
    public Result<String> getSeckillPath(@RequestParam Long goodsId, @RequestParam String captchaKey,
            @RequestParam String captchaText) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 验证码验证逻辑
        if (!captchaService.verifyCaptcha(captchaKey, captchaText)) {
            return Result.error("验证码错误");
        }

        String path = seckillPathService.generateSeckillPath(userId, goodsId);
        return Result.success(path);
    }

    /**
     * 执行秒杀（使用动态URL）
     * 
     * @param path    动态URL的hash部分
     * @param goodsId 商品ID
     */
    @OperationLog(operation = "EXECUTE_SECKILL", description = "执行秒杀（使用动态URL）")
    @RateLimit(globalQps = 1000, userQps = 1, message = "系统繁忙，请稍后再试")
    @PostMapping("/{path}/{goodsId}/execute")
    public Result<String> executeSeckill(
            @PathVariable String path,
            @PathVariable Long goodsId) {
        try {
            // 1. 获取当前用户ID
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }

            // 2. 验证动态URL
            if (!seckillPathService.verifySeckillPath(userId, goodsId, path)) {
                return Result.error("动态URL无效，属于非法请求");
            }

            // // 3. 验证签名（新增）防止参数篡改和重放攻击
            // if (!SignatureUtil.verifySignature(userId, goodsId, timestamp, signature)) {
            // log.warn("签名验证失败: userId={}, goodsId={}, timestamp={}", userId, goodsId,
            // timestamp);
            // // 记录恶意IP（新增）
            // String ip = getClientIp();
            // ipBlacklistService.recordMaliciousAction(ip,
            // "SIGNATURE_VERIFICATION_FAILED");
            // return Result.error("签名验证失败");
            // }

            // 3. 执行秒杀
            String message = seckillService.doSeckillAsync(userId, goodsId);
            return Result.success(message);
        } catch (Exception e) {
            log.error("动态URL秒杀失败: goodsId={}", goodsId, e);
            return Result.error(e.getMessage());
        }
    }
}
