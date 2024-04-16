/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * 解析mybatis-config.xml文件，一部分功能来自BaseBuilder
 */
public class XMLConfigBuilder extends BaseBuilder {

    // 标识是否已经解析完mybatis-config.xml文件
    private boolean parsed;
    // XML解析器
    private final XPathParser parser;
    // 标签定义的环境名称
    private String environment;
    // 用于对Reflector对象的创建和缓存
    private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        super(new Configuration());
        ErrorContext.instance().resource("SQL Mapper Configuration");
        this.configuration.setVariables(props);
        this.parsed = false;
        this.environment = environment;
        this.parser = parser;
    }

    /**
     * 解析配置文件的入口方法
     */
    public Configuration parse() {
        // 不允许重复解析
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        parsed = true;
        // 真正解析XML文件的方法
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    /**
     * 从根节点开始，解析mybatis-config.xml的方法
     * 解析的数据会放入Configuration对象
     *
     * @param root 根节点configuration
     */
    private void parseConfiguration(XNode root) {
        try {
            // issue #117 read properties first
            propertiesElement(root.evalNode("properties")); // <properties>
            Properties settings = settingsAsProperties(root.evalNode("settings")); // 解析settings节点
            loadCustomVfs(settings); // 加载自定义虚拟文件系统，通过<setting name="vfsImpl" value="com.example.MyCustomVfs"/>指定
            loadCustomLogImpl(settings);                        // 日志相关
            typeAliasesElement(root.evalNode("typeAliases")); // <typeAliases>
            pluginElement(root.evalNode("plugins"));    // <plugins>
            objectFactoryElement(root.evalNode("objectFactory")); // <objectFactory>
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));     // <objectWrapperFactory>
            reflectorFactoryElement(root.evalNode("reflectorFactory"));     // <reflectorFactory>
            settingsElement(settings);  // <settings>
            // read it after objectFactory and objectWrapperFactory issue #631
            environmentsElement(root.evalNode("environments")); // <environment>
            databaseIdProviderElement(root.evalNode("databaseIdProvider")); // <databaseIdProvider>
            typeHandlerElement(root.evalNode("typeHandlers"));  // <typeHandler>
            mapperElement(root.evalNode("mappers"));    // <mappers>
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    /**
     * 解析settings节点
     *
     * @param context 该节点对应的XNode
     * @return
     */
    private Properties settingsAsProperties(XNode context) {
        if (context == null) {
            return new Properties();
        }
        // 处理所有子标签，就是<setting>标签，将其name和value属性放到Properties对象里
        Properties props = context.getChildrenAsProperties();
        // Check that all settings are known to the configuration class
        // 检查Configuration对象是否包含每个配置项的setter方法，如果没有要抛异常
        MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
        for (Object key : props.keySet()) {
            if (!metaConfig.hasSetter(String.valueOf(key))) {
                throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
            }
        }
        return props;
    }

    private void loadCustomVfs(Properties props) throws ClassNotFoundException {
        String value = props.getProperty("vfsImpl");
        if (value != null) {
            String[] clazzes = value.split(",");
            for (String clazz : clazzes) {
                if (!clazz.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
                    configuration.setVfsImpl(vfsImpl);
                }
            }
        }
    }

    private void loadCustomLogImpl(Properties props) {
        Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
        configuration.setLogImpl(logImpl);
    }

    /**
     * 处理typeAliases节点
     *
     * @param parent 节点对应的XNode
     */
    private void typeAliasesElement(XNode parent) {
        if (parent != null) {
            // 依次处理每个子节点，也就是typeAlias标签和package标签
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    String typeAliasPackage = child.getStringAttribute("name");
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                } else {
                    // 如果不是package标签, 那就是typeAliases，获取他的type和alias属性，将其注册到configuration.typeAliasRegistry.typeAliases这个map中
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        Class<?> clazz = Resources.classForName(type);
                        if (alias == null) {
                            typeAliasRegistry.registerAlias(clazz);
                        } else {
                            typeAliasRegistry.registerAlias(alias, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                    }
                }
            }
        }
    }

    /**
     * 解析plugin标签
     *
     * @param parent <plugins>标签对应的XNode对象
     */
    private void pluginElement(XNode parent) throws Exception {
        // 前提是<plugins>标签存在才会解析
        if (parent != null) {
            // 依次取出plugins下的每一个标签节点
            for (XNode child : parent.getChildren()) {
                // 读取拦截器类名
                String interceptor = child.getStringAttribute("interceptor");
                // 读取拦截器属性
                Properties properties = child.getChildrenAsProperties();
                // 实例化拦截器类
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
                // 设置拦截器属性
                interceptorInstance.setProperties(properties);
                // 将拦截器加入拦截器链
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    /**
     * 处理<objectFactory>标签
     * 该标签实现了自定义ObjectFactory，可以在该标签配置自定义的实现类
     *
     * @param context objectFactory对应的XNode对象
     */
    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            // 获取标签的type属性
            String type = context.getStringAttribute("type");
            // 获取这个标签定义的name、value属性值
            Properties properties = context.getChildrenAsProperties();
            // 根据该属性值，初始化自定义的ObjectFactory实现
            ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(properties);
            // 将其放入configuration配置对象中
            configuration.setObjectFactory(factory);
        }
    }

    /**
     * <objectWrapperFactory> 标签解析
     * 该标签用来自定义ObjectWrapperFactory
     *
     * @param context <objectWrapperFactory> 标签对应的XNode对象
     */
    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    /**
     * <reflectorFactory> 标签解析
     * 该标签用来自定义reflectorFactory
     *
     * @param context <reflectorFactory> 标签对应的XNode对象
     * @throws Exception
     */
    private void reflectorFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            configuration.setReflectorFactory(factory);
        }
    }

    /**
     * 解析<properties>标签节点，properties标签定义的键值对用作变量在配置文件中使用
     * 对应Configuration的variables属性
     *
     * @param context <properties>生成的XNode对象
     * @throws Exception
     */
    private void propertiesElement(XNode context) throws Exception {
        if (context != null) {
            // 这个变量就是存储所有解析出来的键值对信息的地方
            Properties defaults = context.getChildrenAsProperties();
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            if (resource != null && url != null) {
                throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            if (resource != null) {
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            Properties vars = configuration.getVariables();
            if (vars != null) {
                defaults.putAll(vars);
            }
            parser.setVariables(defaults);
            configuration.setVariables(defaults);
        }
    }

    private void settingsElement(Properties props) {
        configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
        configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
        configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
        configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
        configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
        configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
        configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
        configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
        configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
        configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
        configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
        configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
        configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
        configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
        configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
        configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
        configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
        configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
        configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
        configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
        configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
        configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
        configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
        configuration.setLogPrefix(props.getProperty("logPrefix"));
        configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
        configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
        configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
    }

    /**
     * 解析<environments>标签
     *
     * @param context environment节点生成的XNode对象
     */
    private void environmentsElement(XNode context) throws Exception {
        if (context != null) {
            // 如果没有配置当前环境，就是用标签属性是default的环境
            if (environment == null) {
                environment = context.getStringAttribute("default");
            }
            // 依次处理每个子节点
            for (XNode child : context.getChildren()) {
                String id = child.getStringAttribute("id");
                // 是当前环境才解析
                if (isSpecifiedEnvironment(id)) {
                    // 获取<transactionManager>、<dataSource>等标签，并进行解析，其中会根据配置信息初始化相应的TransactionFactory对象和DataSource对象
                    TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                    DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                    DataSource dataSource = dsFactory.getDataSource();
                    // 创建Environment对象，并关联创建好的TransactionFactory和DataSource
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    // 将environment对象记录到Configuration对象中
                    configuration.setEnvironment(environmentBuilder.build());
                }
            }
        }
    }

    /**
     * 解析<databaseIdProvider>标签
     *
     * @param context <databaseIdProvider>标签对应的XNode对象
     */
    private void databaseIdProviderElement(XNode context) throws Exception {
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            // 获取type属性值
            String type = context.getStringAttribute("type");
            // awful patch to keep backward compatibility
            if ("VENDOR".equals(type)) {
                type = "DB_VENDOR";
            }
            // 初始化databaseIdProvider
            Properties properties = context.getChildrenAsProperties();
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
            databaseIdProvider.setProperties(properties);
        }
        Environment environment = configuration.getEnvironment();
        // 通过DataSource获取DatabaseId，并保存到Configuration中
        if (environment != null && databaseIdProvider != null) {
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            configuration.setDatabaseId(databaseId);
        }
    }

    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    /**
     * 解析数据源配置信息
     *
     * @param context 被解析的节点
     * @return 根据信息创建的数据源工厂
     */
    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            // 通过该type判断数据源类型：POOLED、UNPOOLED、JNDI
            String type = context.getStringAttribute("type");
            // 获取dataSource节点下的配置
            Properties props = context.getChildrenAsProperties();
            // 根据type获取相应的DataSourceFactory数据源工厂
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            // 设置数据源工厂的属性
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    /**
     * 解析typeHandler标签节点
     *
     * @param parent typeHandlers节点对应的XNode对象
     */
    private void typeHandlerElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                // 如果是package标签，就扫描该包下所有类，解析@MappedTypes注解，完成TypeHandler的注册
                if ("package".equals(child.getName())) {
                    String typeHandlerPackage = child.getStringAttribute("name");
                    typeHandlerRegistry.register(typeHandlerPackage);
                } else {
                    // 到这里就是typeHandler标签
                    // 获取javaType、jdbcType、handler
                    String javaTypeName = child.getStringAttribute("javaType");
                    String jdbcTypeName = child.getStringAttribute("jdbcType");
                    String handlerTypeName = child.getStringAttribute("handler");
                    // 根据这三个属性确定TypeHandler类型以及它能够处理的数据库类型和Java类型
                    Class<?> javaTypeClass = resolveClass(javaTypeName);
                    JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                    Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                    // 将其注册到configuration的typeHandlerRegistry的allTypeHandlersMap中
                    if (javaTypeClass != null) {
                        if (jdbcType == null) {
                            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
                        } else {
                            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
                        }
                    } else {
                        typeHandlerRegistry.register(typeHandlerClass);
                    }
                }
            }
        }
    }

    /**
     * 解析mappers标签节点
     *
     * @param parent mappers节点对应的XNode节点
     */
    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            // 依次处理mappers下的每一个节点，可能是mapper节点或package节点，package是指定映射文件所在的包
            for (XNode child : parent.getChildren()) {
                // 如果是package节点，取出包路径，解析出来的结果都加入Configuration对象的MapperRegistry的knownMappers中
                if ("package".equals(child.getName())) {
                    String mapperPackage = child.getStringAttribute("name");
                    configuration.addMappers(mapperPackage);
                } else {
                    // 解析mapper标签
                    // 这三个属性只有一个生效
                    String resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    // resource不为空时，从文件获取流
                    if (resource != null && url == null && mapperClass == null) {
                        ErrorContext.instance().resource(resource);
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        // 使用XMLMapperBuilder解析文件
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                        mapperParser.parse();
                        // url属性不为空
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        // 从网络获取输入流
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                        mapperParser.parse();
                        // mapperClass，配置的不是Mapper文件而是Mapper接口，hi系欸注册class属性指定的Mapper接口
                    } else if (resource == null && url == null && mapperClass != null) {
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
                    }
                }
            }
        }
    }

    private boolean isSpecifiedEnvironment(String id) {
        if (environment == null) {
            throw new BuilderException("No environment specified.");
        } else if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        } else if (environment.equals(id)) {
            return true;
        }
        return false;
    }

}
