package org.jetbrains.bio.predicates

import org.jetbrains.bio.util.withTempFile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredicatesIOTest {

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
}