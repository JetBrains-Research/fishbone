package org.jetbrains.bio.fishbone.predicate

import gnu.trove.map.TObjectIntMap
import gnu.trove.map.hash.TObjectIntHashMap
import org.jetbrains.bio.dataframe.BitList
import org.jetbrains.bio.dataframe.DataFrame
import org.jetbrains.bio.dataframe.DataFrameMappers
import org.jetbrains.bio.dataframe.DataFrameSpec
import org.jetbrains.bio.util.Progress
import org.jetbrains.bio.util.bufferedReader
import org.jetbrains.bio.util.bufferedWriter
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*


object PredicatesIO {
    internal val LOG = LoggerFactory.getLogger(PredicatesIO::class.java)

    private const val INDEX_KEY = "index"
    private const val NOT = "NOT: "
    private const val COMMENT = '#'


    fun <T> predicatesToDataFrame(dataBase: List<T>, predicates: List<Predicate<T>>): DataFrame {
        val progress = Progress {
            title = "Predicates to data frame"
        }.bounded(predicates.size.toLong())
        var df = DataFrame()
        predicates.forEach { predicate ->
            val result = predicate.test(dataBase)
            val mask = BitList(dataBase.size) { result[it] }
            progress.report()
            df = df.with(predicate.name().intern(), mask)
        }
        progress.done()
        return df
    }

    /**
     * Predicates can be loaded by [loadPredicates]
     */
    fun <T> savePredicates(
        path: Path,
        database: List<T>,
        predicates: List<Predicate<T>>,
        index: (T) -> String
    ) {
        val indexDf = DataFrame().with(INDEX_KEY, database.map { index(it) }.toTypedArray())
        val predicatesDf = predicatesToDataFrame(database, predicates)
        val df2save = if (predicates.isNotEmpty()) {
            DataFrame.columnBind(indexDf, predicatesDf)
        } else {
            indexDf
        }
        DataFrameMappers.TSV.save(
            path.bufferedWriter(), df2save,
            header = true,
            typed = false,
            comments = arrayOf(
                "File was generated by ${PredicatesIO::class.java.simpleName}. " +
                        "$NOT${predicates.joinToString("") { it.canNegate().mark().toString() }}"
            )
        )
    }

    /**
     * Load database and predicates saved by [savePredicates]
     */
    fun <T> loadPredicates(
        path: Path,
        resolver: (String) -> T?
    ): Pair<List<T>, List<Predicate<T>>> {
        LOG.info("Loading predicates $path")
        var names = emptyList<String>()
        var negatesInfo = emptyList<Boolean>()
        path.bufferedReader().use { reader ->
            val commentLine = reader.readLine()
            if (commentLine != null && commentLine.startsWith(COMMENT)) {
                negatesInfo = commentLine.substringAfter(NOT).map { it == true.mark() }
            } else {

                return@use
            }
            val headerLine = reader.readLine()
            if (headerLine == null || headerLine.startsWith(COMMENT)) {
                LOG.error("Unexpected file format $path: index $headerLine")
                return@use
            }
            val header = headerLine.split('\t')
            require(header.first() == INDEX_KEY) {
                "Unexpected file format $path: $INDEX_KEY required"
                return@use
            }
            names = header.subList(1, header.size)
            LOG.info("Found predicates ${if (names.size > 10) names.size.toString() else names.joinToString(", ")}")
        }

        val dataFrameSpec = DataFrameSpec()
        dataFrameSpec.strings(INDEX_KEY)
        names.forEach { dataFrameSpec.booleans(it) }
        val df = DataFrameMappers.TSV.load(path, spec = dataFrameSpec, header = true)

        // Common index for all the predicates
        val database = arrayListOf<T>()
        val indexMap = TObjectIntHashMap<T>()

        df.sliceAsObj<String>(INDEX_KEY).forEachIndexed { i, s ->
            val value = resolver(s)
            check(value != null) {
                LOG.error("Failed to resolve $s at line ${i + 2}")
            }
            indexMap.put(value, i)
            database.add(i, value)
        }
        val predicates = names.zip(negatesInfo).map { (name, negate) ->
            val positives = df.sliceAsBool(name.intern())
            LoadedPredicate<T>(name, negate, database, positives, indexMap).apply {
                // Check loaded predicates
                val cardinality = positives.cardinality()
                if (cardinality == 0 || cardinality == positives.size()) {
                    LOG.warn("Predicate $name is constantly ${positives[0]}!")
                }
            }
        }
        return database to predicates
    }


    /**
     * Predicate with [name], [canNegate], stores positive results in bitset and database indexes in map.
     * Is created by [loadPredicates] method.
     */
    class LoadedPredicate<T>(
        private val originalName: String,
        private val originalCanNegate: Boolean,
        private val database: List<T>,
        private val positives: BitList,
        private val indexMap: TObjectIntMap<T>
    ) : Predicate<T>() {
        override fun name() = originalName

        override fun defined() = true

        override fun canNegate() = originalCanNegate

        override fun test(item: T): Boolean {
            if (!indexMap.containsKey(item)) {
                throw NoSuchElementException("$item missing in loaded predicate ${toString()}")
            }
            return positives[indexMap[item]]
        }

        @Synchronized
        override fun test(items: List<T>): BitSet {
            // IMPORTANT: we use reference equality instead of Lists equality,
            // because check of database on each test can be slow for large databases,
            // and result will be cached in super method call.
            if (items === database) {
                return positives
            }
            LOG.debug("Loaded predicate should be checked against loaded database for performance reasons")
            return super.test(items)
        }


        override fun toString() = "Loaded[${name()}]"
    }
}

fun Boolean.mark() = if (this) '1' else '0'
