/**
 * Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.cursor.defaults;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This is the default implementation of a MyBatis Cursor.
 * This implementation is not thread safe.
 * 默认的游标实现
 * 目前看ResultSetWrapper并没有将结果集全都存到内存，所以是可以节约内存的。
 *
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
public class DefaultCursor<T> implements Cursor<T> {

    // ResultSetHandler stuff
    // 结果集处理器
    private final DefaultResultSetHandler resultSetHandler;
    // 结果集对应的ResultMap信息来源于映射文件的ResultMap标签节点
    private final ResultMap resultMap;
    // 返回结果集的详细信息
    private final ResultSetWrapper rsw;
    // 结果集的起始信息
    private final RowBounds rowBounds;
    // ResultHandler的子类，起到暂存结果的作用
    protected final ObjectWrapperResultHandler<T> objectWrapperResultHandler = new ObjectWrapperResultHandler<>();

    // 内部迭代器
    private final CursorIterator cursorIterator = new CursorIterator();
    // 迭代器存在标志位
    private boolean iteratorRetrieved;

    // 游标状态
    private CursorStatus status = CursorStatus.CREATED;
    // 记录已经映射的行
    private int indexWithRowBound = -1;

    /**
     * 内部枚举类，表示游标状态。
     */
    private enum CursorStatus {

        /**
         * 新创建的游标，尚未被消费
         * A freshly created cursor, database ResultSet consuming has not started.
         */
        CREATED,
        /**
         * 游标正在被使用，结果集正在被消费，一个未被使用的游标时CREATED状态
         * A cursor currently in use, database ResultSet consuming has started.
         */
        OPEN,
        /**
         * 关闭的游标，结果集未被完全消费
         * A closed cursor, not fully consumed.
         */
        CLOSED,
        /**
         * 游标已关闭，结果集已被完全消费
         * A fully consumed cursor, a consumed cursor is always closed.
         */
        CONSUMED
    }

    public DefaultCursor(DefaultResultSetHandler resultSetHandler, ResultMap resultMap, ResultSetWrapper rsw, RowBounds rowBounds) {
        this.resultSetHandler = resultSetHandler;
        this.resultMap = resultMap;
        this.rsw = rsw;
        this.rowBounds = rowBounds;
    }

    @Override
    public boolean isOpen() {
        return status == CursorStatus.OPEN;
    }

    @Override
    public boolean isConsumed() {
        return status == CursorStatus.CONSUMED;
    }

    @Override
    public int getCurrentIndex() {
        return rowBounds.getOffset() + cursorIterator.iteratorIndex;
    }

    /**
     * 返回迭代器
     */
    @Override
    public Iterator<T> iterator() {
        // 如果已经有迭代器，抛出异常
        if (iteratorRetrieved) {
            throw new IllegalStateException("Cannot open more than one iterator on a Cursor");
        }
        // 如果游标已经关闭，抛出异常
        if (isClosed()) {
            throw new IllegalStateException("A Cursor is already closed.");
        }
        // 声明迭代器存在标志
        iteratorRetrieved = true;
        // 返回迭代器
        return cursorIterator;
    }

    @Override
    public void close() {
        if (isClosed()) {
            return;
        }

        ResultSet rs = rsw.getResultSet();
        try {
            if (rs != null) {
                rs.close();
            }
        } catch (SQLException e) {
            // ignore
        } finally {
            status = CursorStatus.CLOSED;
        }
    }

    /**
     * 考虑边界限制（limit），从数据库获取下一个对象
     *
     * @return 下一个对象
     */
    protected T fetchNextUsingRowBound() {
        // 从数据库结果集取出一个对象
        T result = fetchNextObjectFromDatabase();
        // 如果对象存在但是不满足边界限制，则持续读取数据库结果中的下一个位置，直到起始位置
        while (objectWrapperResultHandler.fetched && indexWithRowBound < rowBounds.getOffset()) {
            result = fetchNextObjectFromDatabase();
        }
        return result;
    }

    /**
     * 每次调用都会从数据库查询返回的结果集取出一条数据
     *
     * @return 下一个对象
     */
    protected T fetchNextObjectFromDatabase() {
        if (isClosed()) {
            return null;
        }

        try {
            objectWrapperResultHandler.fetched = false;
            status = CursorStatus.OPEN;
            // 结果集尚未关闭
            if (!rsw.getResultSet().isClosed()) {
                // 从结果集取出一条记录，将其转化为对象，存入objectWrapperResultHandler中
                resultSetHandler.handleRowValues(rsw, resultMap, objectWrapperResultHandler, RowBounds.DEFAULT, null);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // 获取存入objectResultHandler中的对象
        T next = objectWrapperResultHandler.result;
        // 读到了新的对象
        if (objectWrapperResultHandler.fetched) {
            // 更改所以，表明记录索引 +1
            indexWithRowBound++;
        }
        // No more object or limit reached
        // 没有新对象，或已经到了rowBounds边界
        if (!objectWrapperResultHandler.fetched || getReadItemsCount() == rowBounds.getOffset() + rowBounds.getLimit()) {
            // 游标内数据已经消费完毕，更新游标状态
            close();
            status = CursorStatus.CONSUMED;
        }
        // 清除objectWrapperHandler中的对象，准备存储下一个对象
        objectWrapperResultHandler.result = null;

        return next;
    }

    private boolean isClosed() {
        return status == CursorStatus.CLOSED || status == CursorStatus.CONSUMED;
    }

    private int getReadItemsCount() {
        return indexWithRowBound + 1;
    }

    /**
     * 内部类，一个简单的结果处理器，只是将上下文中的一条结果取出放入自身的result属性中
     */
    protected static class ObjectWrapperResultHandler<T> implements ResultHandler<T> {

        protected T result;
        protected boolean fetched;

        /**
         * 从结果集取出并处理结果
         *
         * @param context 结果上下文
         */
        @Override
        public void handleResult(ResultContext<? extends T> context) {
            // 从上下文取出一条及给
            this.result = context.getResultObject();
            // 关闭结果上下文
            context.stop();
            fetched = true;
        }
    }

    /**
     * 迭代器。
     */
    protected class CursorIterator implements Iterator<T> {

        /**
         * 持有下一个要返回的对象，在next操作中完成写入
         * Holder for the next object to be returned.
         */
        T object;

        /**
         * next方法中返回的对象的索引，默认是-1
         * Index of objects returned using next(), and as such, visible to users.
         */
        int iteratorIndex = -1;

        /**
         * 判断是否还有下一个元素，如果有，则先写入object中
         *
         * @return 是否存在下一个元素
         */
        @Override
        public boolean hasNext() {
            // 如果fetched属性为false
            if (!objectWrapperResultHandler.fetched) {
                // 判断是否能获取到新的，获取到放入objec体重
                object = fetchNextUsingRowBound();
            }
            // 如果fetched为true，表示拿过一条数据，说明还有数据
            return objectWrapperResultHandler.fetched;
        }

        /**
         * 返回下一个元素
         *
         * @return 下一个元素
         */
        @Override
        public T next() {
            // Fill next with object fetched from hasNext()
            T next = object;

            // fetched属性为false，去拿数据
            if (!objectWrapperResultHandler.fetched) {
                next = fetchNextUsingRowBound();
            }

            // fetch为true，将fetched置为false，
            if (objectWrapperResultHandler.fetched) {
                objectWrapperResultHandler.fetched = false;
                object = null;
                iteratorIndex++;
                return next;
            }
            throw new NoSuchElementException();
        }

        /**
         * 重写Iterator的remove方法，不允许这个操作，直接抛出异常
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Cannot remove element from Cursor");
        }
    }
}
