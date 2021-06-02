package pro.krit.hiveprocessor.common

import com.mobrun.plugin.models.StatusSelectTable
import pro.krit.hiveprocessor.base.IFmpLocalDao
import pro.krit.hiveprocessor.extensions.tableName

object QueryBuilder {

    const val SELECT_QUERY = "SELECT * FROM"
    const val DELETE_QUERY = "DELETE FROM"

    const val BEGIN_TRANSACTION_QUERY = "BEGIN TRANSACTION;"
    const val END_TRANSACTION_QUERY = "END TRANSACTION;"

    const val WHERE = "WHERE"
    const val LIMIT = "LIMIT"

    private const val CREATE_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS "
    private const val TEXT_PRIMARY_KEY = " TEXT PRIMARY KEY NOT NULL"
    private const val TEXT_FIELD_TYPE = " TEXT"

    private const val INSERT_OR_REPLACE = "INSERT OR REPLACE INTO "
    private const val VALUES = " VALUES "

    fun <E : Any, S : StatusSelectTable<E>> createTableQuery(dao: IFmpLocalDao<E, S>): String {
        val localDaoFields = dao.localDaoFields
        val fieldsNames = localDaoFields?.fieldsNames
        if (fieldsNames.isNullOrEmpty()) {
            throw UnsupportedOperationException("No 'fieldsNames' key for operation 'createTableQuery'")
        }
        val primaryKeyName = localDaoFields.primaryKeyName

        var prefix = ""
        val localFieldNames = fieldsNames.orEmpty()
        return buildString {
            append(CREATE_TABLE_QUERY)
            append(dao.tableName)
            append(" (")

            primaryKeyName?.let {
                append(it)
                append(TEXT_PRIMARY_KEY)
                prefix = ", "
            }

            for (fieldName in localFieldNames) {
                append(prefix)
                append(fieldName)
                append(TEXT_FIELD_TYPE)
                prefix = ", "
            }
            append(")")
        }
    }

    fun <E : Any, S : StatusSelectTable<E>> createInsertOrReplaceQuery(
        dao: IFmpLocalDao<E, S>,
        item: E
    ): String {
        val fieldsForQuery = dao.localDaoFields?.fieldsForQuery
            ?: throw UnsupportedOperationException("No 'fieldsForQuery' key for operation 'createInsertOrReplaceQuery'")

        val fieldsValues = FieldsBuilder.getValues(dao, item)
        return "$INSERT_OR_REPLACE${dao.tableName} $fieldsForQuery$VALUES$fieldsValues"
    }

    fun <E : Any, S : StatusSelectTable<E>> createInsertOrReplaceQuery(dao: IFmpLocalDao<E, S>, items: List<E>): String {
        val stringBuilder = StringBuilder()
        for (item in items) {
            stringBuilder.append(createInsertOrReplaceQuery(dao, item))
                .append("; ")
        }
        return stringBuilder.toString()
    }

    fun <E : Any, S : StatusSelectTable<E>> createDeleteQuery(
        dao: IFmpLocalDao<E, S>,
        item: E
    ): String {
        val localDaoFields = dao.localDaoFields
        val primaryKeyField = localDaoFields?.primaryKeyField
        val primaryKeyName = localDaoFields?.primaryKeyName
        if (primaryKeyField == null || primaryKeyName.isNullOrEmpty()) {
            throw UnsupportedOperationException("No 'primaryKeyField' for operation 'createDeleteQuery'")
        }
        val keyValue: Any = try {
            primaryKeyField[item]
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            throw UnsupportedOperationException(e.message)
        }
        return "$DELETE_QUERY ${dao.tableName} $WHERE $primaryKeyName = '$keyValue'"
    }

    fun <E : Any, S : StatusSelectTable<E>> createDeleteQuery(dao: IFmpLocalDao<E, S>, items: List<E>): String {
        val stringBuilder = StringBuilder()
        for (item in items) {
            stringBuilder.append(createDeleteQuery(dao, item))
                .append("; ")
        }
        return stringBuilder.toString()
    }
}