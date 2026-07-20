# Spring Boot 启动流程详解

## 整体流程图（方便记忆）

```
SpringApplication.run()
    ├── 阶段一：初始化 SpringApplication 对象
    │   ├── 1. 记录启动时间
    │   ├── 2. 判断 Web 应用类型
    │   ├── 3. 加载 ApplicationContextInitializer
    │   └── 4. 加载 ApplicationListener
    │
    ├── 阶段二：执行 run() 方法
    │   ├── 5. 创建 SpringApplicationRunListeners
    │   ├── 6. 发布 applicationStarting 事件
    │   ├── 7. 准备 Environment 环境
    │   ├── 8. 发布 applicationEnvironmentPrepared 事件
    │   ├── 9. 创建 ApplicationContext
    │   ├── 10. 准备 Context（核心！）
    │   ├── 11. 发布 contextPrepared 事件
    │   ├── 12. 加载 BeanDefinition
    │   ├── 13. 发布 contextLoaded 事件
    │   ├── 14. 刷新 Context（Bean 实例化！）
    │   ├── 15. 发布 applicationStarted 事件
    │   ├── 16. 执行 Runner
    │   └── 17. 发布 applicationReady 事件
    │
    └── 阶段三：启动完成
```

---

## 阶段一：初始化 SpringApplication 对象

### 步骤 1：记录启动时间
```java
// 记录应用开始启动的时间戳
long startTime = System.currentTimeMillis();
```

### 步骤 2：判断 Web 应用类型
```java
// 根据 classpath 下的类判断 Web 类型
this.webApplicationType = WebApplicationType.deduceFromClasspath();
// 可能值：NONE（非Web）、SERVLET（传统Servlet）、REACTIVE（WebFlux）
```

**面试点**：Spring Boot 通过检测 classpath 下是否存在 `DispatcherServlet`、`WebFlux` 等类来判断应用类型。

### 步骤 3：加载 ApplicationContextInitializer
```java
// 从 META-INF/spring.factories 加载所有初始化器
setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
```

**常见初始化器**：
- `ConfigurationPropertiesBindingPostProcessor` - 配置属性绑定
- `ContextIdApplicationContextInitializer` - 设置 Context ID

### 步骤 4：加载 ApplicationListener
```java
// 从 META-INF/spring.factories 加载所有监听器
setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
```

**常见监听器**：
- `BackgroundPreinitializer` - 后台预初始化
- `LoggingApplicationListener` - 日志系统初始化

---

## 阶段二：执行 run() 方法（核心！）

### 步骤 5：创建 SpringApplicationRunListeners
```java
SpringApplicationRunListeners listeners = getRunListeners(args);
// 用于在启动过程中发布事件
```

### 步骤 6：发布 applicationStarting 事件
```java
listeners.starting();
// 应用开始启动，此时 Environment 和 Context 还未创建
```

### 步骤 7：准备 Environment 环境
```java
ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
ConfigurableEnvironment environment = prepareEnvironment(listeners, applicationArguments);
// 加载 application.yml、application.properties 等配置
```

**关键点**：
- 解析命令行参数
- 加载配置文件（application.yml）
- 发布 `ApplicationEnvironmentPreparedEvent` 事件

### 步骤 8：发布 applicationEnvironmentPrepared 事件
```java
listeners.environmentPrepared(environment);
// 此时可以修改 Environment，如添加自定义配置源
```

### 步骤 9：创建 ApplicationContext
```java
context = createApplicationContext();
// 根据 Web 类型创建不同的 Context：
// - SERVLET: AnnotationConfigServletWebServerApplicationContext
// - REACTIVE: AnnotationConfigReactiveWebServerApplicationContext
// - NONE: AnnotationConfigApplicationContext
```

### 步骤 10：准备 Context（核心！）
```java
prepareContext(context, environment, listeners, applicationArguments, printedBanner);
```

**这个阶段做了什么**：
1. 设置 Environment 到 Context
2. 执行所有 `ApplicationContextInitializer` 的 `initialize()` 方法
3. 发布 `ApplicationContextInitializedEvent` 事件
4. 注册 `springApplicationArguments` Bean
5. 注册 `springBootBanner` Bean

