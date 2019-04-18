package org.jetbrains.bio.util.ciofani

import joptsimple.BuiltinHelpFormatter
import joptsimple.OptionParser
import org.apache.log4j.Logger
import org.jetbrains.bio.util.ciofani.query.AllTfsToIrf4Query
import org.jetbrains.bio.util.ciofani.query.AllTfsToIrf4QueryWithTr
import org.jetbrains.bio.util.ciofani.query.AllTfsToRorcQuery
import org.jetbrains.bio.util.parse

class CiofaniBedFilesCreator {

    fun createBedFilesForQuery(
        query: CiofaniCheckQuery, filePath: String, outputFolder: String, prefix: String
    ): CiofaniBedFilesInfo {
        val databaseFilename = prefix + "_database.bed"
        CiofaniBedFilesCreator.LOG.info("Started processing files")

        val sources = CiofaniTFsOutputFileParser.parseSources(filePath, query, outputFolder)
        CiofaniBedFilesCreator.LOG.info("Sources parsed into $sources")
        val targets = CiofaniTFsOutputFileParser.parseTarget(filePath, query, outputFolder)
        CiofaniBedFilesCreator.LOG.info("Targets parsed into $targets")
        val database = CiofaniTFsOutputFileParser.parseDatabase(filePath, databaseFilename, outputFolder)
        CiofaniBedFilesCreator.LOG.info("Database parsed into $database")
        return CiofaniBedFilesInfo(sources, targets, database.toString())
    }

    companion object {
        private val LOG = Logger.getLogger(CiofaniBedFilesCreator::class.java)
        private val queries = mapOf(
            "allTfsToRorcQuery" to AllTfsToRorcQuery(),
            "allTfsToIrf4Query" to AllTfsToIrf4Query(),
            "allTfsToIrf4QueryWithTr" to AllTfsToIrf4QueryWithTr()
        )
        private val bedFilesCreator = CiofaniBedFilesCreator()

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            OptionParser().apply {
                accepts("query", "Query to build bed files").withRequiredArg()
                accepts("file", "Path to ciofani output txt file").withRequiredArg()
                accepts("output", "Output folder").withRequiredArg()
                accepts("prefix", "Prefix to use in output filenames").withRequiredArg()

                formatHelpWith(BuiltinHelpFormatter(200, 2))
            }.parse(args) { options ->
                val query = options.valueOf("query").toString()
                if (!queries.containsKey(query)) {
                    LOG.error("Invalid query name $query. Should be one of $queries.keys")
                } else {
                    bedFilesCreator.createBedFilesForQuery(
                        queries.getValue(query),
                        options.valueOf("file").toString(),
                        options.valueOf("output").toString(),
                        options.valueOf("prefix").toString()
                    )
                }
            }
        }

    }
}