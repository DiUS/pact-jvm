package io.pact.provider.specs2

import io.pact.core.matchers.{FullResponseMatch, ResponseMatching}
import io.pact.core.model.{DefaultPactReader, RequestResponsePact}
import io.pact.provider.scalasupport.HttpClient
import org.specs2.Specification
import org.specs2.execute.Result
import org.specs2.specification.core.Fragments

import java.io.{File, InputStream, Reader, StringReader}
import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.jdk.CollectionConverters._

trait PactInput
case class StringInput(string: String) extends PactInput
case class ReaderInput(reader: Reader) extends PactInput
case class StreamInput(stream: InputStream) extends PactInput
case class FileInput(file: File) extends PactInput

trait ProviderSpec extends Specification {

  def timeout = Duration.apply(10000, "s")

  def convertInput(input: PactInput) = {
    input match {
      case StringInput(string) => new StringReader(string)
      case ReaderInput(reader) => reader
      case StreamInput(stream) => stream
      case FileInput(file) => file
    }
  }

  override def is = {
    val pact = DefaultPactReader.INSTANCE.loadPact(convertInput(honoursPact)).asInstanceOf[RequestResponsePact]
    val fs = pact.getInteractions.asScala.map { interaction =>
      val description = s"${interaction.getProviderStates.asScala.map(_.getName).mkString(", ")} ${interaction.getDescription}"
      val test: String => Result = { url =>
        implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
        val request = interaction.getRequest.copy
        request.setPath(s"$url${interaction.getRequest.getPath}")
        val actualResponseFuture = HttpClient.run(request)
        val actualResponse = Await.result(actualResponseFuture, timeout)
        ResponseMatching.matchRules(interaction.getResponse, actualResponse) must beEqualTo(FullResponseMatch.INSTANCE)
      }
      fragmentFactory.example(description, {inState(interaction.getProviderStates.asScala.headOption.map(_.getName).getOrElse(""), test)})
    }.toSeq
    Fragments(fs :_*)
  }

  def honoursPact: PactInput

  def inState(state: String, test: String => Result): Result

  implicit def steamToPactInput(source: InputStream) : PactInput = StreamInput(source)
  implicit def stringToPactInput(source: String) : PactInput = StringInput(source)
  implicit def readerToPactInput(source: Reader) : PactInput = ReaderInput(source)
  implicit def fileToPactInput(source: File) : PactInput = FileInput(source)

}
