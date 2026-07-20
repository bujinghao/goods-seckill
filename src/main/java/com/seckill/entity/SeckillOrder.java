package com.seckill.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体
 */
@Data
public class SeckillOrder {
    /** 订单ID */
    private Long id;
    /** 订单编号 */
    private String orderNo;
    /** 用户ID */
    private Long userId;
    /** 商品ID */
    private Long goodsId;
    /** 商品名称（冗余） */
    private String goodsName;
    /** 秒杀价格 */
    private BigDecimal seckillPrice;
    /** 购买数量 */
    private Integer count;
    /** 订单总价 */
    private BigDecimal totalPrice;
    /** 订单状态：0-待支付，1-已支付，2-已取消，3-已超时 */
    private Integer status;
    /** 支付时间 */
    private LocalDateTime payTime;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
