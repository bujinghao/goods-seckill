package com.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品实体
 */
@Data
public class SeckillGoods {
    /** 商品ID */
    private Long id;
    /** 商品名称 */
    private String goodsName;
    /** 商品标题 */
    private String goodsTitle;
    /** 商品图片 */
    private String goodsImg;
    /** 原价 */
    private BigDecimal goodsPrice;
    /** 秒杀价 */
    private BigDecimal seckillPrice;
    /** 库存数量 */
    private Integer stockCount;
    /** 每人限购数量 */
    private Integer stockCountPerUser;
    /** 秒杀开始时间 */
    private LocalDateTime startTime;
    /** 秒杀结束时间 */
    private LocalDateTime endTime;
    /** 状态：0-未开始，1-进行中，2-已结束 */
    private Integer status;
    /** 商品描述 */
    private String goodsDesc;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
