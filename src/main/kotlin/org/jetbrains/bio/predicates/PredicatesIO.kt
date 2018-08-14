package org.jetbrains.bio.predicates

import org.apache.log4j.Logger
import org.jetbrains.bio.genome.GeneResolver
import org.jetbrains.bio.genome.GenomeQuery
import org.jetbrains.bio.genome.Transcript
import org.jetbrains.bio.statistics.data.BitterSet
import org.jetbrains.bio.statistics.data.DataFrame
import org.jetbrains.bio.statistics.data.DataFrameMapper
import org.jetbrains.bio.statistics.data.DataFrameSpec
import org.jetbrains.bio.util.Progress
import org.jetbrains.bio.util.bufferedReader
import org.jetbrains.bio.util.bufferedWriter
import java.nio.file.Path

object PredicatesIO {
    private val LOG = Logger.getLogger(PredicatesIO::class.java)

    private const val INDEX_KEY = "index"
    private const val NOT = "NOT: "
    private const val COMMENT = '#'

    fun Boolean.mark() = if (this) '1' else '0'


    fun <T> predicatesToDataFrame(dataBase: List<T>, predicates: List<Predicate<T>>): DataFrame {
        val progress = Progress {
            title = "Predicates to data frame"
        }.bounded(predicates.size.toLong())
        var df = DataFrame()
        predicates.forEach { predicate ->
            val result = predicate.test(dataBase)
            val mask = BitterSet(dataBase.size) { result[it] }
            progress.report()
            df = df.with(predicate.name().intern(), mask)
        }
        progress.done()
        return df
    }

    /**
     * Predicates can be loaded by [loadPredicates]
     */
    fun <T> savePredicates(path: Path,
                           database: List<T>,
                           predicates: List<Predicate<T>>,
                           index: (T) -> String) {
        val indexDf = DataFrame().with(INDEX_KEY, database.map { index(it) }.toTypedArray())
        val predicatesDf = predicatesToDataFrame(database, predicates)
        val df2save = if (predicates.isNotEmpty()) {
            DataFrame.columnBind(indexDf, predicatesDf)
        } else {
            indexDf
        }
        DataFrameMapper.TSV.save(path.bufferedWriter(), df2save,
                header = true,
                typed = false,
                comments = arrayOf("File was generated by ${PredicatesIO::class.java.simpleName}. " +
                        "$NOT${predicates.joinToString("") { it.canNegate().mark().toString() }}"))
    }

    /**
     * Loads predicates saved by [savePredicates]
     */
    fun <T> loadPredicates(path: Path,
                           resolver: (String) -> T?): List<Predicate<T>> {
        val comments = path.bufferedReader().useLines { lines ->
            lines.filter { it.startsWith(COMMENT) }.map { it.substringAfter(COMMENT).trim() }.toList()
        }
        check(comments.size == 1) { "Unexpected file format:\n${comments.joinToString("\n") { "#$it" }}" }
        val negatesInfo = comments.first().substringAfter(NOT).map { it == true.mark() }
        if (negatesInfo.isEmpty()) {
            return emptyList()
        }
        val names = path.bufferedReader().useLines {
            val header = it.take(2).last().split('\t')
            require(header.first() == INDEX_KEY) {
                "Unexpected file format: $INDEX_KEY required"
            }
            header.subList(1, header.size)
        }
        val dataFrameSpec = DataFrameSpec()
        dataFrameSpec.strings(INDEX_KEY)
        names.forEach { dataFrameSpec.bools(it) }
        val predicates = names.zip(negatesInfo).map {
            LoadedPredicate<T>(it.first, it.second)
        }

        val df = DataFrameMapper.TSV.load(path, spec = dataFrameSpec, header = true)
        val progress = Progress {
            title = "Predicates from data frame"
        }.bounded(negatesInfo.size.toLong())
        val index = df.sliceAsObj<String>(INDEX_KEY).map {
            val value = resolver(it)
            if (value == null) {
                LOG.warn("Unknown: $it")
            }
            value
        }
        predicates.forEach {
            val result = df.sliceAsBool(it.name().intern())
            val positives = it.positives
            result.iterator().forEach {
                val value = index[it]
                if (value != null) {
                    positives.add(value)
                }
            }
            progress.report()
        }
        progress.done()

        // Check loaded predicates
        predicates.forEach {
            if (it.positives.isEmpty()) {
                LOG.warn("Predicate ${it.name()} is always false!")
            }
        }
        LOG.debug("Loaded ${predicates.size} predicates $path")
        return predicates
    }


    /**
     * Loads predicates saved by [savePredicates]
     */
    fun loadPredicates(path: Path, genomeQuery: GenomeQuery): List<Predicate<Transcript>> {
        return loadPredicates(path, { GeneResolver.getAny(genomeQuery.build, it) })
    }

    /**
     * Predicate with [name], [canNegate], stores positive [test] results in the set.
     * Is created by [loadPredicates] method.
     */
    class LoadedPredicate<T>(private val originalName: String,
                             private val originalCanNegate: Boolean) : Predicate<T>() {
        val positives = hashSetOf<T>()

        override fun name() = originalName

        override fun defined() = true

        override fun canNegate() = originalCanNegate

        override fun test(item: T) = item in positives

        override fun toString() = "Loaded[${name()}]"
    }
}
