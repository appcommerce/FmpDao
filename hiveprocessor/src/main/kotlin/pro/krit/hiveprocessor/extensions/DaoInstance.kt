package pro.krit.hiveprocessor.extensions

import com.mobrun.plugin.models.StatusSelectTable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import pro.krit.hiveprocessor.base.IDao
import pro.krit.hiveprocessor.common.QueryBuilder
import pro.krit.hiveprocessor.common.QueryExecuter

object DaoInstance {

    inline fun <reified E : Any, reified S : StatusSelectTable<E>> flowable(
        dao: IDao,
        where: String = "",
        limit: Int = 0,
        offset: Int = 0,
        orderBy: String = "",
        withStart: Boolean = true,
        emitDelay: Long = 0L,
        withDistinct: Boolean = false
    ) = flow {
        dao.getTrigger().collect {
            if(emitDelay > 0) {
                delay(emitDelay)
            }
            val result = select<E, S>(dao, where, limit, offset, orderBy)
            emit(result)
        }
    }.onStart {
        val isTriggerEmpty = dao.isTriggerEmpty()
        if (withStart && isTriggerEmpty) {
            if(emitDelay > 0) {
                delay(emitDelay)
            }
            val startResult = select<E, S>(dao, where, limit, offset, orderBy)
            emit(startResult)
        }
    }.apply {
        if (withDistinct) {
            distinctUntilChanged()
        }
    }

    inline fun <reified E : Any, reified S : StatusSelectTable<E>> select(
        dao: IDao,
        where: String = "",
        limit: Int = 0,
        offset: Int = 0,
        orderBy: String = ""
    ): List<E> {
        val selectQuery = QueryBuilder.createQuery(
            dao = dao,
            prefix = QueryBuilder.SELECT_QUERY,
            where = where,
            limit = limit,
            offset = offset,
            orderBy = orderBy
        )
        println("------> selectQuery = $selectQuery")
        return QueryExecuter.executeQuery<E, S>(
            dao = dao,
            query = selectQuery,
            errorCode = ERROR_CODE_SELECT_WHERE,
            methodName = "selectWhere"
        )
    }

    /*suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> selectAsync(
        dao: IDao,
        where: String = "",
        limit: Int = 0
    ): List<E> {
        return withContext(Dispatchers.IO) { select<E, S>(dao, where, limit) }
    }*/

    inline fun <reified E : Any, reified S : StatusSelectTable<E>> selectResult(
        dao: IDao,
        where: String = "",
        limit: Int = 0,
        offset: Int = 0,
        orderBy: String = ""
    ): Result<List<E>> {
        val selectQuery = QueryBuilder.createQuery(
            dao = dao,
            prefix = QueryBuilder.SELECT_QUERY,
            where = where,
            limit = limit,
            offset = offset,
            orderBy = orderBy
        )
        return QueryExecuter.executeResultQuery<E, S>(
            dao = dao,
            query = selectQuery,
            errorCode = ERROR_CODE_SELECT_WHERE,
            methodName = "selectWhere"
        )
    }


    ///// Create tables
    inline fun <reified E : Any, reified S : StatusSelectTable<E>> createTable(
        dao: IDao.IFieldsDao
    ): S {
        dao.initFields<E>()
        val query = QueryBuilder.createTableQuery(dao)
        return QueryExecuter.executeStatus(
            dao = dao,
            query = query,
            errorCode = ERROR_CODE_CREATE,
            methodName = "createTable"
        )
    }

    /*suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> createTableAsync(
        dao: IDao.IFieldsDao
    ): S {
        return withContext(Dispatchers.IO) {
            dao.initFieldsAsync<E>()
            createTable<E, S>(dao)
        }
    }*/

    /////---------- DELETE QUERIES
    inline fun <reified E : Any, reified S : StatusSelectTable<E>> delete(
        dao: IDao.IFieldsDao,
        where: String = "",
        notifyAll: Boolean = false
    ): S {
        val deleteQuery = QueryBuilder.createQuery(dao, QueryBuilder.DELETE_QUERY, where)
        return QueryExecuter.executeStatus(
            dao = dao,
            query = deleteQuery,
            errorCode = ERROR_CODE_REMOVE_WHERE,
            methodName = "delete",
            notifyAll = notifyAll
        )
    }

