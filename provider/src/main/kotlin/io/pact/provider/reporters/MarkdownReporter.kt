package io.pact.provider.reporters

import io.pact.core.matchers.BodyTypeMismatch
import io.pact.core.matchers.HeaderMismatch
import io.pact.core.model.BasePact
import io.pact.core.model.Interaction
import io.pact.core.model.Pact
import io.pact.core.model.PactSource
import io.pact.core.model.UrlPactSource
import io.pact.core.pactbroker.VerificationNotice
import io.pact.core.support.hasProperty
import io.pact.core.support.property
import io.pact.provider.BodyComparisonResult
import io.pact.provider.IConsumerInfo
import io.pact.provider.IProviderInfo
import io.pact.provider.IProviderVerifier
import io.pact.provider.VerificationResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.vladsch.flexmark.ast.Heading
import com.vladsch.flexmark.ext.tables.TableBlock
import com.vladsch.flexmark.ext.tables.TableBody
import com.vladsch.flexmark.ext.tables.TableCell
import com.vladsch.flexmark.ext.tables.TableRow
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.formatter.Formatter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.sequence.BasedSequence
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.time.ZonedDateTime

data class Event(
  val type: String,
  val contents: String,
  val data: List<Any?>
)

/**
 * Pact verifier reporter that displays the results of the verification in a markdown document
 */
