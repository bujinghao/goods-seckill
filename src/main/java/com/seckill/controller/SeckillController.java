package com.seckill.controller;

import com.seckill.dto.Result;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.service.SeckillService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final SeckillService seckillService;
    private final RedisTemplate<String, Object> redisTemplate;

    public SeckillController(SeckillService seckillService, RedisTemplate<String, Object> redisTemplate) {
        this.seckillService = seckillService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询所有秒杀商品
     */
    @GetMapping("/query")
    public Result<List<SeckillGoods>> listGoods() {
        List<SeckillGoods> goodsList = seckillService.listGoods();
        return Result.success(goodsList);
    }

    /**
     * 查询秒杀商品详情
     */
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
     * @param userId 用户ID（阶段一暂用参数传入，后续阶段改用JWT解析）
     * @param goodsId 商品ID
     */
    @PostMapping("/{goodsId}/do")
    public Result<SeckillOrder> doSeckill(
            @RequestParam Long userId,
            @PathVariable Long goodsId) {
        try {
            SeckillOrder order = seckillService.doSeckill(userId, goodsId);
            return Result.success(order);
        } catch (Exception e) {
            log.error("秒杀失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 清除商品缓存（用于解决序列化配置变更后的类型转换问题）
     */
    @DeleteMapping("/cache")
    public Result<String> clearCache() {
        var keys = redisTemplate.keys("goods:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            return Result.success("已清除 " + keys.size() + " 个缓存");
        }
        return Result.success("无缓存需要清除");
    }
}
