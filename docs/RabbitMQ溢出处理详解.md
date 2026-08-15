# RabbitMQ队列溢出处理详解

## 📊 `overflow` 参数作用

当队列消息数量达到 `maxLength` 限制时，如何处理新消息：

```java
.maxLength(10000L)  // 队列最多容纳10000条消息
.overflow("reject-publish")  // 达到上限后的行为（字符串参数）
```

**注意**：Spring AMQP的 `overflow()` 方法接受字符串参数，而不是枚举！

---

## 🔍 两种溢出模式对比

### 模式1：`"reject-publish"`（推荐）

```
队列满时：
┌────────────────────────────────────────────────────────┐
│  新消息 → 交换机 → 检测队列满 → 拒绝消息 → 返回失败    │
│                                                        │
│  生产者收到：                                           │
│  - confirmCallback（ack=false）                        │
│  - cause="queue overflow"                              │
│                                                        │
│  优点：                                                 │
│  - 保护现有消息（先进先出，公平性）                      │
│  - 快速失败，避免无效等待                               │
│  - 防止MQ内存溢出                                       │
│                                                        │
│  缺点：                                                 │
│  - 需要生产者处理发送失败                               │
└────────────────────────────────────────────────────────┘
```

### 模式2：`"drop-head"`（不推荐）

```
队列满时：
┌────────────────────────────────────────────────────────┐
│  新消息 → 交换机 → 丢弃最老消息 → 新消息入队            │
│                                                        │
│  优点：                                                 │
│  - 确保最新消息被处理                                   │
│                                                        │
│  缺点：                                                 │
│  - 早期排队的用户会被丢弃（不公平）                      │
│  - 秒杀场景不推荐                                       │
└────────────────────────────────────────────────────────┘
```

---

## ✅ 秒杀场景推荐使用 `"reject-publish"` 的原因

| 原因 | 说明 |
|-----|------|
| **公平性** | 保护先到用户的权益（先进先出） |
| **快速失败** | 队列满时直接返回"系统繁忙"，避免无效等待 |
| **系统稳定性** | 防止MQ内存溢出，保护系统稳定性 |

---

## 🔧 完整实现流程

### 1. 队列配置（已完成）

```java
@Bean
public Queue seckillQueue() {
    return QueueBuilder.durable(SECKILL_QUEUE)
            .maxLength(10000L)  // 队列容量限制
            .overflow("reject-publish")  // 溢出行为（字符串参数）
            .build();
}
```

### 2. 生产者配置（已完成）

**关键配置（application.yml）**：
```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated  # 启用发布确认
    publisher-returns: true  # 启用发布返回
    template:
      mandatory: true  # 消息路由失败时返回给生产者
```

**生产者代码（SeckillMessageProducer.java）**：
```java
@Component
public class SeckillMessageProducer implements RabbitTemplate.ConfirmCallback {

    public SeckillMessageProducer(RabbitTemplate rabbitTemplate) {
        rabbitTemplate.setConfirmCallback(this);  // 注册确认回调
    }

    /**
     * 消息确认回调
     * 队列满时：ack=false, cause="queue overflow"
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (!ack) {
            log.error("队列已满，消息被拒绝: messageId={}, cause={}",
                correlationData.getId(), cause);

            // 解析消息ID，回补库存
            String[] parts = correlationData.getId().split("_");
            Long userId = Long.parseLong(parts[1]);
            Long goodsId = Long.parseLong(parts[2]);

            // 回补Redis库存
            redisTemplate.opsForValue().increment("seckill:stock:" + goodsId);
        }
    }
}
```

### 3. 消费者配置（已完成）

```java
@RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
public void handleSeckillRequest(SeckillMessage message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    try {
        // 处理秒杀请求
        seckillService.validateAndCreateOrder(userId, goodsId);

        // 手动确认消息
        channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
        // 失败时回补库存
        seckillService.refundStock(goodsId);

        // 确认消息（不重新入队）
        channel.basicAck(deliveryTag, false);
    }
}
```

---

## 📊 实际运行示例

### 场景1：队列正常（消息数 < 10000）

```
时刻1: 用户A发送秒杀请求
       Redis扣减库存（stock=999）
       发送MQ消息
       → confirmCallback（ack=true）
       → 返回"排队中"

时刻2: MQ消费者处理
       数据库创建订单
       → 确认消息
       → 用户A收到"秒杀成功"
```

### 场景2：队列满（消息数 = 10000）

```
时刻1: 用户B发送秒杀请求
       Redis扣减库存（stock=999）
       发送MQ消息
       → 队列满（10000条）
       → RabbitMQ拒绝消息
       → confirmCallback（ack=false, cause="PRECONDITION_FAILED - queue overflow"）

时刻2: 生产者收到失败回调
       解析消息ID（userId=B, goodsId=1）
       → 回补Redis库存（stock=1000）
       → 日志记录"队列已满"

时刻3: 用户B收到响应
       → "系统繁忙，请稍后重试"
```

---

## 🚨 异常处理流程

### 流程图

