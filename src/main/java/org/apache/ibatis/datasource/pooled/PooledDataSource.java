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
package org.apache.ibatis.datasource.pooled;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * This is a simple, synchronous, thread-safe database connection pool.
 *
 * @author Clinton Begin
 * 池化数据源，池化的目的是
 * 1. 在空闲时段缓存一些连接，防止被突发流量冲垮
 * 2. 实现数据库连接复用，提到响应速度
 * 3. 控制数据库连接上限，防止连接过多造成数据库假死
 * 4. 统一管理数据库连接，避免连接泄露
 */
public class PooledDataSource implements DataSource {

    private static final Log log = LogFactory.getLog(PooledDataSource.class);

    // 真正管理连接的地方
    private final PoolState state = new PoolState(this);
    // 当连接池需要新连接时，通过这个非池化数据源来创建
    private final UnpooledDataSource dataSource;

    // OPTIONAL CONFIGURATION FIELDS
    // 和连接池相关的配置，都有默认值，是可选的配置
    protected int poolMaximumActiveConnections = 10;  // 最大活跃连接数
    protected int poolMaximumIdleConnections = 5; // 最大空闲连接数
    protected int poolMaximumCheckoutTime = 20000;  // 最大超时时间
    protected int poolTimeToWait = 20000; // 等待时间
    // 最大本地坏链接容忍度，坏连接是无法执行操作的或已经关闭的连接
    // 默认情况下，如果一个线程尝试从连接池中获取连接时遇到坏连接，它会再尝试获取新连接，直到达到这个容忍度的限制；
    // 这个机制确保了即使在遇到多个连续的坏连接的情况下，应用程序也能继续尝试执行其数据库操作
    protected int poolMaximumLocalBadConnectionTolerance = 3;
    // 发送到数据库的侦测查询，用来验证连接是否处于良好状态并准备好接受请求。可以设置为select 1
    protected String poolPingQuery = "NO PING QUERY SET";
    // 是否启用侦测查询。如果启用，需要设置 poolPingQuery 属性为一个有效的SQL语句。 默认false不启用
    protected boolean poolPingEnabled;
    // 设置了 poolPingQuery 的执行频率。它可以设置为与数据库连接超时时间相同，以避免不必要的侦测。
    protected int poolPingConnectionsNotUsedFor;
    // 数据源的一个唯一标识。这个代码是根据("" + url + username + password).hashCode()计算出来的一个哈希值。
    // 因此，整个池子中的所有连接的编码必须是一致的，里面的连接是等价的
    // MyBatis使用这个代码来确保当应用程序请求连接时，能够从连接池中获取到正确配置的连接。
    private int expectedConnectionTypeCode;

    // ====   各种构造器 ====

    // 构造池化数据源时，无参调用实际默认的是非池化的数据源
    public PooledDataSource() {
        dataSource = new UnpooledDataSource();
    }

    // 调用有参构造器根据传入的数据源决定是否是池化的
    public PooledDataSource(UnpooledDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PooledDataSource(String driver, String url, String username, String password) {
        dataSource = new UnpooledDataSource(driver, url, username, password);
        expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    }

    public PooledDataSource(String driver, String url, Properties driverProperties) {
        dataSource = new UnpooledDataSource(driver, url, driverProperties);
        expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    }

    public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
        dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
        expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    }

