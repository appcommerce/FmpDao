// Copyright (c) 2021 Aleksandr Minkin aka Rasalexman (sphc@yandex.ru)
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software
// and associated documentation files (the "Software"), to deal in the Software without restriction,
// including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
// subject to the following conditions:
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
// WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
// WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
// THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package pro.krit.hiveprocessor.extensions

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.mobrun.plugin.api.request_assistant.PrimaryKey
import com.mobrun.plugin.models.StatusSelectTable
import pro.krit.hiveprocessor.base.IFmpLocalDao
import pro.krit.hiveprocessor.common.FieldsBuilder.getFields
import pro.krit.hiveprocessor.common.LocalDaoFields
import pro.krit.hiveprocessor.common.QueryBuilder
import pro.krit.hiveprocessor.common.QueryExecuter.checkStatusForTable
import pro.krit.hiveprocessor.common.QueryExecuter.executeStatus
import pro.krit.hiveprocessor.common.QueryExecuter.executeTransactionStatus
import java.lang.reflect.Field
import java.util.*

const val ERROR_CODE_CREATE = 10004
const val ERROR_CODE_INSERT = 10005
const val ERROR_CODE_DELETE = 10006

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.createTable(): S {
    val query = QueryBuilder.createTableQuery(this)
    return executeStatus(
        dao = this,
        query = query,
        errorCode = ERROR_CODE_CREATE,
        methodName = "createTable"
    )
}

suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.createTableAsync(): S {
    return createTable()
}

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.insertOrReplace(
    item: E
): S {
    val query = QueryBuilder.createInsertOrReplaceQuery(this, item)
    var status = executeStatus(
        dao = this,
        query = query,
        errorCode = ERROR_CODE_INSERT,
        methodName = "insertOrReplace"
    )
    val tableIsNotCreated = checkStatusForTable(this, status)
    if (tableIsNotCreated == null) {
        status = executeStatus(
            dao = this,
            query = query,
            errorCode = ERROR_CODE_INSERT,
            methodName = "insertOrReplace"
        )
    }
    return status
}

suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.insertOrReplaceAsync(
    item: E
): S {
    return insertOrReplace(item)
}

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.insertOrReplace(
    items: List<E>
): S {
    val query = QueryBuilder.createInsertOrReplaceQuery(this, items)
    var status = executeTransactionStatus(
        dao = this,
        query = query,
        errorCode = ERROR_CODE_INSERT,
        methodName = "insertOrReplaceList"
    )
    val tableIsNotCreated = checkStatusForTable(this, status)
    if (tableIsNotCreated == null) {
        status = executeTransactionStatus(
            dao = this,
            query = query,
            errorCode = ERROR_CODE_INSERT,
            methodName = "insertOrReplaceList"
        )
    }
    return status
}

suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.insertOrReplaceAsync(
    items: List<E>
): S {
    return insertOrReplace(items)
}

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.delete(item: E): S {
    val query = QueryBuilder.createDeleteQuery(this, item)
    return executeStatus(
        dao = this,
        query = query,
        errorCode = ERROR_CODE_DELETE,
        methodName = "delete"
    )
}

suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.deleteAsync(
    item: E
): S {
    return delete(item)
}

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.delete(items: List<E>): S {
    val query = QueryBuilder.createDeleteQuery(this, items)
    return executeTransactionStatus(
        dao = this,
        query = query,
        errorCode = ERROR_CODE_DELETE,
        methodName = "deleteList"
    )
}

suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.deleteAsync(
    items: List<E>
): S {
    return delete(items)
}

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IFmpLocalDao<E, S>.initFields() {
    var localPrimaryKey: Field? = null
    var localPrimaryKeyName: String? = null
    val localFields = ArrayList<Field>()
    val localFieldsNames = ArrayList<String>()
    var localFieldsForQuery: String? = null

    val fieldsClass = E::class.java.fields
    for (field in fieldsClass) {
        val skipExposeAnnotation = field.getAnnotation(Expose::class.java) != null
        if (skipExposeAnnotation) {
            continue
        }

        val primaryKeyAnnotation = field.getAnnotation(
            PrimaryKey::class.java
        )
        var isPrimary = false
        if (primaryKeyAnnotation != null) {
            if (localPrimaryKey != null) {
                throw UnsupportedOperationException("Not support multiple PrimaryKey")
            }
            isPrimary = true
        }
        val annotationSerializedName = field.getAnnotation(
            SerializedName::class.java
        )

        val name: String = annotationSerializedName?.value ?: field.name
        if (isPrimary) {
            localPrimaryKeyName = name
            localPrimaryKey = field
        } else {
            localFields.add(field)
            localFieldsNames.add(name)
        }
        localFieldsForQuery = getFields(localPrimaryKeyName, localFieldsNames)
    }

    localDaoFields = LocalDaoFields(
        fields = localFields,
        fieldsNames = localFieldsNames,
        primaryKeyField = localPrimaryKey,
        primaryKeyName = localPrimaryKeyName,
        fieldsForQuery = localFieldsForQuery
    )
}