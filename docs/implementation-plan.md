# 商品秒杀系统 - 分阶段实现方案

## 项目描述

为电商平台设计高可用秒杀系统，支持单商品瞬时千级并发请求，通过分布式缓存、消息队列等技术保障数据强一致性，实现99.9%的请求成功率。

## 技术架构

- **高并发处理**：基于SpringBoot实现接口限流（令牌桶算法）+ Redis预减库存，QPS提升至3000+
- **数据一致性**：采用RabbitMQ异步处理订单，结合MySQL事务保证最终一致性
- **性能优化**：Redis集群缓存热点数据（商品详情/用户信息），响应时间 ≤ 50ms
- **安全机制**：JWT + 拦截器实现权限校验，AOP记录全链路操作日志

## 关键成果

- 通过RabbitMQ削峰填谷，系统吞吐量提升400%
- 库存超卖率控制在0.01%以下
- 前端使用Thymeleaf动态渲染，首屏加载优化至1.2s

***

## 阶段一：基础架构搭建（1-2天）

**目标**：搭建Spring Boot项目骨架，实现基础秒杀接口

**核心任务**：

1. 创建Spring Boot项目，配置MySQL、MyBatis/JPA
2. 设计数据库表：商品表、库存表、订单表、用户表
3. 实现基础秒杀接口（无并发控制）：
   - `GET /seckill/query` - 查询秒杀商品
   - `POST /seckill/{id}/do` - 执行秒杀
4. 基础事务控制：`@Transactional` 保证订单创建和库存扣减的原子性

**知识点**：Spring Boot、MyBatis/JPA、事务管理、RESTful API

**验证标准**：单线程下能正常完成秒杀流程，库存正确扣减	————测试通过

***

## 阶段二：Redis缓存层实现（1-2天）

**目标**：引入Redis实现库存预扣减，解决数据库压力问题

**核心任务**：

1. 配置Redis连接（单机/集群模式）
2. 实现库存预热：秒杀开始前将库存加载到Redis
3. Redis预减库存逻辑（使用Lua脚本保证原子性）：
   ```java
   Long stock = redisTemplate.execute(decrScript, Arrays.asList(key));
   if (stock < 0) {
       // 恢复库存，返回失败
   }
   ```
4. 实现商品详情缓存（StringRedisTemplate + JSON序列化）
5. 缓存一致性策略：定时同步或主动失效

**知识点**：Redis数据结构、Lua脚本、缓存穿透/击穿/雪崩、分布式锁

**验证标准**：

- 1000并发下无超卖		  ————测试通过，主机配置高的可以测更多并发，或者集群测试
- 商品详情查询响应时间 < 50ms  ————测试通过

***

## 阶段三：接口限流机制（1天）

**目标**：实现令牌桶算法限流，保护系统不被流量击垮

**核心任务**：

1. 实现令牌桶算法（或使用Guava RateLimiter）
2. 自定义注解 `@RateLimit` + AOP切面
3. 限流维度：
   - 全局限流：系统总QPS限制
   - 用户限流：单用户每秒最多N次请求
4. 限流后的友好提示（返回"系统繁忙"）

**知识点**：限流算法（令牌桶/漏桶）、AOP、自定义注解、拦截器

**验证标准**：

- 超过限流阈值的请求被快速拒绝
- 限流不影响正常请求处理

***

## 阶段四：RabbitMQ异步订单处理（2天）

**目标**：通过消息队列削峰填谷，提升系统吞吐量

**核心任务**：

1. 配置RabbitMQ（Direct/Topic模式）
2. 秒杀流程改造：
   ```
   预减库存成功 → 发送消息到MQ → 立即返回"排队中"
   MQ消费者 → 创建订单 → 数据库扣减库存
   ```
3. 消息可靠性保障：
   - 生产者确认机制（publisher-confirm）
   - 消息持久化
   - 消费者手动ACK
4. 幂等性处理：防止消息重复消费导致重复下单
5. 前端轮询订单状态（或WebSocket推送）

**知识点**：RabbitMQ、消息可靠性、幂等性、异步编程、最终一致性

**验证标准**：

- 系统吞吐量提升400%+
- 消息不丢失，订单最终都能创建成功
- 库存超卖率 < 0.01%

***

## 阶段五：安全机制与日志（1天）

**目标**：实现JWT权限校验和全链路操作日志

**核心任务**：