    /*suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> deleteAsync(
        dao: IDao.IFieldsDao,
        where: String = "",
        notifyAll: Boolean = true
    ): S {
        return withContext(Dispatchers.IO) {  delete<E, S>(dao, where, notifyAll) }
    }*/

    inline fun <reified E : Any, reified S : StatusSelectTable<E>> delete(
        dao: IDao.IFieldsDao,
        item: E,
        notifyAll: Boolean = false
    ): S {
        val query = QueryBuilder.createDeleteQuery(dao, item)
        return QueryExecuter.executeStatus(
            dao = dao,
            query = query,
            errorCode = ERROR_CODE_DELETE,
            methodName = "delete",
            notifyAll = notifyAll
        )
    }

    /*suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> deleteAsync(
        dao: IDao.IFieldsDao,
        item: E,
        notifyAll: Boolean = false
    ): S {
        return withContext(Dispatchers.IO) { delete<E, S>(dao, item, notifyAll) }
    }*/

    inline fun <reified E : Any, reified S : StatusSelectTable<E>> delete(
        dao: IDao.IFieldsDao,
        items: List<E>,
        notifyAll: Boolean = false
    ): S {
        val query = QueryBuilder.createDeleteQuery(dao, items)
        return QueryExecuter.executeTransactionStatus(
            dao = dao,
            query = query,
            errorCode = ERROR_CODE_DELETE,
            methodName = "deleteList",
            notifyAll = notifyAll
        )
    }

    /*suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> deleteAsync(
        dao: IDao.IFieldsDao,
        items: List<E>,
        notifyAll: Boolean = false
    ): S {
        return withContext(Dispatchers.IO) { delete<E, S>(dao, items, notifyAll) }
    }*/

    ////--------- UPDATE QUERIES
    inline fun <reified E : Any, reified S : StatusSelectTable<E>> update(
        dao: IDao.IFieldsDao,
        setQuery: String,
        from: String = "",
        where: String = "",
        notifyAll: Boolean = false
    ): S {
        val updateQuery = QueryBuilder.createUpdateQuery(dao, setQuery, from, where)
        return QueryExecuter.executeStatus(
            dao = dao,
            query = updateQuery,
            errorCode = ERROR_CODE_UPDATE,
            methodName = "update",
            notifyAll = notifyAll
        )
    }

    ////--------- INSERT QUERIES
    inline fun <reified E : Any, reified S : StatusSelectTable<E>> insertOrReplace(
        dao: IDao.IFieldsDao,
        item: E,
        notifyAll: Boolean = false
    ): S {
        val query = QueryBuilder.createInsertOrReplaceQuery(dao, item)
        return QueryExecuter.executeStatus(
            dao = dao,
            query = query,
            errorCode = ERROR_CODE_INSERT,
            methodName = "insertOrReplace",
            notifyAll
        )
    }

    /*suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> insertOrReplaceAsync(
        dao: IDao.IFieldsDao,
        item: E,
        notifyAll: Boolean = false
    ): S {
        return withContext(Dispatchers.IO) { insertOrReplace<E, S>(dao, item, notifyAll) }
    }*/

    inline fun <reified E : Any, reified S : StatusSelectTable<E>> insertOrReplace(
        dao: IDao.IFieldsDao,
        items: List<E>,
        notifyAll: Boolean = false
    ): S {
        val query = QueryBuilder.createInsertOrReplaceQuery(dao, items)
        return QueryExecuter.executeTransactionStatus<E, S>(
            dao = dao,
            query = query,
            errorCode = ERROR_CODE_INSERT,
            methodName = "insertOrReplaceList",
            notifyAll = notifyAll
        )
    }

   /* suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> insertOrReplaceAsync(
        dao: IDao.IFieldsDao,
        items: List<E>,
        notifyAll: Boolean = false
    ): S {
        return withContext(Dispatchers.IO) { insertOrReplace<E, S>(dao, items, notifyAll) }
    }*/

}