```
┌─────────────────────────────────────────────────────────┐
│  用户发送秒杀请求                                         │
└───────────────┬─────────────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────────────┐
│  Redis Lua脚本原子扣减库存                                │
│  - 成功：stock > 0，继续                                  │
│  - 失败：stock <= 0，返回"已售罄"                         │
└───────────────┬─────────────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────────────┐
│  发送消息到RabbitMQ                                       │
│  - 成功：返回"排队中"                                      │
│  - 失败（队列满）：触发confirmCallback（ack=false）        │
└───────────────┬─────────────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────────────┐
│  confirmCallback（ack=false）                             │
│  - 解析消息ID（userId, goodsId）                          │
│  - 回补Redis库存（INCR）                                  │
│  - 日志记录"队列已满"                                      │
│  - 返回用户"系统繁忙"                                      │
└─────────────────────────────────────────────────────────┘
```

---

## 📋 监控与告警

### 关键监控指标

| 指标 | 监控方式 | 阈值 | 处理建议 |
|-----|---------|------|---------|
| **队列消息数** | RabbitMQ Management | > 8000 | 增加消费者数量 |
| **队列满拒绝次数** | confirmCallback（ack=false） | > 100/分钟 | 扩容MQ或增加消费者 |
| **消费者处理速度** | 自定义Metrics | < 200 TPS | 增加消费者数量 |
| **Redis回补次数** | 日志统计 | > 50/分钟 | 检查消费者健康状况 |

### 告警配置

```yaml
# Prometheus告警规则
groups:
  - name: seckill_alerts
    rules:
      - alert: QueueNearFull
        expr: rabbitmq_queue_messages{queue="seckill.queue"} > 8000
        for: 1m
        annotations:
          summary: "秒杀队列接近满载"
          description: "队列消息数：{{ $value }}"

      - alert: QueueFullRejects
        expr: increase(seckill_queue_rejects_total[5m]) > 100
        for: 1m
        annotations:
          summary: "队列拒绝消息过多"
          description: "最近5分钟拒绝：{{ $value }} 次"
```

---

## 🎯 最佳实践总结

### 配置建议

| 配置项 | 推荐值 | 说明 |
|-------|-------|------|
| `maxLength` | 10000 | 根据MQ内存和消费者速度调整 |
| `overflow` | `"reject-publish"` | 秒杀场景推荐（字符串参数） |
| `publisher-confirm-type` | `correlated` | 必须启用，否则无法检测队列满 |
| `concurrency` | 50-300 | 匹配数据库连接池大小 |

### 异常处理建议

| 异常场景 | 处理方式 | 用户体验 |
|---------|---------|---------|
| **队列满** | confirmCallback回补库存 | "系统繁忙，请稍后重试" |
| **消费者失败** | 手动回补库存 + 确认消息 | 用户无感知（排队中） |
| **Redis异常** | 降级到同步处理 | "秒杀成功/失败" |

---

## ⚠️ 注意事项

1. **必须启用发布确认**
   ```yaml
   spring.rabbitmq.publisher-confirm-type: correlated
   ```
   否则无法检测队列满的情况！

2. **必须手动确认消息**
   ```java
   channel.basicAck(deliveryTag, false);
   ```
   否则消息会重复投递！

3. **必须回补库存**
   ```java
   redisTemplate.opsForValue().increment(stockKey);
   ```
   否则会出现数据不一致！

4. **队列容量要合理**
   - 太小：频繁拒绝，用户体验差
   - 太大：MQ内存溢出风险
   - 推荐：10000（可根据压测调整）

---

## 🚀 验证步骤

### 1. 启动RabbitMQ

```bash
# Windows
rabbitmq-server

# 查看队列状态
http://wljhost:15672/#/queues
```

### 2. 压测验证

```bash
# 使用JMeter发送10000请求
POST http://wljhost:8080/seckill/1/do/async?userId=123

# 观察队列状态
- 队列消息数：应逐渐增加，达到10000后保持稳定
- 拒绝次数：应 > 0（队列满时）

# 观察日志
grep "队列已满" logs/app.log
grep "回补库存" logs/app.log
```

### 3. 数据一致性验证

```sql
-- 检查Redis库存与数据库订单数是否一致
SELECT COUNT(*) FROM seckill_order WHERE goods_id = 1;

-- Redis库存
GET "seckill:stock:1"

-- 公式：初始库存 - 订单数 = Redis库存
```

---

## 📋 总结

| 问题 | 答案 |
|-----|------|
| **何时触发overflow** | 队列消息数 = maxLength |
| **推荐模式** | `"reject-publish"`（秒杀场景） |
| **如何检测** | `confirmCallback（ack=false）` |
| **如何处理** | 回补Redis库存 + 返回用户"系统繁忙" |
| **关键配置** | `publisher-confirm-type: correlated` |

**核心要点**：
- 使用 `"reject-publish"` 保护先到用户（公平性）
- 必须启用发布确认，否则无法检测队列满
- 队列满时立即回补库存，避免数据不一致
- 监控队列消息数，及时扩容消费者