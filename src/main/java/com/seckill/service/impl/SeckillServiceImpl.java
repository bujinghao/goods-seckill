package com.seckill.service.impl;

import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.service.SeckillService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillServiceImpl implements SeckillService {

    private final Logger log = LoggerFactory.getLogger(SeckillServiceImpl.class);

    private final SeckillGoodsMapper goodsMapper;
    private final SeckillOrderMapper orderMapper;

    public SeckillServiceImpl(SeckillGoodsMapper goodsMapper, SeckillOrderMapper orderMapper) {
        this.goodsMapper = goodsMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * @Autowired 是 Spring 框架注解，先按类型后按名称匹配；@Resource 是 Java 标准注解，先按名称后按类型匹配。
     * 
     *      来源与匹配策略
     *      来源不同‌：@Autowired 来自 Spring
     *      框架（org.springframework.beans.factory.annotation），@Resource 来自
     *      JSR-250 规范（javax.annotation/jakarta.annotation）。
     *      匹配顺序‌：@Autowired 默认按类型（byType）查找，有多个时再按名称匹配；@Resource
     *      默认按名称（byName）查找，找不到再按类型匹配。
     *      指定名称‌：@Autowired 需配合@Qualifier 注解指定名称，@Resource 可直接用 name
     *      属性指定。‌‌‌
     *      支持位置与属性
     *      适用位置‌：@Autowired 支持构造器、方法、参数、字段注入；@Resource 仅支持字段和方法注入，不支持构造器。
     *      required 属性‌：@Autowired 支持 required=false 允许注入失败不报错，@Resource
     *      不支持该属性，注入失败直接抛异常。‌‌‌
     */
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<SeckillGoods> listGoods() {
        return goodsMapper.selectAll();
    }

    @Override
    public SeckillGoods getGoodsById(Long id) {
        // 先查询缓存中的商品信息；===建议： 秒杀场景下，库存和状态应该 直接查数据库 ，只缓存不常变化的商品信息。
        SeckillGoods goods = (SeckillGoods) redisTemplate.opsForValue().get("goods:" + id);
        if(goods != null){
            return goods;
        }
        // 判断redis服务是否正常，若异常则打印日志并且降级使用数据库查询
        if(redisTemplate.getConnectionFactory().getConnection().isClosed()){
            log.error("Redis服务异常，使用数据库查询商品信息");
            goods = goodsMapper.selectById(id);
        }else{
        // synchronized(this){
            // 再次查询缓存，确保缓存中的数据是最新的，避免缓存穿透，双重检查锁
            goods = (SeckillGoods) redisTemplate.opsForValue().get("goods:" + id);
            if(goods != null){
                return goods;
            }else{
                // 从数据库查询商品信息, 并放到缓存
                goods = goodsMapper.selectById(id);
                redisTemplate.opsForValue().set("goods:" + id, goods, 60 * 5, TimeUnit.SECONDS);
            }
        // }
        }
        return goods;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrder doSeckill(Long userId, Long goodsId) {
        // 1. 查询商品是否存在，从缓存中获取商品信息
        SeckillGoods goods = getGoodsById(goodsId);
        if (goods == null) {
            throw new RuntimeException("商品不存在");
        }

        // 2. 校验秒杀时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(goods.getStartTime())) {
            throw new RuntimeException("秒杀尚未开始");
        }
        if (now.isAfter(goods.getEndTime())) {
            throw new RuntimeException("秒杀已经结束");
        }

        // 3. 校验是否重复下单（同一用户同一商品只能买一次）
        int orderCount = orderMapper.countByUserAndGoods(userId, goodsId);
        if (orderCount >= goods.getStockCountPerUser()) {
            throw new RuntimeException("已达到限购数量，不能重复购买");
        }

        // 4. 扣减库存（数据库层面防止超卖）
        // 查询数据库库存, 并更新缓存（扣减成功），影响行数为1
        int affected = goodsMapper.decreaseStock(goodsId);
        if (affected == 0) {
            throw new RuntimeException("库存不足，秒杀失败");
        }else{
            // 删除缓存中的商品信息
            redisTemplate.delete("goods:" + goodsId);
            // 扣减成功，更新缓存
            redisTemplate.opsForValue().set("goods:" + goodsId, goods, 60 * 5, TimeUnit.SECONDS);
        }

        // 5. 创建订单
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(generateOrderNo(userId));
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setGoodsName(goods.getGoodsName());
        order.setSeckillPrice(goods.getSeckillPrice());
        order.setCount(1);
        order.setTotalPrice(goods.getSeckillPrice().multiply(BigDecimal.ONE));
        order.setStatus(0); // 待支付
        orderMapper.insert(order);

        return order;
    }

    /**
     * 秒杀方法重载，引入Redis实现库存预扣·减，解决数据库压力问题
     * <p>
     * 流程：Redis DECR 原子预减库存 → 成功则继续下单 → 失败直接返回"已售罄"
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrder doSeckillWithRedis(Long userId, Long goodsId) {
        // Redis库存Key
        String stockKey = "seckill:stock:" + goodsId;

        // 1. Redis原子预减库存
        Long stock = redisTemplate.opsForValue().decrement(stockKey);
        if (stock == null) {
            // Redis中没有库存缓存，降级到数据库
            log.warn("Redis中无库存缓存，降级到数据库扣减: goodsId={}", goodsId);
            return doSeckill(userId, goodsId);
        }

        if (stock < 0) {
            // 库存不足，回补并返回
            redisTemplate.opsForValue().increment(stockKey);
            throw new RuntimeException("已售罄");
        }

        try {
            // 2. 查询商品信息（包含时间校验）
            SeckillGoods goods = getGoodsById(goodsId);
            if (goods == null) {
                throw new RuntimeException("商品不存在");
            }

            // 3. 校验秒杀时间
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(goods.getStartTime())) {
                throw new RuntimeException("秒杀尚未开始");
            }
            if (now.isAfter(goods.getEndTime())) {
                throw new RuntimeException("秒杀已经结束");
            }

            // 4. 校验是否重复下单
            int orderCount = orderMapper.countByUserAndGoods(userId, goodsId);
            if (orderCount >= goods.getStockCountPerUser()) {
                throw new RuntimeException("已达到限购数量，不能重复购买");
            }

            // 5. 扣减数据库库存（最终兜底）
            int affected = goodsMapper.decreaseStock(goodsId);
            if (affected == 0) {
                throw new RuntimeException("库存不足，秒杀失败");
            }

            // 6. 创建订单
            SeckillOrder order = new SeckillOrder();
            order.setOrderNo(generateOrderNo(userId));
            order.setUserId(userId);
            order.setGoodsId(goodsId);
            order.setGoodsName(goods.getGoodsName());
            order.setSeckillPrice(goods.getSeckillPrice());
            order.setCount(1);
            order.setTotalPrice(goods.getSeckillPrice().multiply(BigDecimal.ONE));
            order.setStatus(0);
            orderMapper.insert(order);

            log.info("秒杀成功: userId={}, goodsId={}, orderNo={}", userId, goodsId, order.getOrderNo());
            return order;

        } catch (Exception e) {
            // 发生异常，回补Redis库存
            redisTemplate.opsForValue().increment(stockKey);
            log.error("秒杀失败，回补库存: goodsId={}, error={}", goodsId, e.getMessage());
            throw e;
        }
    }

    /**
     * 生成订单编号：时间戳 + 用户ID后4位 + 随机UUID片段
     */
    private String generateOrderNo(Long userId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String userSuffix = String.format("%04d", userId % 10000);
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return timestamp + userSuffix + random;
    }
}
