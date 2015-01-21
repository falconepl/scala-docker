package se.marcuslonnberg.scaladocker.remote.api

import akka.actor.ActorSystem
import akka.http.Http
import akka.http.marshalling.Marshaller
import akka.http.marshalling.Marshalling.WithFixedCharset
import akka.http.model.Uri.{Path, Query}
import akka.http.model._
import akka.http.unmarshalling.FromResponseUnmarshaller
import akka.stream.scaladsl._
import akka.stream.{FlattenStrategy, FlowMaterializer}
import akka.util.Timeout
import org.reactivestreams.Publisher

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait DockerPipeline extends JsonSupport {
  private[api] def baseUri: Uri

  private[api] def createUri(path: Path, query: Query): Uri = {
    baseUri.withPath(baseUri.path ++ path).withQuery(query)
  }

  private[api] def sendGetRequest(path: Uri.Path, query: Uri.Query = Uri.Query.Empty)
                                 (implicit system: ActorSystem,
                                  executionContext: ExecutionContext,
                                  materializer: FlowMaterializer): Future[HttpResponse] = {
    val uri = createUri(path, query)
    sendRequest(HttpRequest(HttpMethods.GET, uri))
  }

  private[api] def sendPostRequest[F, T](path: Uri.Path,
                                         query: Uri.Query = Uri.Query.Empty,
                                         content: F)
                                        (implicit system: ActorSystem,
                                         marshaller: Marshaller[F, RequestEntity],
                                         materializer: FlowMaterializer): Future[HttpResponse] = {
    import system.dispatcher
    val uri = createUri(path, query)
    val eventualEntity = createEntity(content)
    eventualEntity.flatMap { entity =>
      val httpRequest = HttpRequest(HttpMethods.POST, uri, entity = entity)
      sendRequest(httpRequest)
    }
  }

  private[api] def createEntity[T, F](content: F)(implicit executionContext: ExecutionContext,
                                                  marshaller: Marshaller[F, RequestEntity]): Future[RequestEntity] = {
    marshaller(content).map { xs =>
      xs.head match {
        case marshalling: WithFixedCharset[RequestEntity] =>
          marshalling.marshal().withContentType(MediaTypes.`application/json`)
        case marshalling =>
          sys.error(s"Unsupported marshalling: $marshalling")
      }
    }
  }

  private[api] def sendAndUnmarshal[T](httpRequest: HttpRequest)
                                      (implicit system: ActorSystem,
                                       executionContext: ExecutionContext,
                                       manifest: Manifest[T],
                                       unmarshaller: FromResponseUnmarshaller[T],
                                       materializer: FlowMaterializer): Future[T] = {
    sendRequest(httpRequest).flatMap { response =>
      unmarshaller(response)
    }
  }

  private[api] def sendRequest(request: HttpRequest)
                              (implicit system: ActorSystem, materializer: FlowMaterializer,
                               timeout: Timeout = Timeout(30.seconds)): Future[HttpResponse] = {
    val connection = Http(system)
      .outgoingConnection(request.uri.authority.host.address(), request.uri.effectivePort)

    Source.single(request)
      .via(connection.flow)
      .runWith(HeadSink())
  }

  private[api] def requestChunkedLines(httpRequest: HttpRequest)
                                      (implicit system: ActorSystem, materializer: FlowMaterializer): Publisher[String] = {
    val eventualResponse = sendRequest(httpRequest)

    val responseToChunks: Flow[HttpResponse, Source[String]] = Flow[HttpResponse].map { response =>
      response.entity match {
        case HttpEntity.Chunked(contentType, chunks) =>
          chunks.map(_.data().utf8String)
      }
    }

    Source(eventualResponse)
      .via(responseToChunks)
      .flatten(FlattenStrategy.concat)
      .runWith(PublisherSink())
  }
}