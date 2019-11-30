package de.ioswarm.hyperion.http

import java.util.UUID

import akka.actor.Props
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.util.TupleOps.Join
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Sink
import de.ioswarm.hyperion.Service.ServiceReceive
import de.ioswarm.hyperion.model.AuthenticatedUser
import de.ioswarm.hyperion.{AppendableService, AppendableServiceFacade, Service, ServiceContext, ServiceOptions}
import de.ioswarm.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure => UFailure, Success => USuccess}

object CRUD {

  sealed trait CRUDCommand[L, R, E]
  final case class ListEntities[L, R, E](params: L, from: Int, size: Int, filter: Map[String, String], user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class CreateEntity[L, R, E](params: L, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class ReadEntity[L, R, E](params: R, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class UpdateEntity[L, R, E](params: R, oldEntity: E, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class DeleteEntity[L, R, E](params: R, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]

  final case class Result[E](entity: Option[E])
  final case class ListResult[E](entities: Vector[E])

  sealed trait CRUDEvent[L, R, E]
  final case class EntityCreated[L, R, E](params: L, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDEvent[L, R, E]
  final case class EntityUpdated[L, R, E](params: R, oldEntity: E, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDEvent[L, R, E]
  final case class EntityDeleted[L, R, E](params: R, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDEvent[L, R, E]

  type CRUDList[L, R, E] = ServiceContext => ListEntities[L, R, E] => Future[Vector[E]]
  type CRUDCreate[L, R, E] = ServiceContext => CreateEntity[L, R, E] => Future[Option[E]]
  type CRUDRead[L, R, E] = ServiceContext => ReadEntity[L, R, E] => Future[Option[E]]
  type CRUDUpdate[L, R, E] = ServiceContext => UpdateEntity[L, R, E] => Future[Option[E]]
  type CRUDDelete[L, R, E] = ServiceContext => DeleteEntity[L, R, E] => Future[Option[E]]

  def emptyCRUDList[L, R, E]: CRUDList[L, R, E] = { _ => _ => Future.successful(Vector.empty)}
  def emptyCRUDCreate[L, R, E]: CRUDCreate[L, R, E] = { _ => _ => Future.successful(None)}
  def emptyCRUDRead[L, R, E]: CRUDRead[L, R, E] = { _ => _ => Future.successful(None)}
  def emptyCRUDUpdate[L, R, E]: CRUDUpdate[L, R, E] = { _ => _ => Future.successful(None)}
  def emptyCRUDDelete[L, R, E]: CRUDDelete[L, R, E] = { _ => _ => Future.successful(None)}

  type AdditionalRoute[T] = T => Service.ServiceRoute

  /*implicit class _ServiceExtender(val s: Service) {

    def crud[L, E](pm: PathMatcher[L], entityClass: Class[E]): Service = s

  }*/

  implicit class _PathMatcherExtender[L](val pm: PathMatcher[L]) extends AnyVal {

    import akka.http.scaladsl.server.PathMatchers.Segment

    def crudOf[E](implicit unmarshaller: FromEntityUnmarshaller[E], marshaller: ToEntityMarshaller[E], listMarshaller: ToEntityMarshaller[Vector[E]], join: Join[L, Tuple1[String]]): DefaultCRUDService[L, join.Out, E] = DefaultCRUDService(
      UUID.randomUUID().toString
      , pm
      , pm / Segment
      , unmarshaller
      , marshaller
      , listMarshaller
    )

  }

  trait CRUDService[L, R, E] extends AppendableService {

    def pathMatcher: PathMatcher[L]
    def innerPathMatcher: PathMatcher[R]

    def unmarshaller: FromEntityUnmarshaller[E]
    def marshaller: ToEntityMarshaller[E]
    def listMarshaller: ToEntityMarshaller[Vector[E]]

    def onList: CRUDList[L, R, E]
    def onCreate: CRUDCreate[L, R, E]
    def onRead:   CRUDRead[L, R, E]
    def onUpdate: CRUDUpdate[L, R, E]
    def onDelete: CRUDDelete[L, R, E]

    def listTimeout: FiniteDuration
    def createTimeout: FiniteDuration
    def readTimeout: FiniteDuration
    def updateTimeout: FiniteDuration
    def deleteTimeout: FiniteDuration

    def eventConsumer: Option[Sink[CRUDEvent[L, R, E], _]]

    def authenticate: Authenticate.AuthenticationMethod
    def authenticator: ContextualAuthenticator[AuthenticatedUser]

    def outerRoute: AdditionalRoute[L]
    def innerRoute: AdditionalRoute[R]

    def receive: Service.ServiceReceive
  }

  trait CRUDServiceFacade[L, R, E, A <: CRUDServiceFacade[L, R, E, A]] extends CRUDService[L, R, E] with AppendableServiceFacade[A] {
    def withUnmarshaller(um: FromEntityUnmarshaller[E]): A
    def withMarshaller(m: ToEntityMarshaller[E]): A
    def withListMarshaller(lm: ToEntityMarshaller[Vector[E]]): A

    def withOnList(ol: CRUDList[L, R, E]): A
    def withOnCreate(oc: CRUDCreate[L, R, E]): A
    def withOnRead(or: CRUDRead[L, R, E]): A
    def withOnUpdate(ou: CRUDUpdate[L, R, E]): A
    def withOnDelete(od: CRUDDelete[L, R, E]): A

    def withListTimeout(timeout: FiniteDuration): A
    def withCreateTimeout(timeout: FiniteDuration): A
    def withReadTimeout(timeout: FiniteDuration): A
    def withUpdateTimeout(timeout: FiniteDuration): A
    def withDeleteTimeout(timeout: FiniteDuration): A

    def append(f: PathMatcher[R] => Service): A

    def withEventConsumer(sink: Sink[CRUDEvent[L, R, E], _]): A

    def withAuthenticate(am: Authenticate.AuthenticationMethod): A
    def withAuthenticator(a: ContextualAuthenticator[AuthenticatedUser]): A

    def withOuterRoute(f: AdditionalRoute[L]): A
    def withInnerRoute(f: AdditionalRoute[R]): A

    def withReceive(rec: Service.ServiceReceive): A

  }

  final case class DefaultCRUDService[L, R, E](
                                           name: String
                                           , pathMatcher: PathMatcher[L]
                                           , innerPathMatcher: PathMatcher[R]
                                           , unmarshaller: FromEntityUnmarshaller[E]
                                           , marshaller: ToEntityMarshaller[E]
                                           , listMarshaller: ToEntityMarshaller[Vector[E]]
                                           , onList: CRUDList[L, R, E] = emptyCRUDList[L, R, E]
                                           , onCreate: CRUDCreate[L, R, E] = emptyCRUDCreate[L, R, E]
                                           , onRead: CRUDRead[L, R, E] = emptyCRUDRead[L, R, E]
                                           , onUpdate: CRUDUpdate[L, R, E] = emptyCRUDUpdate[L, R, E]
                                           , onDelete: CRUDDelete[L, R, E] = emptyCRUDDelete[L, R, E]
                                           , listTimeout: FiniteDuration = 5.seconds   // TODO load defaults from config
                                           , createTimeout: FiniteDuration = 500.millis
                                           , readTimeout: FiniteDuration = 500.millis
                                           , updateTimeout: FiniteDuration = 500.millis
                                           , deleteTimeout: FiniteDuration = 500.millis
                                           , receive: Service.ServiceReceive = Service.emptyBehavior
                                           , options: ServiceOptions = ServiceOptions(actorClass = classOf[DefaultCRUDServiceActor[L, R, E]], dispatcher = "crud-dispatcher")
                                           , eventConsumer: Option[Sink[CRUDEvent[L, R, E], _]] = None
                                           , authenticate: Authenticate.AuthenticationMethod = Authenticate.NONE
                                           , authenticator: ContextualAuthenticator[AuthenticatedUser] = noneAuthenticator
                                           , outerRoute: AdditionalRoute[L] = (_: L) => Service.emptyRoute
                                           , innerRoute: AdditionalRoute[R] = (_: R) => Service.emptyRoute
                                           , children: List[Service] = List.empty
                                           ) extends CRUDServiceFacade[L, R, E, DefaultCRUDService[L, R, E]] {

    import Routes._

    override def props: Props = Props(options.actorClass, this)
      .withDispatcher(options.dispatcher)
      .withMailbox(options.mailbox)
      .withRouter(options.routerConfig)

    override def route: ServiceRoute = { ref =>
      import akka.pattern.ask
      import akka.util.Timeout
      import akka.http.scaladsl.model.StatusCodes._

      def crudRoute(user: Option[AuthenticatedUser]): Route = pathPrefix(pathMatcher).tapply { t =>
        pathEndOrSingleSlash {
          get {
            implicit val timeout: Timeout = listTimeout
            implicit val m: ToEntityMarshaller[Vector[E]] = listMarshaller
            parameters('from ? 0, 'size ? 100) { (from, size) =>
              parameterMap { qryParams =>
                onComplete((ref ? ListEntities(t, from, size, qryParams.filterKeys(key => key != "from" && key != "size"), user)).mapTo[ListResult[E]]) {
                  case USuccess(result) => complete(OK, result.entities)
                  case UFailure(t) => failWith(t)
                }
              }
            }
          } ~
          post {
            implicit val timeout: Timeout = createTimeout
            implicit val m: ToEntityMarshaller[E] = marshaller
            implicit val um: FromEntityUnmarshaller[E] = unmarshaller
            entity(as[Option[E]]) {
              case Some(entity) =>
                onComplete((ref ? CreateEntity(t, entity, user)).mapTo[Result[E]]) {
                  case USuccess(result) => result.entity match {
                    case Some(res) => complete(Created, res)
                    case None => complete(NotFound) // TODO result message
                  }
                  case UFailure(t) => failWith(t) // TODO result message, catch timeout exceptions for special status-code
                }
              case None => complete(BadRequest) // TODO result message
            }
          }
        } ~ outerRoute(t)(ref)
      } ~
      pathPrefix(innerPathMatcher).tapply { t =>
        pathEndOrSingleSlash {
          // Read
          get {
            implicit val timeout: Timeout = readTimeout
            implicit val m: ToEntityMarshaller[E] = marshaller
            onComplete((ref ? ReadEntity(t, user)).mapTo[Result[E]]) {
              case USuccess(result) => result.entity match {
                case Some(res) => complete(OK, res)
                case None => complete(NotFound) // TODO result message
              }
              case UFailure(t) => failWith(t) // TODO result message, catch timeout exceptions for special status-code
            }
          } ~
          // Update
          put {
            implicit val timeout: Timeout = updateTimeout
            implicit val m: ToEntityMarshaller[E] = marshaller
            implicit val um: FromEntityUnmarshaller[E] = unmarshaller
            entity(as[Option[E]]) {
              case Some(entity) =>
                onComplete((ref ? ReadEntity(t, user)).mapTo[Result[E]]) {
                  case USuccess(rresult) => rresult.entity match {
                    case Some(oldE) =>
                      onComplete((ref ? UpdateEntity(t, oldE, entity, user)).mapTo[Result[E]]) {
                        case USuccess(result) => result.entity match {
                          case Some(res) => complete(OK, res)
                          case None => complete(NotFound) // TODO result message
                        }
                        case UFailure(t) => failWith(t) // TODO result message, catch timeout exceptions for special status-code
                      }
                    case None => complete(NotFound) // TODO result message
                  }
                  case UFailure(t) => failWith(t)
                }
              case None => complete(BadRequest) // TODO result message
            }
          } ~
          // Delete
          delete {
            implicit val timeout: Timeout = deleteTimeout
            implicit val m: ToEntityMarshaller[E] = marshaller
            onComplete((ref ? ReadEntity(t, user)).mapTo[Result[E]]) {
              case USuccess(result) => result.entity match {
                case Some(entity) =>
                  onComplete((ref ? DeleteEntity(t, entity, user)).mapTo[Result[E]]) {
                    case USuccess(deletion) => deletion.entity match {
                      case Some(res) => complete(OK, res)
                      case None => complete(NotFound) // TODO result message
                    }
                    case UFailure(t) => failWith(t) // TODO result message, catch timeout exceptions for special status-code
                  }
                case None => complete(NotFound) // TODO result message
              }
              case UFailure(t) => failWith(t) // TODO result message, catch timeout exceptions for special status-code
            }
          }
        } ~ innerRoute(t)(ref)
      }

      authenticate match {
        case Authenticate.BASIC =>
          extractContext { ctx =>
            authenticateBasicAsync("realm", authenticator(ctx)) { auser => // TODO configure realm
              crudRoute(Some(auser))
            }
          }
        case Authenticate.OAUTH2 =>
          extractContext { ctx =>
            authenticateOAuth2Async("realm", authenticator(ctx)) { auser => // TODO configure realm
              crudRoute(Some(auser))
            }
          }
        case _ => crudRoute(None)
      }
    }

    override def withName(n: String): DefaultCRUDService[L, R, E] = copy(name = n)

    override def withRoute(r: ServiceRoute): DefaultCRUDService[L, R, E] = throw new IllegalArgumentException()

    override def withOptions(opt: ServiceOptions): DefaultCRUDService[L, R, E] = copy(options = opt)

    override def withUnmarshaller(um: FromEntityUnmarshaller[E]): DefaultCRUDService[L, R, E] = copy(unmarshaller = um)

    override def withMarshaller(m: ToEntityMarshaller[E]): DefaultCRUDService[L, R, E] = copy(marshaller = m)

    override def withListMarshaller(lm: ToEntityMarshaller[Vector[E]]): DefaultCRUDService[L, R, E] = copy(listMarshaller = lm)

    override def withOnList(ol: CRUDList[L, R, E]): DefaultCRUDService[L, R, E] = copy(onList = ol)

    override def withOnCreate(oc: CRUDCreate[L, R, E]): DefaultCRUDService[L, R, E] = copy(onCreate = oc)

    override def withOnRead(or: CRUDRead[L, R, E]): DefaultCRUDService[L, R, E] = copy(onRead = or)

    override def withOnUpdate(ou: CRUDUpdate[L, R, E]): DefaultCRUDService[L, R, E] = copy(onUpdate = ou)

    override def withOnDelete(od: CRUDDelete[L, R, E]): DefaultCRUDService[L, R, E] = copy(onDelete = od)

    def withOnList(tl: (CRUDList[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onList = tl._1, listTimeout = tl._2)

    def withOnCreate(tc: (CRUDCreate[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onCreate = tc._1, createTimeout = tc._2)

    def withOnRead(tr: (CRUDRead[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onRead = tr._1, readTimeout = tr._2)

    def withOnUpdate(tu: (CRUDUpdate[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onUpdate = tu._1, updateTimeout = tu._2)

    def withOnDelete(td: (CRUDDelete[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onDelete = td._1, deleteTimeout = td._2)

    override def appendService(child: Service): DefaultCRUDService[L, R, E] = copy(children = children :+ child)

    override def append(f: PathMatcher[R] => Service): DefaultCRUDService[L, R, E] = appendService(f(innerPathMatcher))

    override def withListTimeout(timeout: FiniteDuration): DefaultCRUDService[L, R, E] = copy(listTimeout = timeout)

    override def withCreateTimeout(timeout: FiniteDuration): DefaultCRUDService[L, R, E] = copy(createTimeout = timeout)

    override def withReadTimeout(timeout: FiniteDuration): DefaultCRUDService[L, R, E] = copy(readTimeout = timeout)

    override def withUpdateTimeout(timeout: FiniteDuration): DefaultCRUDService[L, R, E] = copy(updateTimeout = timeout)

    override def withDeleteTimeout(timeout: FiniteDuration): DefaultCRUDService[L, R, E] = copy(deleteTimeout = timeout)

    override def withEventConsumer(sink: Sink[CRUDEvent[L, R, E], _]): DefaultCRUDService[L, R, E] = copy(eventConsumer = Some(sink))

    override def withAuthenticate(am: Authenticate.AuthenticationMethod): DefaultCRUDService[L, R, E] = copy(authenticate = am)

    override def withAuthenticator(a: ContextualAuthenticator[AuthenticatedUser]): DefaultCRUDService[L, R, E] = copy(authenticator = a)

    override def withOuterRoute(f: AdditionalRoute[L]): DefaultCRUDService[L, R, E] = copy(outerRoute = f)

    override def withInnerRoute(f: AdditionalRoute[R]): DefaultCRUDService[L, R, E] = copy(innerRoute = f)

    override def withReceive(rec: ServiceReceive): DefaultCRUDService[L, R, E] = copy(receive = rec)
  }

}
