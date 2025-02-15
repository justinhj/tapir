package tapir.examples

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import io.circe.generic.auto._
import tapir._
import tapir.docs.openapi._
import tapir.json.circe._
import tapir.openapi.OpenAPI
import tapir.openapi.circe.yaml._
import tapir.server.akkahttp._
import tapir.swagger.akkahttp.SwaggerAkka

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MultipleEndpointsDocumentationAkkaServer extends App {
  // endpoint descriptions
  case class Author(name: String)
  case class Book(title: String, year: Int, author: Author)

  val booksListing: Endpoint[Unit, Unit, Vector[Book], Nothing] = endpoint.get
    .in("books")
    .in("list" / "all")
    .out(jsonBody[Vector[Book]])

  val addBook: Endpoint[Book, Unit, Unit, Nothing] = endpoint.post
    .in("books")
    .in("add")
    .in(
      jsonBody[Book]
        .description("The book to add")
        .example(Book("Pride and Prejudice", 1813, Author("Jane Austen")))
    )

  // server-side logic
  val books = new AtomicReference(
    Vector(
      Book("The Sorrows of Young Werther", 1774, Author("Johann Wolfgang von Goethe")),
      Book("Iliad", -8000, Author("Homer")),
      Book("Nad Niemnem", 1888, Author("Eliza Orzeszkowa")),
      Book("The Colour of Magic", 1983, Author("Terry Pratchett")),
      Book("The Art of Computer Programming", 1968, Author("Donald Knuth")),
      Book("Pharaoh", 1897, Author("Boleslaw Prus"))
    )
  )

  val booksListingRoute = booksListing.toRoute(_ => Future.successful(Right(books.get())))
  val addBookRoute = addBook.toRoute(book => Future.successful(Right(books.getAndUpdate(books => books :+ book))))

  // generating the documentation in yml; extension methods come from imported packages
  val openApiDocs: OpenAPI = List(booksListing, addBook).toOpenAPI("The tapir library", "1.0.0")
  val openApiYml: String = openApiDocs.toYaml

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  try {
    import akka.http.scaladsl.server.Directives._
    Await.result(Http().bindAndHandle(booksListingRoute ~ addBookRoute ~ new SwaggerAkka(openApiYml).routes, "localhost", 8080), 1.minute)

    // testing
    println("Go to: http://localhost:8080/docs")
    println("Press any key to exit ...")
    scala.io.StdIn.readLine()
  } finally {
    // cleanup
    actorSystem.terminate()
  }
}
