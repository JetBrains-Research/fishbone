package org.jetbrains.bio.fishbone.rule

import com.google.common.collect.ObjectArrays
import com.google.gson.GsonBuilder
import org.apache.commons.csv.CSVFormat.DEFAULT
import org.apache.commons.csv.CSVRecord
import org.jetbrains.bio.fishbone.miner.FishboneMiner
import org.jetbrains.bio.fishbone.predicate.AndPredicate
import org.jetbrains.bio.fishbone.predicate.OrPredicate
import org.jetbrains.bio.fishbone.predicate.Predicate
import org.jetbrains.bio.fishbone.predicate.PredicateParser
import org.jetbrains.bio.fishbone.rule.log.RuleRecord
import org.jetbrains.bio.genome.data.DataConfig
import org.jetbrains.bio.util.bufferedWriter
import org.jetbrains.bio.util.deleteIfExists
import org.jetbrains.bio.util.toPath
import org.jetbrains.bio.util.write
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.IOException
import java.nio.file.Path


