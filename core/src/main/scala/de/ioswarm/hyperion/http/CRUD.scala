package de.ioswarm.hyperion.http

import java.util.UUID

import akka.actor.Props
import akka.http.scaladsl.server.util.TupleOps.Join
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.stream.scaladsl.Sink
import argonaut.{DecodeJson, EncodeJson}
import com.typesafe.config.ConfigFactory
import de.ioswarm.hyperion.Service.ServiceReceive
import de.ioswarm.hyperion.json.JsonUtils
import de.ioswarm.hyperion.model.AuthenticatedUser
import de.ioswarm.hyperion.{AppendableService, AppendableServiceFacade, Service, ServiceContext, ServiceOptions}
import de.ioswarm.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure => UFailure, Success => USuccess}

object CRUD {

  private lazy val conf = ConfigFactory.load().getConfig("hyperion.http.crud")
  implicit def _crudJavaDurationToScalaDuration(duration: java.time.Duration): FiniteDuration = Duration.fromNanos(duration.toNanos)

  sealed trait CRUDCommand[L, R, E]
  final case class ListEntities[L, R, E](params: L, listOptions: ListOptions, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class CreateEntity[L, R, E](params: L, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class ReadEntity[L, R, E](params: R, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class UpdateEntity[L, R, E](params: R, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class PatchEntity[L, R, E](params: R, ops: PatchOperations, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]
  final case class DeleteEntity[L, R, E](params: R, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDCommand[L, R, E]

  sealed trait CRUDEvent[L, R, E]
  final case class EntityCreated[L, R, E](params: L, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDEvent[L, R, E]
  final case class EntityUpdated[L, R, E](params: R, oldEntity: E, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDEvent[L, R, E]
  final case class EntityPatched[L, R, E](params: R, oldEntity: E, entity: E, ops: PatchOperations, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDEvent[L, R, E]
  final case class EntityDeleted[L, R, E](params: R, entity: E, user: Option[AuthenticatedUser], timestamp: DateTime = DateTime()) extends CRUDEvent[L, R, E]

  type CRUDList[L, R, E] = ServiceContext => ListEntities[L, R, E] => Future[ListResult[E]]
  type CRUDCreate[L, R, E] = ServiceContext => CreateEntity[L, R, E] => Future[Option[E]]
  type CRUDRead[L, R, E] = ServiceContext => ReadEntity[L, R, E] => Future[Option[E]]
  type CRUDUpdate[L, R, E] = ServiceContext => UpdateEntity[L, R, E] => Future[Option[E]]
  type CRUDPatch[L, R, E] = ServiceContext => PatchEntity[L, R, E] => Future[Option[E]]
  type CRUDDelete[L, R, E] = ServiceContext => DeleteEntity[L, R, E] => Future[Option[Long]]

  def emptyCRUDList[L, R, E]: CRUDList[L, R, E] = { _ => le => Future.successful(ListResult(le.listOptions.from, Some(0), Vector.empty))}
  def emptyCRUDCreate[L, R, E]: CRUDCreate[L, R, E] = { _ => _ => Future.successful(None)}
  def emptyCRUDRead[L, R, E]: CRUDRead[L, R, E] = { _ => _ => Future.successful(None)}
  def emptyCRUDUpdate[L, R, E]: CRUDUpdate[L, R, E] = { _ => _ => Future.successful(None)}
  def emptyCRUDPatch[L, R, E]: CRUDPatch[L, R, E] = { _ => _ => Future.successful(None)}
  def emptyCRUDDelete[L, R, E]: CRUDDelete[L, R, E] = { _ => _ => Future.successful(None)}

  type AdditionalRoute[T] = T => Service.ServiceRoute

  implicit class _PathMatcherExtender[L](val pm: PathMatcher[L]) extends AnyVal {

    import akka.http.scaladsl.server.PathMatchers.Segment

    def crudOf[E](name: String)(implicit encoder: EncodeJson[E], decoder: DecodeJson[E], join: Join[L, Tuple1[String]]): DefaultCRUDService[L, join.Out, E] = DefaultCRUDService(
      name
      , pm
      , pm / Segment
      , encoder
      , decoder
    )

    def crudOf[E](implicit encoder: EncodeJson[E], decoder: DecodeJson[E], join: Join[L, Tuple1[String]]): DefaultCRUDService[L, join.Out, E] = crudOf(UUID.randomUUID().toString)(encoder, decoder, join)

  }

  trait CRUDService[L, R, E] extends AppendableService {

    def pathMatcher: PathMatcher[L]
    def innerPathMatcher: PathMatcher[R]

    def encoder: EncodeJson[E]
    def decoder: DecodeJson[E]

    def onList: CRUDList[L, R, E]
    def onCreate: CRUDCreate[L, R, E]
    def onRead:   CRUDRead[L, R, E]
    def onUpdate: CRUDUpdate[L, R, E]
    def onPatch: CRUDPatch[L, R, E]
    def onDelete: CRUDDelete[L, R, E]

    def listTimeout: FiniteDuration
    def createTimeout: FiniteDuration
    def readTimeout: FiniteDuration
    def updateTimeout: FiniteDuration
    def patchTimeout: FiniteDuration
    def deleteTimeout: FiniteDuration

    def eventConsumer: Option[Sink[CRUDEvent[L, R, E], _]]

    def authenticate: Authenticate.AuthenticationMethod
    def authenticator: ContextualAuthenticator[AuthenticatedUser]

    def outerRoute: AdditionalRoute[L]
    def innerRoute: AdditionalRoute[R]

    def receive: Service.ServiceReceive
  }

  trait CRUDServiceFacade[L, R, E, A <: CRUDServiceFacade[L, R, E, A]] extends CRUDService[L, R, E] with AppendableServiceFacade[A] {
    def withEncoder(enc: EncodeJson[E]): A
    def withDecoder(dec: DecodeJson[E]): A

    def withOnList(ol: CRUDList[L, R, E]): A
    def withOnCreate(oc: CRUDCreate[L, R, E]): A
    def withOnRead(or: CRUDRead[L, R, E]): A
    def withOnUpdate(ou: CRUDUpdate[L, R, E]): A
    def withOnPatch(op: CRUDPatch[L, R, E]): A
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
                                           , encoder: EncodeJson[E]
                                           , decoder: DecodeJson[E]
                                           , onList: CRUDList[L, R, E] = emptyCRUDList[L, R, E]
                                           , onCreate: CRUDCreate[L, R, E] = emptyCRUDCreate[L, R, E]
                                           , onRead: CRUDRead[L, R, E] = emptyCRUDRead[L, R, E]
                                           , onUpdate: CRUDUpdate[L, R, E] = emptyCRUDUpdate[L, R, E]
                                           , onPatch: CRUDPatch[L, R, E] = emptyCRUDPatch[L, R, E]
                                           , onDelete: CRUDDelete[L, R, E] = emptyCRUDDelete[L, R, E]
                                           , listTimeout: FiniteDuration = conf.getDuration("listTimeout")
                                           , createTimeout: FiniteDuration = conf.getDuration("createTimeout")
                                           , readTimeout: FiniteDuration = conf.getDuration("readTimeout")
                                           , updateTimeout: FiniteDuration = conf.getDuration("updateTimeout")
                                           , patchTimeout: FiniteDuration = conf.getDuration("patchTimeout")
                                           , deleteTimeout: FiniteDuration = conf.getDuration("deleteTimeout")
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
      import de.heikoseeberger.akkahttpargonaut.ArgonautSupport._

      implicit val entityEncoder: EncodeJson[E] = encoder
      implicit val entityDecoder: DecodeJson[E] = decoder

      def crudRoute(user: Option[AuthenticatedUser]): Route = pathPrefix(pathMatcher).tapply { t =>
        pathEndOrSingleSlash {
          get {
            implicit val timeout: Timeout = listTimeout

            parameters('from.as[Long] ? 0, 'size.as[Long].?, 'filter.?, 'attributes.?) { (from, size, filter, attributes) =>
              val at = attributes.map(_.split(",").filter(_.length != 0).toList)
              onComplete((ref ? ListEntities(t, ListOptions(from, size, filter, at.getOrElse(List.empty)), user)).mapTo[ListResult[E]]) {
                  case USuccess(result) => at match {
                    case Some(attrs) if attrs.nonEmpty =>
                      import argonaut._
                      import Argonaut._
                      complete(OK, result.map(r => JsonUtils.extractAttributes(r.asJson, attrs:_*)))
                    case _ => complete(OK, result)
                  }
                  case UFailure(t) => failWith(t)
              }
            }
          } ~
          post {
            implicit val timeout: Timeout = createTimeout

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
        } ~
        pathPrefix(".search") {
          post {
            implicit val timeout: Timeout = listTimeout

            entity(as[Option[ListOptions]]) {
              case Some(lo) =>
                onComplete((ref ? ListEntities(t, lo, user)).mapTo[ListResult[E]]) {
                  case USuccess(result) => lo.attributes match {
                    case attrs if attrs.nonEmpty =>
                      import argonaut._
                      import Argonaut._
                      complete(OK, result.map(r => JsonUtils.extractAttributes(r.asJson, attrs:_*)))
                    case _ => complete(OK, result)
                  }
                  case UFailure(t) => failWith(t)
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

            entity(as[Option[E]]) {
              case Some(entity) =>
                onComplete((ref ? UpdateEntity(t, entity, user)).mapTo[Result[E]]) {
                  case USuccess(result) => result.entity match {
                    case Some(res) => complete(OK, res)
                    case None => complete(NotFound) // TODO result message
                  }
                  case UFailure(t) => failWith(t) // TODO result message, catch timeout exceptions for special status-code
                }
              case None => complete(BadRequest) // TODO result message
            }
          } ~
          patch {
            implicit val timeout: Timeout = patchTimeout

            entity(as[PatchOperations]) {
              case ops if ops.operations.nonEmpty =>
                onComplete((ref ? PatchEntity(t, ops, user)).mapTo[Result[E]]) {
                  case USuccess(result) => result.entity match {
                    case Some(res) => complete(OK, res)
                    case None => complete(NotFound) // TODO result message
                  }
                  case UFailure(t) => failWith(t) // TODO result message, catch timeout exceptions for special status-code
                }
              case _ => complete(BadRequest) // TODO result message
            }
          } ~
          // Delete
          delete {
            implicit val timeout: Timeout = deleteTimeout

            onComplete((ref ? DeleteEntity(t, user)).mapTo[Result[E]]) {
              case USuccess(deletion) => deletion.entity match {
                case Some(res) => complete(OK, res)
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

    override def withEncoder(enc: EncodeJson[E]): DefaultCRUDService[L, R, E] = copy(encoder = enc)

    override def withDecoder(dec: DecodeJson[E]): DefaultCRUDService[L, R ,E] = copy(decoder = dec)

    override def withOnList(ol: CRUDList[L, R, E]): DefaultCRUDService[L, R, E] = copy(onList = ol)

    override def withOnCreate(oc: CRUDCreate[L, R, E]): DefaultCRUDService[L, R, E] = copy(onCreate = oc)

    override def withOnRead(or: CRUDRead[L, R, E]): DefaultCRUDService[L, R, E] = copy(onRead = or)

    override def withOnUpdate(ou: CRUDUpdate[L, R, E]): DefaultCRUDService[L, R, E] = copy(onUpdate = ou)

    override def withOnPatch(op: CRUDPatch[L, R, E]): DefaultCRUDService[L, R, E] = copy(onPatch = op)

    override def withOnDelete(od: CRUDDelete[L, R, E]): DefaultCRUDService[L, R, E] = copy(onDelete = od)

    def withOnList(tl: (CRUDList[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onList = tl._1, listTimeout = tl._2)

    def withOnCreate(tc: (CRUDCreate[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onCreate = tc._1, createTimeout = tc._2)

    def withOnRead(tr: (CRUDRead[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onRead = tr._1, readTimeout = tr._2)

    def withOnUpdate(tu: (CRUDUpdate[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onUpdate = tu._1, updateTimeout = tu._2)

    def withOnPatch(tu: (CRUDPatch[L, R, E], FiniteDuration)): DefaultCRUDService[L, R, E] = copy(onPatch = tu._1, patchTimeout = tu._2)

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
