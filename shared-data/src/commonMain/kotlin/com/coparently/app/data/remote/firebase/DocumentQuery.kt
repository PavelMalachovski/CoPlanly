package com.coparently.app.data.remote.firebase

/**
 * A query over one top-level collection, as data: the filters, the order and the bound, each in
 * the order it was added, so the client builds the SDK query exactly as the data source used to.
 *
 * A filter's value is a plain value ([FirestoreValues]): text, a number, a boolean or a
 * [FirestoreTimestamp].
 */
data class DocumentQuery(
    val collection: String,
    val filters: List<QueryFilter> = emptyList(),
    val order: List<QueryOrder> = emptyList(),
    val limitToLast: Long? = null
) {
    /** Documents whose [field] equals [value]. */
    fun whereEqualTo(field: String, value: Any?): DocumentQuery = where(field, FilterOp.EQUAL_TO, value)

    /** Documents whose array [field] contains [value]. */
    fun whereArrayContains(field: String, value: Any): DocumentQuery = where(field, FilterOp.ARRAY_CONTAINS, value)

    /** Documents whose [field] is greater than [value]. */
    fun whereGreaterThan(field: String, value: Any): DocumentQuery = where(field, FilterOp.GREATER_THAN, value)

    /** Documents whose [field] is at least [value]. */
    fun whereGreaterThanOrEqualTo(field: String, value: Any): DocumentQuery =
        where(field, FilterOp.GREATER_THAN_OR_EQUAL_TO, value)

    /** Documents whose [field] is less than [value]. */
    fun whereLessThan(field: String, value: Any): DocumentQuery = where(field, FilterOp.LESS_THAN, value)

    /** Ordered by [field], ascending unless [descending]. */
    fun orderBy(field: String, descending: Boolean = false): DocumentQuery =
        copy(order = order + QueryOrder(field, descending))

    /** Only the last [limit] documents of the order. */
    fun limitToLast(limit: Long): DocumentQuery = copy(limitToLast = limit)

    private fun where(field: String, op: FilterOp, value: Any?): DocumentQuery =
        copy(filters = filters + QueryFilter(field, op, value))
}

/** One `where` of a [DocumentQuery]. */
data class QueryFilter(val field: String, val op: FilterOp, val value: Any?)

/** The comparisons the data sources use. */
enum class FilterOp {
    EQUAL_TO,
    ARRAY_CONTAINS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL_TO,
    LESS_THAN
}

/** One `orderBy` of a [DocumentQuery]. */
data class QueryOrder(val field: String, val descending: Boolean)