    public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
        dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
        expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
    }

    @Override
    public Connection getConnection() throws SQLException {
        return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return popConnection(username, password).getProxyConnection();
    }

    @Override
    public void setLoginTimeout(int loginTimeout) {
        DriverManager.setLoginTimeout(loginTimeout);
    }

    @Override
    public int getLoginTimeout() {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public void setLogWriter(PrintWriter logWriter) {
        DriverManager.setLogWriter(logWriter);
    }

    @Override
    public PrintWriter getLogWriter() {
        return DriverManager.getLogWriter();
    }

    /**
     * 设置连接池的驱动
     *
     * @param driver 具体的驱动
     */
    public void setDriver(String driver) {
        dataSource.setDriver(driver);
        forceCloseAll();
    }

    public void setUrl(String url) {
        dataSource.setUrl(url);
        forceCloseAll();
    }

    public void setUsername(String username) {
        dataSource.setUsername(username);
        forceCloseAll();
    }

    public void setPassword(String password) {
        dataSource.setPassword(password);
        forceCloseAll();
    }

    public void setDefaultAutoCommit(boolean defaultAutoCommit) {
        dataSource.setAutoCommit(defaultAutoCommit);
        forceCloseAll();
    }

    public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
        dataSource.setDefaultTransactionIsolationLevel(defaultTransactionIsolationLevel);
        forceCloseAll();
    }

    public void setDriverProperties(Properties driverProps) {
        dataSource.setDriverProperties(driverProps);
        forceCloseAll();
    }

    /**
     * Sets the default network timeout value to wait for the database operation to complete. See {@link Connection#setNetworkTimeout(java.util.concurrent.Executor, int)}
     *
     * @param milliseconds The time in milliseconds to wait for the database operation to complete.
     * @since 3.5.2
     */
    public void setDefaultNetworkTimeout(Integer milliseconds) {
        dataSource.setDefaultNetworkTimeout(milliseconds);
        forceCloseAll();
    }

    /**
     * The maximum number of active connections.
     *
     * @param poolMaximumActiveConnections The maximum number of active connections
     */
    public void setPoolMaximumActiveConnections(int poolMaximumActiveConnections) {
        this.poolMaximumActiveConnections = poolMaximumActiveConnections;
        forceCloseAll();
    }

    /**
     * The maximum number of idle connections.
     *
     * @param poolMaximumIdleConnections The maximum number of idle connections
     */
    public void setPoolMaximumIdleConnections(int poolMaximumIdleConnections) {
        this.poolMaximumIdleConnections = poolMaximumIdleConnections;
        forceCloseAll();
    }

    /**
     * The maximum number of tolerance for bad connection happens in one thread
     * which are applying for new {@link PooledConnection}.
     *
     * @param poolMaximumLocalBadConnectionTolerance max tolerance for bad connection happens in one thread
     * @since 3.4.5
     */
    public void setPoolMaximumLocalBadConnectionTolerance(
            int poolMaximumLocalBadConnectionTolerance) {
        this.poolMaximumLocalBadConnectionTolerance = poolMaximumLocalBadConnectionTolerance;
    }

    /**
     * The maximum time a connection can be used before it *may* be
     * given away again.
     *
     * @param poolMaximumCheckoutTime The maximum time
     */
    public void setPoolMaximumCheckoutTime(int poolMaximumCheckoutTime) {
        this.poolMaximumCheckoutTime = poolMaximumCheckoutTime;
        forceCloseAll();
    }

    /**
     * The time to wait before retrying to get a connection.
     *
     * @param poolTimeToWait The time to wait
     */
    public void setPoolTimeToWait(int poolTimeToWait) {
        this.poolTimeToWait = poolTimeToWait;
        forceCloseAll();
    }

    /**
     * The query to be used to check a connection.
     *
     * @param poolPingQuery The query
     */
    public void setPoolPingQuery(String poolPingQuery) {
        this.poolPingQuery = poolPingQuery;
        forceCloseAll();
    }

    /**
     * Determines if the ping query should be used.
     *
     * @param poolPingEnabled True if we need to check a connection before using it
     */
    public void setPoolPingEnabled(boolean poolPingEnabled) {
        this.poolPingEnabled = poolPingEnabled;
        forceCloseAll();
    }

    /**
     * If a connection has not been used in this many milliseconds, ping the
     * database to make sure the connection is still good.
     *
     * @param milliseconds the number of milliseconds of inactivity that will trigger a ping
     */
    public void setPoolPingConnectionsNotUsedFor(int milliseconds) {
        this.poolPingConnectionsNotUsedFor = milliseconds;
        forceCloseAll();
    }

    public String getDriver() {
        return dataSource.getDriver();
    }

    public String getUrl() {
        return dataSource.getUrl();
    }

    public String getUsername() {
        return dataSource.getUsername();
    }

    public String getPassword() {
        return dataSource.getPassword();
    }

    public boolean isAutoCommit() {
        return dataSource.isAutoCommit();
    }

    public Integer getDefaultTransactionIsolationLevel() {
        return dataSource.getDefaultTransactionIsolationLevel();
    }

    public Properties getDriverProperties() {
        return dataSource.getDriverProperties();
    }

    /**
     * Gets the default network timeout.
     *
     * @return the default network timeout
     * @since 3.5.2
     */
    public Integer getDefaultNetworkTimeout() {
        return dataSource.getDefaultNetworkTimeout();
    }

    public int getPoolMaximumActiveConnections() {
        return poolMaximumActiveConnections;
    }

    public int getPoolMaximumIdleConnections() {
        return poolMaximumIdleConnections;
    }

    public int getPoolMaximumLocalBadConnectionTolerance() {
        return poolMaximumLocalBadConnectionTolerance;
    }

    public int getPoolMaximumCheckoutTime() {
        return poolMaximumCheckoutTime;
    }

    public int getPoolTimeToWait() {
        return poolTimeToWait;
    }

    public String getPoolPingQuery() {
        return poolPingQuery;
    }

    public boolean isPoolPingEnabled() {
        return poolPingEnabled;
    }

    public int getPoolPingConnectionsNotUsedFor() {
        return poolPingConnectionsNotUsedFor;
    }

    /**
     * Closes all active and idle connections in the pool.
     * 关闭连接池中所有的活跃的、空闲的连接。在数据源属性变动时会被调用，保证连接池中所有的连接都是等价的，防止中途数据源信息改变导致的连接属性不同
     */
    public void forceCloseAll() {
        // 增加一个同步锁
        synchronized (state) {
            // 计算出连接的类型编码
            expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
            // 依次关闭所有活动的连接
            for (int i = state.activeConnections.size(); i > 0; i--) {
                try {
                    PooledConnection conn = state.activeConnections.remove(i - 1);
                    conn.invalidate();

                    Connection realConn = conn.getRealConnection();
                    if (!realConn.getAutoCommit()) {
                        realConn.rollback();
                    }
                    realConn.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            // 依次关闭所有空闲的连接
            for (int i = state.idleConnections.size(); i > 0; i--) {
                try {
                    PooledConnection conn = state.idleConnections.remove(i - 1);
                    conn.invalidate();

                    Connection realConn = conn.getRealConnection();
                    if (!realConn.getAutoCommit()) {
                        realConn.rollback();
                    }
                    realConn.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("PooledDataSource forcefully closed/removed all connections.");
        }
    }

    public PoolState getPoolState() {
        return state;
    }

    /**
     * 计算连接的类型编码
     */
    private int assembleConnectionTypeCode(String url, String username, String password) {
        return ("" + url + username + password).hashCode();
    }

    /**
     * 收回一个连接
     *
     * @param conn 要收回的连接
     * @throws SQLException
     */
    protected void pushConnection(PooledConnection conn) throws SQLException {

        // 防止多线程冲突
        synchronized (state) {
            // 将该连接从活跃连接里删除
            state.activeConnections.remove(conn);
            if (conn.isValid()) { // 如果当前连接是可用的
                // 如果空闲连接池未满，且该连接的类型编码属于这个连接池
                if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
                    state.accumulatedCheckoutTime += conn.getCheckoutTime();
                    // 如果不是自动提交的连接，将连接的操作回滚
                    if (!conn.getRealConnection().getAutoCommit()) {
                        conn.getRealConnection().rollback();
                    }
                    // 重新整理连接
                    PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
                    // 将连接放入空闲连接池
                    state.idleConnections.add(newConn);
                    newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
                    newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
                    // 设置连接为未校验状态，以便后续使用的时候重新校验
                    conn.invalidate();
                    if (log.isDebugEnabled()) {
                        log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
                    }
                    state.notifyAll();
                } else { // 如果连接池已满，或者不属于这个连接池
                    state.accumulatedCheckoutTime += conn.getCheckoutTime();
                    // 如果不是自动提交的先将操作回滚
                    if (!conn.getRealConnection().getAutoCommit()) {
                        conn.getRealConnection().rollback();
                    }
                    // 直接将连接关闭
                    conn.getRealConnection().close();
                    if (log.isDebugEnabled()) {
                        log.debug("Closed connection " + conn.getRealHashCode() + ".");
                    }
                    // 连接置为未校验
                    conn.invalidate();
                }
            } else { // 如果回收的连接不是valid的，就说明出现了一个坏链接，不对他做任何处理，记录坏连接数
                if (log.isDebugEnabled()) {
                    log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
                }
                state.badConnectionCount++;
            }
        }
    }

    /**
     * 从池化数据源中给出一个连接
     *
     * @param username 用户名
     * @param password 密码
     * @return 池化的数据库连接
     */
    private PooledConnection popConnection(String username, String password) throws SQLException {
        boolean countedWait = false;    // 用来实现每个请求等待多轮也只会记录一次
        PooledConnection conn = null;
        // 用于计算取出连接花费的时间
        long t = System.currentTimeMillis();
        int localBadConnectionCount = 0;

        while (conn == null) {
            // state就是连接池，用它加锁，防止多线程冲突
            synchronized (state) {
                // 空闲连接池非空，直接从空闲连接池中取一个连接，remove()方法会移除并返回元素
                if (!state.idleConnections.isEmpty()) {
                    // Pool has available connection
                    conn = state.idleConnections.remove(0);
                    // 打印日志
                    if (log.isDebugEnabled()) {
                        log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
                    }
                } else {    // 如果空闲连接池为空，说明此时所有连接都被占用
                    // Pool does not have available connection
                    // 检查当前活跃连接数是否达到配置的线程最大连接数，如果没有达到，说明允许创建新连接
                    if (state.activeConnections.size() < poolMaximumActiveConnections) {
                        // Can create new connection
                        // 通过非池化数据源创建一个新的数据库连接，创建的时候定义是属于这个数据源的。底层就是DriverManger#getConnection
                        conn = new PooledConnection(dataSource.getConnection(), this);
                        if (log.isDebugEnabled()) {
                            log.debug("Created connection " + conn.getRealHashCode() + ".");
                        }
                    } else {    // 如果已经达到最大连接数限制，就只能等别的线程释放连接了
                        // Cannot create new connection
                        // 获取所有活跃链接里最老的那个（第一个被使用的）
                        PooledConnection oldestActiveConnection = state.activeConnections.get(0);
                        // 获取该连接的已经被取出了多久
                        long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
                        // 如果这个最旧的连接已经被取出超过最大超时时间。
                        if (longestCheckoutTime > poolMaximumCheckoutTime) {
                            // Can claim overdue connection
                            // 声明该连接超期不还
                            state.claimedOverdueConnectionCount++;
                            state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;
                            state.accumulatedCheckoutTime += longestCheckoutTime;
                            // 因逾期不还从连接池中移除
                            state.activeConnections.remove(oldestActiveConnection);
                            // 如果这个连接不是auto commit的，尝试将其事务回滚
                            if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                                try {
                                    oldestActiveConnection.getRealConnection().rollback();
                                } catch (SQLException e) {
                  /*
                     Just log a message for debug and continue to execute the following
                     statement like nothing happened.
                     Wrap the bad connection with a new PooledConnection, this will help
                     to not interrupt current executing thread and give current thread a
                     chance to join the next competition for another valid/good database
                     connection. At the end of this loop, bad {@link @conn} will be set as null.
                   */
                                    log.debug("Bad connection. Could not roll back");
                                }
                            }
                            // 用最旧的那个连接代理的真正的连接来创建一个新连接，替代之前那个逾期不换的连接
                            // 并更新连接的创建时间，最后一次使用时间
                            conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
                            conn.setCreatedTimestamp(oldestActiveConnection.getCreatedTimestamp());
                            conn.setLastUsedTimestamp(oldestActiveConnection.getLastUsedTimestamp());
                            oldestActiveConnection.invalidate();
                            if (log.isDebugEnabled()) {
                                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
                            }
                        } else { // 如果以上情况都不存在，那就是单纯的连接池被占满，只能等待
                            // Must wait
                            try {
                                if (!countedWait) {
                                    // 记录发生等待的次数。某次请求等待多轮也只能算作发生了一次等待
                                    state.hadToWaitCount++;
                                    countedWait = true;
                                }
                                // 打印日志需要等待
                                if (log.isDebugEnabled()) {
                                    log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                                }
                                long wt = System.currentTimeMillis();
                                // 休眠一段时间再尝试，防止占用计算资源
                                state.wait(poolTimeToWait);
                                state.accumulatedWaitTime += System.currentTimeMillis() - wt;
                                // 中间发生异常，终止while循环
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                }
                // 取到数据库连接后
                if (conn != null) {
                    // ping to server and check the connection is valid or not
                    // 检查该连接是否仍然可用
                    if (conn.isValid()) {
                        // 如果连接未设置自动提交，先回滚未提交的操作
                        if (!conn.getRealConnection().getAutoCommit()) {
                            conn.getRealConnection().rollback();
                        }
                        // 配置该链接的信息，包括连接类型编码，确保归还时校验正确
                        conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
                        conn.setCheckoutTimestamp(System.currentTimeMillis());
                        conn.setLastUsedTimestamp(System.currentTimeMillis());
                        // 更新连接池状态
                        state.activeConnections.add(conn);
                        state.requestCount++;
                        state.accumulatedRequestTime += System.currentTimeMillis() - t;
                    } else { // 如果拿到的这个连接是不可用的
                        if (log.isDebugEnabled()) {
                            log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
                        }
                        // 更新连接池状态
                        state.badConnectionCount++;
                        localBadConnectionCount++;  // 这个是本地坏连接，用于下面那个if语句判断
                        conn = null; // 将拿到的conn置空，继续这个while循环
                        // 如果本地坏连接数量已经超过最大空闲连接数+最大坏连接容忍度的总和，说明没有连接可用。
                        // 直接抛出异常
                        if (localBadConnectionCount > (poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance)) {
                            if (log.isDebugEnabled()) {
                                log.debug("PooledDataSource: Could not get a good connection to the database.");
                            }
                            throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
                        }
                    }
                }
            }

        }
        // 出了while循环，如果conn仍然是null，说明中间出了异常
        if (conn == null) {
            if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
            }
            throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
        }

        return conn;
    }

    /**
     * Method to check to see if a connection is still usable
     * 检查该连接是否还是可用的
     *
     * @param conn - the connection to check
     * @return True if the connection is still usable
     */
    protected boolean pingConnection(PooledConnection conn) {
        boolean result = true;

        try {
            // 检查连接是否关闭
            result = !conn.getRealConnection().isClosed();
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
            }
            result = false;
        }

        if (result && poolPingEnabled && poolPingConnectionsNotUsedFor >= 0
                && conn.getTimeElapsedSinceLastUse() > poolPingConnectionsNotUsedFor) {
            // 通过连接ping数据库，中间发生任何异常都表示这个连接不可用
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Testing connection " + conn.getRealHashCode() + " ...");
                }
                Connection realConn = conn.getRealConnection();
                try (Statement statement = realConn.createStatement()) {
                    // ping一个NO PING QUERY SET来侦测数据库连接是否可用
                    statement.executeQuery(poolPingQuery).close();
                }
                if (!realConn.getAutoCommit()) {
                    realConn.rollback();
                }
                result = true;
                if (log.isDebugEnabled()) {
                    log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
                }
            } catch (Exception e) {
                log.warn("Execution of ping query '" + poolPingQuery + "' failed: " + e.getMessage());
                try {
                    conn.getRealConnection().close();
                } catch (Exception e2) {
                    // ignore
                }
                result = false;
                if (log.isDebugEnabled()) {
                    log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * Unwraps a pooled connection to get to the 'real' connection
     *
     * @param conn - the pooled connection to unwrap
     * @return The 'real' connection
     */
    public static Connection unwrapConnection(Connection conn) {
        if (Proxy.isProxyClass(conn.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(conn);
            if (handler instanceof PooledConnection) {
                return ((PooledConnection) handler).getRealConnection();
            }
        }
        return conn;
    }

    @Override
    protected void finalize() throws Throwable {
        forceCloseAll();
        super.finalize();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException(getClass().getName() + " is not a wrapper.");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }

}