@Suppress("EmptyFunctionBlock", "TooManyFunctions")
class MarkdownReporter(
  var name: String,
  override var reportDir: File?,
  override var ext: String
) : VerifierReporter {

  constructor(name: String, reportDir: File?) : this(name, reportDir, ".md")

  override lateinit var reportFile: File
  override lateinit var verifier: IProviderVerifier

  private lateinit var provider: IProviderInfo
  private val events = mutableListOf<Event>()

  init {
    if (reportDir == null) {
      reportDir = File(System.getProperty("user.dir"))
    }
    reportFile = File(reportDir, "$name$ext")
  }

  override fun initialise(provider: IProviderInfo) {
    this.provider = provider
    reportDir!!.mkdirs()
    reportFile = File(reportDir, provider.name + ext)
    events.clear()
  }

  override fun finaliseReport() {
    if (reportFile.exists()) {
      updateReportFile()
    } else {
      generateReportFile()
    }
  }

  private fun generateReportFile() {
    PrintWriter(BufferedWriter(FileWriter(reportFile, true))).use { pw ->
      pw.write("""
        # ${provider.name}
      
        | Description    | Value |
        | -------------- | ----- |
        | Date Generated | ${ZonedDateTime.now()} |
        | Pact Version   | ${BasePact.lookupVersion()} |
        
        ## Summary
        
        | Consumer    | Result |
        | ----------- | ------ |
        
      """.trimIndent())

      var consumer: IConsumerInfo? = null
      var state = "OK"
      for (event in events) {
        when (event.type) {
          "reportVerificationForConsumer" -> {
            if (consumer != null) {
              val pending = if (consumer.pending) " [Pending]" else ""
              pw.println("| ${consumer.name}$pending | $state |")
            }

            consumer = event.data[0] as IConsumerInfo
          }
          "stateChangeRequestFailedWithException", "stateChangeRequestFailed" -> state = "State change call failed"
          "requestFailed" -> state = "Request failed"
          "statusComparisonFailed", "headerComparisonFailed", "bodyComparisonFailed", "verificationFailed",
          "metadataComparisonFailed" -> state = "Failed"
        }
      }

      if (consumer != null) {
        val pending = if (consumer.pending) " [Pending]" else ""
        pw.println("| ${consumer.name}$pending | $state |")
      }

      pw.println()
      for (event in events) {
        pw.write(event.contents)
      }
    }
  }

  private fun updateReportFile() {
    val options = parserOptions()
    val parser = Parser.builder(options).build()
    val document = parser.parseReader(BufferedReader(FileReader(reportFile)))

    val (consumer: IConsumerInfo?, state) = consumerAndStatus(document)
    val header = events.find { it.type == "reportVerificationForConsumer" }?.contents?.substring(2)?.trim()
    if (consumer != null) {
      var consumerSection: Node? = null
      for (child in document.children) {
        if (child is Heading && child.text.unescape() == "Summary") {
          updateSummary(child.next, consumer, state)
        }

        if (child is Heading && child.text.contains(header.toString())) {
          consumerSection = child
        }
      }

      if (consumerSection == null) {
        for (event in events) {
          val section = parser.parseReader(StringReader(event.contents))
          document.appendChild(section)
        }
      } else {
        var child = consumerSection.next
        while (child != null && child !is Heading) {
          child = child.next
        }

        if (child == null) {
          for (event in events) {
            if (event.type != "reportVerificationForConsumer") {
              val section = parser.parseReader(StringReader(event.contents))
              document.appendChild(section)
            }
          }
        } else {
          for (event in events) {
            if (event.type != "reportVerificationForConsumer") {
              val section = parser.parseReader(StringReader(event.contents))
              child.insertBefore(section)
            }
          }
        }
      }
    }

    val formatter = Formatter.builder(options).build()
    BufferedWriter(FileWriter(reportFile)).use { w -> w.write(formatter.render(document)) }
  }

  private fun consumerAndStatus(document: Document): Pair<IConsumerInfo?, String> {
    var consumer: IConsumerInfo? = null
    var state = "OK"
    for (event in events) {
      when (event.type) {
        "reportVerificationForConsumer" -> {
          if (consumer != null) {
            for (child in document.children) {
              if (child is Heading && child.text.unescape() == "Summary") {
                updateSummary(child.next, consumer, state)
              }
            }
          }

          consumer = event.data[0] as IConsumerInfo
        }
        "stateChangeRequestFailedWithException", "stateChangeRequestFailed" -> state = "State change call failed"
        "requestFailed" -> state = "Request failed"
        "statusComparisonFailed", "headerComparisonFailed", "bodyComparisonFailed", "verificationFailed",
        "metadataComparisonFailed" -> state = "Failed"
      }
    }
    return Pair(consumer, state)
  }

  private fun parserOptions(): MutableDataSet {
    val options = MutableDataSet().set(Parser.EXTENSIONS, listOf(TablesExtension.create()))
      .set(TablesExtension.WITH_CAPTION, false)
      .set(TablesExtension.COLUMN_SPANS, false)
      .set(TablesExtension.MIN_HEADER_ROWS, 1)
      .set(TablesExtension.MAX_HEADER_ROWS, 1)
      .set(TablesExtension.APPEND_MISSING_COLUMNS, true)
      .set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
      .set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
    return options
  }

  private fun updateSummary(table: Node?, consumer: IConsumerInfo, state: String) {
    if (table is TableBlock) {
      for (child in table.children) {
        if (child is TableBody) {
          val consumerRow = child.children.find {
            it is TableRow && it.firstChild is TableCell && (it.firstChild as TableCell).text.startsWith(consumer.name)
          }
          if (consumerRow != null) {
            val stateCell = consumerRow.lastChild as TableCell
            stateCell.text = BasedSequence.of(state)
          } else {
            val row = TableRow()
            val pending = if (consumer.pending) " [Pending]" else ""
            val tableCellText = BasedSequence.of(consumer.name + pending)
            val tableCell = TableCell(tableCellText)
            tableCell.text = tableCellText
            row.appendChild(tableCell)
            val statusCell = TableCell(BasedSequence.of(state))
            statusCell.text = BasedSequence.of(state)
            row.appendChild(statusCell)
            child.appendChild(row)
          }
        }
      }
    }
  }

  override fun reportVerificationForConsumer(consumer: IConsumerInfo, provider: IProviderInfo, tag: String?) {
    val output = StringBuilder("## Verifying a pact between _${consumer.name}_")
    if (!consumer.name.contains(provider.name)) {
      output.append(" and _${provider.name}_")
    }
    if (tag != null) {
      output.append(" for tag $tag")
    }
    if (consumer.pending) {
      output.append(" [PENDING]")
    }
    output.append("\n\n")
    events.add(Event("reportVerificationForConsumer", output.toString(), listOf(consumer, provider, tag)))
  }

  override fun verifyConsumerFromUrl(pactUrl: UrlPactSource, consumer: IConsumerInfo) {
    events.add(Event("verifyConsumerFromUrl", "From `${pactUrl.description()}`<br/>\n",
      listOf(pactUrl, consumer)))
  }

  override fun verifyConsumerFromFile(pactFile: PactSource, consumer: IConsumerInfo) {
    events.add(Event("verifyConsumerFromFile", "From `${pactFile.description()}`<br/>\n",
      listOf(pactFile, consumer)))
  }

  override fun pactLoadFailureForConsumer(consumer: IConsumerInfo, message: String) { }

  override fun warnProviderHasNoConsumers(provider: IProviderInfo) { }

  override fun warnPactFileHasNoInteractions(pact: Pact) { }

  override fun interactionDescription(interaction: Interaction) {
    events.add(Event("interactionDescription", "${interaction.description}  <br/>\n", listOf(interaction)))
  }

  override fun stateForInteraction(state: String, provider: IProviderInfo, consumer: IConsumerInfo, isSetup: Boolean) {
    events.add(Event("stateForInteraction", "Given **$state**  <br/>\n",
      listOf(state, provider, consumer, isSetup)))
  }

  override fun warnStateChangeIgnored(state: String, provider: IProviderInfo, consumer: IConsumerInfo) {
    events.add(Event("warnStateChangeIgnored",
      "&nbsp;&nbsp;&nbsp;&nbsp;<span style=\'color: yellow\'>WARNING: State Change ignored as " +
        "there is no stateChange URL</span>  <br/>\n", listOf(state, provider, consumer)))
  }

  override fun stateChangeRequestFailedWithException(
    state: String,
    isSetup: Boolean,
    e: Exception,
    printStackTrace: Boolean
  ) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write("&nbsp;&nbsp;&nbsp;&nbsp;<span style='color: red'>State Change Request Failed - ${e.message}" +
      "</span>\n\n```\n")
    e.printStackTrace(pw)
    pw.write("\n```\n\n")
    pw.close()

    events.add(Event("stateChangeRequestFailedWithException", sw.toString(),
      listOf(state, isSetup, e, printStackTrace)))
  }

  override fun stateChangeRequestFailed(state: String, provider: IProviderInfo, isSetup: Boolean, httpStatus: String) {
    events.add(Event("stateChangeRequestFailedWithException",
      "&nbsp;&nbsp;&nbsp;&nbsp;<span style='color: red'>State Change Request Failed - $httpStatus" +
        "</span>  \n", listOf(state, provider, isSetup, httpStatus)))
  }

  override fun warnStateChangeIgnoredDueToInvalidUrl(
    state: String,
    provider: IProviderInfo,
    isSetup: Boolean,
    stateChangeHandler: Any
  ) {
    events.add(Event("warnStateChangeIgnoredDueToInvalidUrl",
      "&nbsp;&nbsp;&nbsp;&nbsp;<span style=\'color: yellow\'>WARNING: State Change ignored as " +
        "there is no stateChange URL, received `$stateChangeHandler`</span>  <br/>\n",
      listOf(state, provider, isSetup, stateChangeHandler)))
  }

  override fun requestFailed(
    provider: IProviderInfo,
    interaction: Interaction,
    interactionMessage: String,
    e: Exception,
    printStackTrace: Boolean
  ) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write("&nbsp;&nbsp;&nbsp;&nbsp;<span style='color: red'>Request Failed - ${e.message}</span>\n\n```\n")
    e.printStackTrace(pw)
    pw.write("\n```\n\n")
    pw.close()

    events.add(Event("requestFailed", sw.toString(), listOf(provider, interaction, interactionMessage, e,
      printStackTrace)))
  }

  override fun returnsAResponseWhich() {
    events.add(Event("returnsAResponseWhich", "&nbsp;&nbsp;returns a response which  <br/>\n", listOf()))
  }

  override fun statusComparisonOk(status: Int) {
    events.add(Event("statusComparisonOk", "&nbsp;&nbsp;&nbsp;&nbsp;has status code **$status** " +
      "(<span style='color:green'>OK</span>)  <br/>\n", listOf(status)))
  }

  override fun statusComparisonFailed(status: Int, comparison: Any) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write("&nbsp;&nbsp;&nbsp;&nbsp;has status code **$status** " +
      "(<span style='color:red'>FAILED</span>)\n\n```\n")
    if (comparison.hasProperty("message")) {
      pw.write(comparison.property("message")?.get(comparison).toString())
    } else {
      pw.write(comparison.toString())
    }
    pw.write("\n```\n\n")
    pw.close()

    events.add(Event("statusComparisonFailed", sw.toString(), listOf(status, comparison)))
  }

  override fun includesHeaders() {
    events.add(Event("includesHeaders", "&nbsp;&nbsp;&nbsp;&nbsp;includes headers  <br/>\n", listOf()))
  }

  override fun headerComparisonOk(key: String, value: List<String>) {
    events.add(Event("headerComparisonOk",
      "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"**$key**\" with value \"**$value**\" " +
        "(<span style=\'color:green\'>OK</span>)  <br/>\n", listOf(key, value)))
  }

  override fun headerComparisonFailed(key: String, value: List<String>, comparison: Any) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"**$key**\" with value \"**$value**\" " +
      "(<span style=\'color:red\'>FAILED</span>)  \n\n```\n")
    when (comparison) {
      is List<*> -> comparison.forEach {
        when (it) {
          is HeaderMismatch -> pw.write(it.mismatch)
          else -> pw.write(it.toString())
        }
      }
      else -> pw.write(comparison.toString())
    }
    pw.write("\n```\n\n")
    pw.close()

    events.add(Event("headerComparisonFailed", sw.toString(), listOf(key, value, comparison)))
  }

  override fun bodyComparisonOk() {
    events.add(Event("bodyComparisonOk",
      "&nbsp;&nbsp;&nbsp;&nbsp;has a matching body (<span style='color:green'>OK</span>)  <br/>\n", listOf()))
  }

  override fun bodyComparisonFailed(comparison: Any) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write("&nbsp;&nbsp;&nbsp;&nbsp;has a matching body (<span style='color:red'>FAILED</span>)  \n\n")

    when (comparison) {
      is Err<*> -> {
        comparison as Err<BodyTypeMismatch>
        pw.write("```\n${comparison.error.description()}\n```\n")
      }
      is Ok<*> -> {
        comparison as Ok<BodyComparisonResult>
        pw.write("| Path | Failure |\n")
        pw.write("| ---- | ------- |\n")
        comparison.value.mismatches.forEach { (path, mismatches) ->
          pw.write("|`$path`|${mismatches.first().description()}|\n")
          if (mismatches.size > 1) {
            mismatches.drop(1).forEach {
              pw.write("||${it.description()}|\n")
            }
          }
        }
        pw.write("\n\nDiff:\n\n")
        renderDiff(pw, comparison.value.diff)
        pw.write("\n\n")
      }
      else -> pw.write("```\n${comparison}\n```\n")
    }
    pw.close()
    events.add(Event("bodyComparisonFailed", sw.toString(), listOf(comparison)))
  }

  private fun renderDiff(pw: PrintWriter, diff: Any?) {
    pw.write("```diff\n")
    if (diff is List<*>) {
      pw.write(diff.joinToString("\n"))
    } else {
      pw.write(diff.toString())
    }
    pw.write("\n```\n")
  }

  override fun errorHasNoAnnotatedMethodsFoundForInteraction(interaction: Interaction) { }

  override fun verificationFailed(interaction: Interaction, e: Exception, printStackTrace: Boolean) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write("&nbsp;&nbsp;&nbsp;&nbsp;<span style='color: red'>Verification Failed - ${e.message}</span>\n\n```\n")
    e.printStackTrace(pw)
    pw.write("\n```\n\n")
    pw.close()
    events.add(Event("verificationFailed", sw.toString(), listOf(interaction, e, printStackTrace)))
  }

  override fun generatesAMessageWhich() {
    events.add(Event("generatesAMessageWhich", "&nbsp;&nbsp;generates a message which  <br/>\n", listOf()))
  }

  override fun displayFailures(failures: Map<String, Any>) { }

  override fun displayFailures(failures: List<VerificationResult.Failed>) { }

  override fun metadataComparisonFailed(key: String, value: Any?, comparison: Any) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"**$key**\" with value \"**$value**\" " +
      "(<span style=\'color:red\'>FAILED</span>)  \n")
    pw.write("\n```\n$comparison\n```\n\n")
    pw.close()
    events.add(Event("metadataComparisonFailed", sw.toString(), listOf(key, value, comparison)))
  }

  override fun includesMetadata() {
    events.add(Event("includesMetadata", "&nbsp;&nbsp;&nbsp;&nbsp;includes metadata  <br/>\n", listOf()))
  }

  override fun metadataComparisonOk(key: String, value: Any?) {
    events.add(Event("metadataComparisonOk",
      "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\"**$key**\" with value \"**$value**\" " +
        "(<span style=\'color:green\'>OK</span>)  <br/>\n", listOf(key, value)))
  }

  override fun metadataComparisonOk() {
    events.add(Event("metadataComparisonOk",
      "&nbsp;&nbsp;&nbsp;&nbsp;has matching metadata (<span style='color:green'>OK</span>)<br/>\n", listOf()))
  }

  override fun reportVerificationNoticesForConsumer(
    consumer: IConsumerInfo,
    provider: IProviderInfo,
    notices: List<VerificationNotice>
  ) {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write("Notices:\n")
    notices.forEachIndexed { i, notice -> pw.write("${i + 1}. ${notice.text}\n") }
    pw.write("\n")
    pw.close()
    events.add(Event("reportVerificationNoticesForConsumer", sw.toString(), listOf(consumer, provider, notices)))
  }

  override fun warnPublishResultsSkippedBecauseFiltered() {
    events.add(Event("warnPublishResultsSkippedBecauseFiltered",
      "NOTE: Skipping publishing of verification results as the interactions have been filtered<br/>\n", listOf()))
  }

  override fun warnPublishResultsSkippedBecauseDisabled(envVar: String) {
    events.add(Event("warnPublishResultsSkippedBecauseDisabled",
      "NOTE: Skipping publishing of verification results as it has been disabled ($envVar is not 'true')<br/>\n",
      listOf(envVar)))
  }
}
