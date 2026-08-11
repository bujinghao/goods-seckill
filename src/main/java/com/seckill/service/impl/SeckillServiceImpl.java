package com.seckill.service.impl;

import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.mq.SeckillMessageProducer;
import com.seckill.service.SeckillService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
    // 用户秒杀状态缓存键：存在则表示用户已完成秒杀，并且达到秒杀数量上限
    private final String SECKILL_USER_STATUS_KEY = "seckill:user:status:%s:%s";
    // seckill:user:status:{userId}:{goodsId}

    @Autowired
    private SeckillMessageProducer messageProducer;

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
     *      匹配顺序‌：
     *      @Autowired 默认按类型（byType）查找，有多个时再按名称匹配；
     *      @Resource默认按名称（byName）查找，找不到再按类型匹配。
     *      指定名称‌：@Autowired 需配合@Qualifier 注解指定名称，@Resource 可直接用 name
     *      属性指定。‌‌‌
     * 
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
            synchronized(this){
                // 再次查询缓存，确保缓存中的数据是最新的，避免缓存穿透，双重检查锁
                goods = (SeckillGoods) redisTemplate.opsForValue().get("goods:" + id);
                if(goods != null){
                    return goods;
                }else{
                    // 从数据库查询商品信息, 并放到缓存
                    goods = goodsMapper.selectById(id);
                    redisTemplate.opsForValue().set("goods:" + id, goods, 60 * 5, TimeUnit.SECONDS);
                }
            }
        }
        return goods;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrder doSeckill(Long userId, Long goodsId) {
        SeckillOrder order = validateAndCreateOrder(userId, goodsId);
        return order;
    }

    // /**
    //  * 秒杀方法重载，引入Redis实现库存预扣·减，解决数据库压力问题
    //  * <p>
    //  * 流程：Redis DECR 原子预减库存 → 成功则继续下单 → 失败直接返回"已售罄"
    //  * 存在数据一致性问题，比如redis宕机
    //  * </p>
    //  */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrder doSeckillWithRedis(Long userId, Long goodsId) {

    //     // Redis库存Key
    //     String stockKey = "seckill:stock:" + goodsId;

    //     // 1. Redis原子预减库存
    //     Long stock = redisTemplate.opsForValue().decrement(stockKey);
    //     if (stock == null) {
    //         stock = preheatStock(stockKey, goodsId);
    //         if(stock == null){
    //             // Redis中没有库存缓存，降级到数据库
    //             log.warn("Redis中无库存缓存，降级到数据库扣减: goodsId={}", goodsId);
    //             return doSeckill(userId, goodsId);
    //         }
    //     }

    //     if (stock < 0) {
    //         // 库存不足，回补并返回
    //         redisTemplate.opsForValue().increment(stockKey);
    //         throw new RuntimeException("已售罄");
    //     }

    //     try {
    //         // 2. 校验并创建订单
    //         return validateAndCreateOrder(userId, goodsId);

    //     } catch (Exception e) {
    //         // 发生异常，回补Redis库存
    //         redisTemplate.opsForValue().increment(stockKey);
    //         log.error("秒杀失败，回补库存: goodsId={}, error={}", goodsId, e.getMessage());
    //         throw e;
    //     }
        return null;
    }

    // 预热库存不存在时，从数据库查询库存并预热到Redis缓存
    public Long preheatStock(String stockKey, Long goodsId) {
        // 从数据库查询库存
        SeckillGoods goods = goodsMapper.selectById(goodsId);
        if(goods != null){
            long stock = goods.getStockCount();
            if(stock <= 0){
                return null;
            }
            // 预热库存到Redis缓存
            redisTemplate.opsForValue().set(stockKey, stock);
            return stock;
        }
        return null;
    }

    /**
     * 秒杀方法 - 使用Redis + Lua脚本实现原子性库存扣减
     * <p>
     * Lua脚本优势：
     * 1. 原子性执行，避免竞态条件
     * 2. 减少网络开销，一次网络请求完成检查+扣减
     * 3. 无需额外的分布式锁
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrder doSeckillWithLua(Long userId, Long goodsId) {
        // 校验用户是否已达到限购数量，防止网络重试、重复点击购买
        String userStatusKey = String.format(SECKILL_USER_STATUS_KEY, userId, goodsId);
        if (redisTemplate.hasKey(userStatusKey)) {
            throw new RuntimeException("已达到限购数量，不能重复购买");
        }

        // Redis库存Key
        String stockKey = "seckill:stock:" + goodsId;
        // 从Redis缓存中获取库存值
        Object stockObj = redisTemplate.opsForValue().get(stockKey);
        if (stockObj == null) {// Redis缓存中没有库存缓存，预热库存
            preheatStock(stockKey, goodsId);
        }

        // Lua脚本：检查库存并原子扣减
        // 返回值：>0=扣减成功（返回扣减前库存），0=库存不足，-1=库存Key不存在
        //
        /* 【Lua脚本基础语法】
        *    -- 注释以 -- 开头
        *    -- 变量声明：local 变量名 = 值（local表示局部变量）
        *    -- 条件判断：if 条件 then ... end
        *    -- Redis在Lua中提供两个特殊数组：KEYS[] 和 ARGV[]
        *       KEYS：存储脚本执行时传入的Key参数（KEYS[1]表示第一个Key）
        *       ARGV：存储脚本执行时传入的额外参数（ARGV[1]表示第一个额外参数）
        *
        *    【为什么使用Lua脚本？】
        *    1. 原子性：整个脚本作为一个整体执行，不会被其他命令插队
        *    2. 减少网络开销：一次网络请求完成"检查库存 + 扣减库存"，无需两次往返
        *    3. 无需分布式锁：避免了"先GET判断再DECR"的竞态条件
        *
        *    【脚本执行流程】
        *    Java端调用：redisTemplate.execute(redisScript, List.of(stockKey))
        *               → 传入参数：KEYS[1] = stockKey
        *    Redis端执行：
        *               → 获取库存值
        *               → 判断是否为nil（Key不存在）
        *               → 判断库存是否<=0
        *               → 执行DECR扣减并返回扣减前库存（>0）
        */
        String luaScript = """
                -- 声明局部变量stockKey，从KEYS数组获取第一个参数（库存Key）
                local stockKey = KEYS[1]

                -- 调用Redis的GET命令获取库存值，tonumber()将字符串转为数字
                -- redis.call(命令名, 参数...) 用于在Lua中执行Redis命令
                local stock = tonumber(redis.call('GET', stockKey))

                -- 如果stock为nil，说明Key不存在（可能未预热或已过期）
                if stock == nil then
                    return -1  -- 返回-1表示Key不存在
                end

                -- 如果库存<=0，说明已售罄
                if stock <= 0 then
                    return 0  -- 返回0表示库存不足，Java端会抛出"已售罄"异常
                end

                -- 库存充足，执行DECR命令原子扣减
                redis.call('DECR', stockKey)
                -- 返回扣减前的库存值（>0），避免与"库存不足"的返回值混淆
                return stock
                """;


        // 创建Redis脚本对象
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(luaScript);
        redisScript.setResultType(Long.class);

        // 执行Lua脚本（需捕捉Redis异常，降级到数据库）
        Long result;
        try {
            result = redisTemplate.execute(redisScript, List.of(stockKey));
        } catch (Exception e) {
            // Redis异常，降级到数据库扣减
            log.error("Redis执行Lua脚本异常，降级到数据库: goodsId={}, error={}", goodsId, e.getMessage());
            return doSeckill(userId, goodsId);
        }

        if (result == null || result == -1) {
            throw new RuntimeException("库存Key不存在");
        }else if(result == 0){
            throw new RuntimeException("已售罄");
        }

        try {
            // Lua扣减成功，创建订单
            return validateAndCreateOrder(userId, goodsId);
        } catch (Exception e) {
            // 失败时回补库存
            redisTemplate.opsForValue().increment(stockKey);
            log.error("秒杀失败，回补库存: goodsId={}, error={}", goodsId, e.getMessage());
            throw e;
        }
    }

    /**
     * 检验并创建订单
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 订单对象
     * 
     * 后续优化，批量处理订单创建，避免数据库压力；一些校验以及缓存处理放到秒杀接口中
     */
    @Override
    public SeckillOrder validateAndCreateOrder(Long userId, Long goodsId) {

        String userBoughtKey = "seckill:user:bought:" + userId + ":" + goodsId;
        Integer limitPerUser = 0; // 限购数量
    
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
        limitPerUser = goods.getStockCountPerUser();
        Integer boughtCount = (Integer) redisTemplate.opsForValue().get(userBoughtKey);
        if (boughtCount == null) {
            // 首次购买，从数据库查询历史订单
            boughtCount = orderMapper.countByUserAndGoods(userId, goodsId);
            if (boughtCount > 0) {
                redisTemplate.opsForValue().set(userBoughtKey, boughtCount);
            }
        }

        if (boughtCount >= limitPerUser) {
            // 已达到限购数量，设置用户状态为已购买
            redisTemplate.opsForValue().set(String.format(SECKILL_USER_STATUS_KEY, userId, goodsId), 1);
            throw new RuntimeException("已达到限购数量，不能重复购买");
        }

        // 5. 扣减数据库库存
        int affected = goodsMapper.decreaseStock(goodsId);
        if (affected == 0) {
            throw new RuntimeException("库存不足，秒杀失败");
        }else{
            // 秒杀商品库存变化频繁，直接删除缓存，不重建
            redisTemplate.delete("goods:" + goodsId);
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

        // 创建订单成功后，原子性增加用户已购数量
        redisTemplate.opsForValue().increment(userBoughtKey);
        // // 设置过期时间（秒杀活动结束后自动清理）
        // redisTemplate.expire(userBoughtKey, 7, TimeUnit.DAYS);
        return order;
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

    /**
     * 异步秒杀（Redis预减 + MQ异步处理）
     * 核心优化：流量削峰，将瞬时高并发请求平滑到数据库可承受的范围
     *
     * 流程：
     * 1. Redis Lua脚本原子扣减库存
     * 2. 发送消息到RabbitMQ队列
     * 3. 立即返回"排队中"
     * 4. MQ消费者异步处理数据库操作
     *
     * 队列满时的处理（Overflow.REJECT_PUBLISH）：
     * - 生产者会收到 confirmCallback（ack=false）
     * - 需要立即回补Redis库存
     * - 返回用户"系统繁忙"提示
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return "排队中"提示
     */
    @Override
    public String doSeckillAsync(Long userId, Long goodsId) {
        // 校验用户是否已达到限购数量，防止网络重试、重复点击购买
        String userStatusKey = String.format(SECKILL_USER_STATUS_KEY, userId, goodsId);
        if (redisTemplate.hasKey(userStatusKey)) {
            throw new RuntimeException("已达到限购数量，不能重复购买");
        }

        String stockKey = "seckill:stock:" + goodsId;

        // 1. 检查Redis缓存是否存在
        Object stockObj = redisTemplate.opsForValue().get(stockKey);
        if (stockObj == null) {
            // 重新预热库存
            preheatStock(stockKey, goodsId);
        }

        // 2. Lua脚本原子扣减库存
        String luaScript = """
                local stockKey = KEYS[1]
                local stock = tonumber(redis.call('GET', stockKey))
                if stock == nil then
                    return -1
                end
                if stock <= 0 then
                    return 0
                end
                redis.call('DECR', stockKey)
                return stock
                """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(luaScript);
        redisScript.setResultType(Long.class);

        Long result;
        try {
            result = redisTemplate.execute(redisScript, List.of(stockKey));
        } catch (Exception e) {
            log.error("Redis执行Lua脚本异常，降级到同步处理: goodsId={}, error={}", goodsId, e.getMessage());
            // Redis异常，降级到同步处理
            SeckillOrder order = doSeckill(userId, goodsId);
            return "秒杀成功，订单号：" + order.getOrderNo();
        }

        if (result == null || result == -1) {
            log.warn("Redis中无库存缓存，降级到同步处理: goodsId={}", goodsId);
            SeckillOrder order = doSeckill(userId, goodsId);
            return "秒杀成功，订单号：" + order.getOrderNo();
        } else if (result == 0) {
            throw new RuntimeException("已售罄");
        }

        // 3. Redis扣减成功，发送消息到MQ（异步处理）
        boolean sendSuccess = messageProducer.sendSeckillMessage(userId, goodsId);

        if (!sendSuccess) {
            // MQ发送失败（可能队列满），回补库存
            redisTemplate.opsForValue().increment(stockKey);
            log.error("秒杀请求入队失败，回补库存: goodsId={}", goodsId);
            throw new RuntimeException("系统繁忙，请稍后重试");
        }

        return "秒杀请求已提交，正在排队处理中，请稍后查询订单状态";
    }

    /**
     * 回补Redis库存（MQ消费者失败时调用）
     *
     * @param goodsId 商品ID
     */
    @Override
    public void refundStock(Long goodsId) {
        String stockKey = "seckill:stock:" + goodsId;
        redisTemplate.opsForValue().increment(stockKey);
        log.info("回补Redis库存: goodsId={}", goodsId);
    }

    @Override
    public SeckillOrder getOrderByUserAndGoods(Long userId, Long goodsId) {
        List<SeckillOrder> orders = orderMapper.selectByUserId(userId);
        if (orders != null) {
            for (SeckillOrder order : orders) {
                if (order.getGoodsId().equals(goodsId)) {
                    return order;
                }
            }
        }
        return null;
    }
}
