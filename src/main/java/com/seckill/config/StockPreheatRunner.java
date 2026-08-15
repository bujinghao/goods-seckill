package com.seckill.config;

import com.seckill.entity.SeckillGoods;
import com.seckill.mapper.SeckillGoodsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时预热库存到Redis
 * <p>
 * 使用ApplicationRunner在Spring Boot应用启动完成后自动执行库存预热，
 * 将进行中的秒杀商品库存加载到Redis，key格式: seckill:stock:{goodsId}
 * 
 * ApplicationRunner 与 CommandLineRunner 对比
 * 
 * 仅在于 run 方法接收的‌参数类型不同‌，导致参数解析能力有差异。
 * ‌执行顺序‌：若未指定 @Order，‌ApplicationRunner 默认优先于 CommandLineRunner 执行‌；同类型内按 Order
 * 值升序排列。‌‌
 * 
 * 
 * </p>
 */
@Component
public class StockPreheatRunner implements ApplicationRunner {

    private final Logger log = LoggerFactory.getLogger(StockPreheatRunner.class);

    /** 库存缓存Key前缀 */
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    // /** 限购缓存Key前缀 */
    // private static final String LIMIT_KEY_PREFIX = "seckill:limit:";

    private final SeckillGoodsMapper goodsMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public StockPreheatRunner(SeckillGoodsMapper goodsMapper, RedisTemplate<String, Object> redisTemplate) {
        this.goodsMapper = goodsMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== 开始预热秒杀库存 ==========");

        try {
            // 查询所有进行中的秒杀商品id、库存数量、限购数量
            List<SeckillGoods> goodsList = goodsMapper.selectStockAndLimitCount();

            if (goodsList == null || goodsList.isEmpty()) {
                log.info("当前没有进行中的秒杀商品，无需预热");
                return;
            }

            int successCount = 0;
            for (SeckillGoods goods : goodsList) {
                try {
                    // 预热库存到Redis
                    String key = STOCK_KEY_PREFIX + goods.getId();
                    Integer stock = goods.getStockCount();

                    // // 预热限购到Redis
                    // String limitKey = LIMIT_KEY_PREFIX + goods.getId();
                    // Integer limit = goods.getStockCountPerUser();

                    if (stock != null && stock > 0) {
                        redisTemplate.opsForValue().set(key, stock);
                        log.info("预热商品库存成功: goodsId={}, goodsName={}, stock={}",
                                goods.getId(), goods.getGoodsName(), stock);
                        successCount++;
                    } else {
                        log.warn("商品库存为0或null，跳过预热: goodsId={}", goods.getId());
                    }

                    // if (limit != null && limit > 0) {
                    // redisTemplate.opsForValue().set(limitKey, limit);
                    // log.info("预热商品限购成功: goodsId={}, goodsName={}, limit={}",
                    // goods.getId(), goods.getGoodsName(), limit);
                    // successCount++;
                    // } else {
                    // log.warn("商品限购为0或null，跳过预热: goodsId={}", goods.getId());
                    // }

                } catch (Exception e) {
                    log.error("预热商品库存失败: goodsId={}, error={}", goods.getId(), e.getMessage());
                }
            }

            log.info("========== 库存预热完成，成功: {}/{} ==========", successCount, goodsList.size());

        } catch (Exception e) {
            log.error("库存预热失败，请检查Redis连接: {}", e.getMessage(), e);
        }
    }
}