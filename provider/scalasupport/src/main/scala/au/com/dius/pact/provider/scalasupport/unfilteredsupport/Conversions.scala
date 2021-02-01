package au.com.dius.pact.provider.scalasupport.unfilteredsupport

import java.io.{BufferedReader, InputStreamReader}
import java.net.URI
import java.util.zip.GZIPInputStream
import com.typesafe.scalalogging.StrictLogging
import io.netty.handler.codec.http.{HttpResponse => NHttpResponse}
import io.pact.core.model.{ContentType, OptionalBody, Request, Response}
import unfiltered.netty.ReceivedMessage
import unfiltered.request.HttpRequest
import unfiltered.response.{ContentEncoding, HttpResponse, ResponseFunction, ResponseString, Status}

import scala.jdk.CollectionConverters._
import scala.collection.immutable.Stream

object Conversions extends StrictLogging {

  case class Headers(headers: java.util.Map[String, java.util.List[String]]) extends unfiltered.response.Responder[Any] {
    def respond(res: HttpResponse[Any]) {
      if (headers != null) {
        headers.asScala.foreach { case (key, value) => res.header(key, value.asScala.mkString(", ")) }
      }
    }
  }

  implicit def pactToUnfilteredResponse(response: Response): ResponseFunction[NHttpResponse] = {
    if (response.getBody.isPresent) {
      Status(response.getStatus) ~> Headers(response.getHeaders) ~> ResponseString(response.getBody.valueAsString())
    } else Status(response.getStatus) ~> Headers(response.getHeaders)
  }

  def toHeaders(request: HttpRequest[ReceivedMessage]): java.util.Map[String, java.util.List[String]] = {
    request.headerNames.map(name => name -> request.headers(name).toList.asJava).toMap.asJava
  }

  def toQuery(request: HttpRequest[ReceivedMessage]): java.util.Map[String, java.util.List[String]] = {
    request.parameterNames.map(name => name -> request.parameterValues(name).asJava).toMap.asJava
  }

  def toPath(uri: String) = new URI(uri).getPath

  def toBody(request: HttpRequest[ReceivedMessage], charset: String = "UTF-8") = {
    val br = if (request.headers(ContentEncoding.GZip.name).contains("gzip")) {
      new BufferedReader(new InputStreamReader(new GZIPInputStream(request.inputStream)))
    } else {
      new BufferedReader(request.reader)
    }
    Stream.continually(br.readLine()).takeWhile(_ != null).mkString("\n")
  }

  implicit def unfilteredRequestToPactRequest(request: HttpRequest[ReceivedMessage]): Request = {
    val contentType = new ContentType(request.headers("Content-Type").next())
    new Request(request.method, toPath(request.uri), toQuery(request), toHeaders(request),
      OptionalBody.body(toBody(request).getBytes(contentType.asCharset), contentType))
  }
}