1. JWT认证：
   - 登录接口返回token
   - 自定义拦截器验证token
   - token刷新机制
   - **JWT工具类设计**（`JwtUtil`）：
     - 密钥管理（使用256位密钥，建议存储在配置文件或Vault）
     - 签名算法（HS256或RS256，推荐RS256）
     - token生成方法（包含userId、username、role等claims）
     - token解析和验证方法（验证签名、过期时间）
   - **token存储策略**：
     - Redis存储token（支持单点登录，踢出旧token）
     - key格式：`seckill:token:{userId}`
     - 设置token过期时间（建议30分钟）
     - token刷新策略（快过期时自动续期）
   - **白名单配置**：
     - 登录接口 `/user/login`、`/user/register`
     - 静态资源 `/static/**`、`/public/**`
     - 健康检查 `/actuator/**`
     - 商品列表页 `/goods/list`（不包含秒杀详情）
   - **登录失败处理**：
     - 密码错误次数限制（5次后锁定账户15分钟）
     - 账户锁定状态记录（Redis存储锁定状态）
     - 登录失败日志记录（记录IP、时间、失败原因）

2. 秒杀接口安全：
   - 接口隐藏（秒杀开始前不暴露URL）
   - 防刷机制（同一用户限制访问频率）
   - **动态URL生成机制**：
     - 秒杀开始前，后端生成动态URL（包含随机hash）
     - hash规则：`MD5(userId + goodsId + timestamp + salt)`
     - 客户端在秒杀开始时才获取真实URL
     - URL有效期：30秒，防止提前获取
   - **验证码机制**：
     - 秒杀前要求用户输入图形验证码（防止机器人）
     - 验证码生成：使用Google Kaptcha或类似库
     - 验证码有效期：60秒
     - 验证码验证通过后才返回动态URL
   - **IP黑名单机制**：
     - 记录恶意IP（短时间内大量请求失败）
     - 黑名单存储在Redis（key: `seckill:blacklist:ip`）
     - 黑名单有效期：1小时（自动解除）
     - 黑名单中的IP直接拒绝访问
   - **请求签名验证**：
     - 关键接口（下单）添加签名验证
     - 签名规则：`HMAC-SHA256(userId + goodsId + timestamp, secretKey)`
     - 防止参数篡改和重放攻击
   <!-- - **防重复提交**：
     - Redis记录用户秒杀状态（key: `seckill:user:status:{userId}:{goodsId}`）
     - 同一用户对同一商品只能秒杀一次
     - 订单创建成功后删除状态记录 -->

3. AOP操作日志：
   - 自定义注解 `@OperationLog`
   - 记录：用户、操作、时间、IP、参数、结果
   - **（可选1）生成日志文件，保留最近一星期**
     - Logback配置（`logback-spring.xml`）
     - 日志文件滚动策略：按日期滚动，每天一个文件
     - 文件命名：`operation-log-{yyyy-MM-dd}.log`
     - 文件保留策略：保留最近7天，自动删除旧文件
     - 日志格式：JSON格式（包含userId、operation、ip、params、result、timestamp）
     - 日志级别配置：INFO级别记录关键操作，DEBUG级别记录详细参数
   - **（可选2）异步写入数据库（`@Async`）**
     - **操作日志表设计**（`operation_log`）：
       ```sql
       CREATE TABLE operation_log (
         id BIGINT PRIMARY KEY AUTO_INCREMENT,
         user_id BIGINT NOT NULL COMMENT '操作用户ID',
         username VARCHAR(50) COMMENT '用户名',
         operation VARCHAR(100) NOT NULL COMMENT '操作类型（LOGIN/SECKILL_ORDER/UPDATE_GOODS等）',
         method VARCHAR(200) COMMENT '方法名',
         params TEXT COMMENT '请求参数（JSON格式）',
         result TEXT COMMENT '操作结果（JSON格式）',
         ip VARCHAR(50) COMMENT '操作IP',
         location VARCHAR(100) COMMENT 'IP归属地（可选）',
         time_taken INT COMMENT '执行时长（毫秒）',
         status TINYINT COMMENT '操作状态（1成功 0失败）',
         error_msg TEXT COMMENT '错误信息',
         create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
         INDEX idx_user_id (user_id),
         INDEX idx_create_time (create_time)
       );
       ```
     - **异步线程池配置**：
       - 实现`AsyncConfigurer`接口配置线程池
       - 核心线程数：5，最大线程数：20，队列容量：100
       - 线程名称前缀：`operation-log-async-`
       - 拒绝策略：CallerRunsPolicy（由调用线程执行）
     - **异步日志服务**（`OperationLogService`）：
       - 使用`@Async`注解标记保存方法
       - 捕获异常但不影响主流程（记录到错误日志）
       - 批量插入优化（可选，使用`saveBatch`）
     - **日志敏感信息脱敏**：
       - 参数脱敏：密码、手机号、身份证等（使用正则替换）
       - 结果脱敏：token、密码等敏感字段
       - 使用工具类`LogMaskUtil`统一处理
   - **日志查询接口**（管理后台）：
     - 按用户ID查询：`GET /admin/logs?userId={userId}`
     - 按操作类型查询：`GET /admin/logs?operation={operation}`
     - 按时间范围查询：`GET /admin/logs?startTime={startTime}&endTime={endTime}`
     - 分页查询：支持page和size参数
   - ~~（可选3）近期日志监控使用prometheus和grafana实现日志分析~~
   - ~~（可选4）后期考虑接入elk系统，实现日志聚合和分析~~

