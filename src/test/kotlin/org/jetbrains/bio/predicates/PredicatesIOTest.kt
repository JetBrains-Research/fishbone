package org.jetbrains.bio.predicates

import org.apache.log4j.Level
import org.jetbrains.bio.Logs
import org.jetbrains.bio.util.withTempFile
import org.junit.After
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredicatesIOTest {

    @After
    fun tearDown() {
        System.setOut(OUT)
        System.setErr(ERR)
    }

    @Test
    fun testSaveLoad() {
        val p0 = object : Predicate<Int>() {
            override fun test(item: Int): Boolean = true
            override fun name(): String = "True"
        }
        val p1 = object : Predicate<Int>() {
            override fun test(item: Int): Boolean = false
            override fun name(): String = "FalseNegate"
            override fun canNegate(): Boolean = false
        }
        val predicates = arrayListOf(p0, p1)
        val database = listOf(1, 2, 3, 4, 5)
        withTempFile("predicates", ".tsv") {
            PredicatesIO.savePredicates(it, database, predicates, Int::toString)
            val (loadedDataBase, loaded) = PredicatesIO.loadPredicates(it, String::toInt)
            assertEquals(database, loadedDataBase)
            assertEquals("[True, FalseNegate]", loaded.map { it.name() }.toString())
            database.forEach { assertEquals(predicates[0].test(it), loaded[0].test(it)) }
            database.forEach { assertEquals(predicates[1].test(it), loaded[1].test(it)) }
        }
    }

    @Test
    fun testSaveLoadEmpty() {
        val predicates = emptyList<Predicate<Int>>()
        val database = listOf(1, 2, 3, 4, 5)
        withTempFile("predicates", ".tsv") {
            PredicatesIO.savePredicates(it, database, predicates, Int::toString)
            val (_, loaded) = PredicatesIO.loadPredicates(it, String::toInt)
            assertTrue(loaded.isEmpty())
        }
    }

    @Test
    fun testLoadedPredicatesDatabaseCheck() {
        val stream = ByteArrayOutputStream()
        System.setOut(PrintStream(stream))
        // Update logging
        Logs.addConsoleAppender(Level.INFO)
        val predicates = listOf<Predicate<Int>>(object : Predicate<Int>() {
            override fun test(item: Int): Boolean = true
            override fun name(): String = "True"
        })
        val database = listOf(1, 2, 3, 4, 5)
        withTempFile("predicates", ".tsv") {
            PredicatesIO.savePredicates(it, database, predicates, Int::toString)
            val (loadedDb, loaded) = PredicatesIO.loadPredicates(it, String::toInt)
            assertEquals(5, loaded.first().test(database).cardinality())
            assertTrue("Loaded predicate should be checked against loaded database for performance reasons" in String(stream.toByteArray()))

            val stream = ByteArrayOutputStream()
            System.setOut(PrintStream(stream))
            // Update logging
            Logs.addConsoleAppender(Level.INFO)
            assertEquals(5, loaded.first().test(loadedDb).cardinality())
            assertFalse("Loaded predicate should be checked against loaded database for performance reasons" in String(stream.toByteArray()))
        }
    }

    companion object {
        private val OUT = System.out
        private val ERR = System.err
    }
}