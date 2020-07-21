/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
@file:Suppress("DEPRECATION")

package ai.pedro.fever

import java.io.File

import kotlinx.serialization.*
import kotlinx.serialization.json.*

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.document.StoredField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.RAMDirectory

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import kotlin.streams.toList


@Serializable
data class Evidence(
        val wikipedia_url: String?,
        val sentence_id: Int?
)

typealias EvidenceSet = List<Evidence>

@Serializable
data class FeverExample(
        val id: Int,
        val label: String,
        val claim: String,
        val evidence: List<EvidenceSet>
)


@Serializable
data class WikipediaPage(
        val id: String,
        val title: String,
        val text: String,
        val sentences: Map<Int, String>
)

@Serializable
data class RetrievedDoc(
        val wikipedia_url: String,
        val sentence_id: Int,
        val text: String,
        val score: Float
)

@Serializable
data class LucenePrediction(
        val claim_id: Int,
        val documents: List<RetrievedDoc>
)


fun parseFever(feverPath: String): List<FeverExample> {
    val json = Json(JsonConfiguration.Stable)
    val serializer = FeverExample.serializer()
    return File(feverPath)
            .readLines()
            .map {line -> json.parse(serializer, line)}
}


fun parseWikipedia(wikiPath: String): List<WikipediaPage> {
    val json = Json(JsonConfiguration.Stable)
    val serializer = WikipediaPage.serializer()
    return File(wikiPath)
            .readLines()
            .parallelStream()
            .map {line -> json.parse(serializer, line)}
            .toList()
}


class App: CliktCommand() {
    val wikiPath by argument("wiki_path")
    val feverPath by argument("fever_path")
    val outPath by argument("out_path")
    override fun run() {
        println("Wiki Path: $wikiPath Fever Path: $feverPath Out Path: $outPath")
        // Setup
        val analyzer = StandardAnalyzer()
        val index = RAMDirectory()
        val config = IndexWriterConfig(analyzer)
        val writer = IndexWriter(index, config)

        // Read Wikipedia
        println("Reading Wikipedia")
        val wikiPages = parseWikipedia(wikiPath)

        // Read Fever Data
        println("Reading Fever Examples")
        val examples = parseFever(feverPath)

        println("Creating Wikipedia Index")
        // addDocument is thread safe, so parallelize
        wikiPages.parallelStream().forEach { page ->
            for ((sentenceId, text) in page.sentences) {
                val doc = buildDoc(page.title, text, sentenceId)
                writer.addDocument(doc)
            }
        }
        writer.close()

        println("Getting Wikipedia Predictions")
        val fields = arrayOf("title", "text")
        val reader = DirectoryReader.open(index)
        val searcher = IndexSearcher(reader)
        val nDocs = 100
        val json = Json(JsonConfiguration.Stable)
        val outWriter = File(outPath).bufferedWriter()
        // write() is thread safe, so parallelize
        examples.parallelStream().forEach {
            val queryParser = MultiFieldQueryParser(fields, analyzer)
            val query = queryParser.parse(MultiFieldQueryParser.escape(it.claim))
            val docs = searcher.search(query, nDocs)
            val exPreds = mutableListOf<RetrievedDoc>()
            for (doc in docs.scoreDocs) {
                val document = searcher.doc(doc.doc)
                val title = document.get("title")
                val text = document.get("text")
                val sentenceId = document.get("sentence_id").toInt()
                exPreds.add(RetrievedDoc(title, sentenceId, text, doc.score))
            }
            // Careful to write as a single string for thread safety
            val out = json.stringify(
                    LucenePrediction.serializer(),
                    LucenePrediction(it.id, exPreds)
            ).plus("\n")
            outWriter.write(out)
        }
        reader.close()
        println("Done predicting and saving")
    }
}

fun buildDoc(title: String, text: String, sentenceId: Int): Document {
    val doc = Document()
    doc.add(TextField("title", title, Field.Store.YES))
    doc.add(TextField("text", text, Field.Store.YES))
    doc.add(StoredField("sentence_id", sentenceId))
    return doc
}

fun main(args: Array<String>) {
    App().main(args)
}