### 步骤 11：发布 contextPrepared 事件
```java
listeners.contextPrepared(context);
```

### 步骤 12：加载 BeanDefinition
```java
load(context, sources.getAllSources());
// 扫描 @Component、@Service、@Controller 等注解
// 解析 @Configuration 类中的 @Bean 方法
// 加载 META-INF/spring.factories 中的自动配置类
```

**关键点**：
- 使用 `AnnotatedBeanDefinitionReader` 读取注解
- 使用 `ClassPathBeanDefinitionScanner` 扫描包
- 触发 `@EnableAutoConfiguration` 自动配置机制

### 步骤 13：发布 contextLoaded 事件
```java
listeners.contextLoaded(context);
```

### 步骤 14：刷新 Context（Bean 实例化！）
```java
refreshContext(context);
// 这是最核心的步骤！
```

**refreshContext 内部做了什么**：
1. **调用 `AbstractApplicationContext.refresh()`**
2. 关键步骤：
   - `invokeBeanFactoryPostProcessors()` - 执行 BeanFactoryPostProcessor
   - `registerBeanPostProcessors()` - 注册 BeanPostProcessor
   - `finishBeanFactoryInitialization()` - **实例化所有单例 Bean！**
   - `finishRefresh()` - 发布 `ContextRefreshedEvent`

**面试点**：所有单例 Bean 在这个阶段被实例化、注入依赖、执行初始化方法（`@PostConstruct`、`InitializingBean`）。

### 步骤 15：发布 applicationStarted 事件
```java
listeners.started(context);
// 应用已启动，Context 已刷新，但 Runner 还未执行
```

### 步骤 16：执行 Runner
```java
callRunners(context, applicationArguments);
// 执行 ApplicationRunner 和 CommandLineRunner
作用：
// 1. 初始化数据库
// 2. 预热缓存
// 3. 检查系统状态
// 4. 执行其他自定义逻辑
```

**常见用途**：
- 应用启动后执行初始化逻辑
- 预热缓存
- 检查系统状态

### 步骤 17：发布 applicationReady 事件
```java
listeners.running(context);
// 应用完全就绪，可以接收请求
```

---

## 阶段三：启动完成

```java
// 返回 Context，启动完成
return context;
```

---

## 面试记忆口诀

**"三初四环六上八加，十二刷新十五跑"**

- **三初**：初始化时间、Web类型、Initializer
- **四环**：Environment准备、事件发布
- **六上**：Listener创建、Context创建
- **八加**：加载BeanDefinition
- **十二刷新**：refreshContext（Bean实例化）
- **十五跑**：执行Runner、发布Ready事件

---

## 关键事件总结

| 事件                                  | 触发时机                  | 用途 |
|-------------------------------------- |-------------------------|------|
| `ApplicationStartingEvent`            | 应用开始启动              | 初始化日志 |
| `ApplicationEnvironmentPreparedEvent`     | Environment准备完成     | 修改配置 |
| `ApplicationContextInitializedEvent`    | Context创建后             | 自定义初始化 |
| `ApplicationPreparedEvent`            | Context加载完成           | 最后修改 |
| `ApplicationStartedEvent`             | Context刷新完成           | 启动后初始化 |
| `ApplicationReadyEvent`               | 应用就绪                  | 就绪后操作 |
| `ApplicationFailedEvent`              | 启动失败                | 异常处理 |  

---

## 与秒杀系统的关联

在秒杀系统中，以下阶段会被用到：

1. **阶段一**：配置 Redis 连接池、HikariCP 连接池
2. **阶段二**：
   - 步骤 10：初始化 `RedisTemplate`、`RabbitTemplate`
   - 步骤 14：创建 `SeckillService`、`SeckillController` 等 Bean
   - 步骤 16：执行 `ApplicationRunner` 预热库存到 Redis
3. **阶段三**：应用就绪，可以接收秒杀请求

**面试加分点**：可以说"我们在 `ApplicationRunner` 中实现了库存预热，将商品库存加载到 Redis，避免秒杀开始时大量数据库查询"。