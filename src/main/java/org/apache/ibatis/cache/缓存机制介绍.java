/**
 * Mybatis 包括一级缓存和二级缓存。
 * <p>
 * 一级缓存：
 * 一级缓存又叫本地缓存。有两个与其相关的配置项
 * 1. 在配置文件的settings节点下，可以添加 <\setting name="localCacheScope" value = "SESSION"/> 来控制一级缓存作用范围，可选项有SESSION和STATEMENT，默认是SESSION，即一个会话。
 * 2. 可以在映射文件的数据库操作标签内增加flushCache属性，如果为true，则会在执行该操作前，清除一二级缓存，默认是false。
 * 一级缓存功能由{@link org.apache.ibatis.executor.BaseExecutor}实现。
 * BaseExecutor类作为实际执行器的基类，为所有实际执行器提供一些通用的基本功能，在这里增加缓存也就意味着每个实际执行器都具有这一级缓存。
 * 因为localCache和localOutputParameterCache都是Executor的属性，所以作用范围不会超过Executor的范围，而Executor归属于SqlSession，所以一级缓存最大作用范围就是SqlSession，即一次会话
 * <p>
 * 二级缓存：
 * 有4个配置项
 * 1. 在配置文件的settings标签中，可以通过<\setting name="cacheEnabled" value="true"/> 来控制二级缓存是否启用，默认是true，开启的。
 * 2. 在映射文件中，可以通过cache标签，通过属性配置本命名空间的缓存，或是通过cache-ref标签配置引用其他命名空间的缓存，如果两个标签都没有，则本命名空间没有缓存。
 * 3. 每个数据库操作节点的useCache属性，默认是true，表示该操作节点使用二级缓存，只对select类型的语句有意义。
 * 4. 每个数据库操作节点的flushCache属性，默认false，表示是否在操作前清空一二级缓存。
 * 二级缓存由{@link org.apache.ibatis.executor.CachingExecutor}实现。二级缓存实际存储结构如下：
 * CachingExecutor含有一个{@link org.apache.ibatis.cache.TransactionalCacheManager} 类型的属性，是增加了处理事务功能的包装器，实际上也是管理二级缓存的地方。
 * TransactionalCacheManager中含有一个Map<\Cache, TransactionalCache> transactionalCaches的属性，这里就是二级缓存数据真正存放的地方，每一个映射文件的二级缓存对应一个TransactionalCache对象，
 * 而该对象的delegate属性，就是真正存放二级缓存数据的Cache的实现，一般会是PerpetualCache
 * 关于处理二级缓存配置的代码见{@link org.apache.ibatis.session.Configuration#newExecutor}，其中根据cachingEnable的值决定是否用CachingExecutor装饰实际的执行器。
 * <p>
 * 关于二级缓存更新的逻辑：
 * 当一个更新方法执行的时候，会调用到Executor的update方法。在开启缓存时，具体调用到的实现是{@link org.apache.ibatis.executor.CachingExecutor#update}
 * 而在这个update方法内部，会调用flushCacheIfRequired()方法，来清除本命名空间所有缓存，所以下一次执行查询时，就会因为缓存未命中重新查询数据库，从而获取到最新的数据
 * <p>
 * 关于TransactionalCacheManager的transactionalCache属性的键为什么是一个Cache类，来自哪里。
 * 当对应的映射文件含有cache标签、开启二级缓存时，对应的MappedStatement的cache属性就不是null，而这个cache属性，也就是最终用来作为TransactionalCacheManager的transactionalCache的键的
 * 这个属于MappedStatement中的cache属性的值的生成来源是：{@link org.apache.ibatis.builder.xml.XMLMapperBuilder#parse()} --> configurationElement --> cacheElement（当存在cache标签是，该方法if内的逻辑会执行）
 * --> {@link org.apache.ibatis.builder.MapperBuilderAssistant#useNewCache} --> new CacheBuilder --> builder --> setStandardDecorators
 */