**知识点**：JWT、拦截器、AOP、异步编程、安全机制、日志管理

**验证标准**：

- 未登录用户无法访问秒杀接口
- 所有关键操作都有日志记录
- 日志写入不影响主流程性能（异步写入耗时 < 5ms）
- 日志文件能正确滚动并自动清理
- 数据库日志能正确记录并支持查询
- 登录失败次数限制生效，账户锁定机制正常
- 动态URL验证机制正常，提前获取无效
- 验证码验证机制正常，防止机器人攻击
- IP黑名单机制生效，恶意IP被拦截
- 请求签名验证通过，防止参数篡改

***

## 阶段六：前端优化与集成（1-2天）

**目标**：使用Thymeleaf实现动态页面，优化首屏加载

**核心任务**：

1. Thymeleaf页面渲染：
   - 商品列表页
   - 秒杀详情页（倒计时）
   - 秒杀结果页
2. 静态资源优化：
   - 资源压缩（CSS/JS）
   - CDN加速
   - 图片懒加载
3. 接口调用优化：
   - 前端防抖（防止重复提交）
   - loading状态提示
4. 页面缓存：Thymeleaf模板缓存

**知识点**：Thymeleaf、前端性能优化、浏览器缓存、CDN

**验证标准**：首屏加载时间 ≤ 1.2s

***

## 阶段七：性能调优与压测（2-3天）

**目标**：达到项目描述中的性能指标

**核心任务**：

1. 使用JMeter或wrk进行压力测试
2. JVM调优：
   - 堆内存设置
   - GC策略优化
3. 数据库优化：
   - 索引优化
   - 连接池配置（HikariCP）
4. Redis优化：
   - 连接池配置
   - 序列化优化（Protostuff/Kryo）
5. 监控告警：（考虑使用 Micrometer集成Prometheus、Grafana展示）
   - 接口响应时间监控
   - 异常率监控
   - 库存变化监控

**知识点**：性能测试、JVM调优、数据库优化、监控

**验证标准**：

- QPS ≥ 3000
- 响应时间 ≤ 50ms（P99）
- 请求成功率 ≥ 99.9%
- 超卖率 < 0.01%

***

## 技术要点总结

| 阶段  | 核心技术                | 解决的问题      |
| --- | ------------------- | ---------- |
| 阶段一 | Spring Boot + MySQL | 基础功能实现     |
| 阶段二 | Redis               | 降低数据库压力、超卖问题 |
| 阶段三 | 令牌桶限流               | 系统保护、流量控制  |
| 阶段四 | RabbitMQ            | 高并发削峰、异步处理 |
| 阶段五 | JWT + AOP           | 安全认证、操作审计  |
| 阶段六 | Thymeleaf           | 用户体验、首屏优化  |
| 阶段七 | 性能调优                | 达到生产级指标    |

***

## 建议学习顺序

**先易后难，循序渐进**：

1. 先跑通基础流程（阶段一）
2. 引入缓存解决核心问题（阶段二）
3. 添加保护措施（阶段三）
4. 异步化提升性能（阶段四）
5. 完善安全和监控（阶段五、六）
6. 最后调优达标（阶段七）

每个阶段完成后都进行压测验证，对比优化前后的性能差异，这样能更深刻地理解每项技术的作用。
