package de.frosner.broccoli.controllers

import java.util.concurrent.TimeUnit
import javax.inject.{Named, Inject}

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import de.frosner.broccoli.models.InstanceStatus.InstanceStatus
import de.frosner.broccoli.models.InstanceStatus.InstanceStatus
import de.frosner.broccoli.models.{InstanceStatus, InstanceCreation, Instance}
import Instance.{instanceReads, instanceWrites}
import InstanceCreation.{instanceCreationReads, instanceCreationWrites}
import de.frosner.broccoli.services.InstanceService
import de.frosner.broccoli.services.InstanceService.{SetStatus, NewInstance, GetInstance, GetInstances}
import play.api.Logger

import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

class InstanceController @Inject() (@Named("instance-actor") instanceService: ActorRef) extends Controller {

  implicit val context = play.api.libs.concurrent.Execution.Implicits.defaultContext
  implicit val timeout: Timeout = Duration.create(5, TimeUnit.SECONDS)

//  def listNomad(templateId: Option[String]) = Action.async {
//    val maybeFilteredInstances = instanceService.nomadInstances.map(
//      instances => templateId.map(
//        id => instances.filter(_.template.id == id)
//      ).getOrElse(instances)
//    )
//    maybeFilteredInstances.map(instances => Ok(Json.toJson(instances)))
//  }

  def list(maybeTemplateId: Option[String]) = Action.async {
    val eventuallyInstances = instanceService.ask(GetInstances).mapTo[Iterable[Instance]]
    val eventuallyFilteredInstances = maybeTemplateId.map(
      id => eventuallyInstances.map(_.filter(_.template.id == id))
    ).getOrElse(eventuallyInstances)
    eventuallyFilteredInstances.map(filteredInstances => Ok(Json.toJson(filteredInstances)))
  }

//  def showNomad(id: String) = Action.async {
//    instanceService.nomadInstance(id).map(_.map(instance => Ok(Json.toJson(instance))).getOrElse(NotFound))
//  }

  def show(id: String) = Action.async {
    val eventuallyMaybeInstance = instanceService.ask(GetInstance(id)).mapTo[Option[Instance]]
    eventuallyMaybeInstance.map(_.find(_.id == id).map(instance => Ok(Json.toJson(instance))).getOrElse(NotFound))
  }

  // TODO don't send ID in parameters but generate one instead
  def create = Action.async { request =>
    // TODO check if validate fails
    val maybeValidatedInstanceCreation = request.body.asJson.map(_.validate[InstanceCreation])
    maybeValidatedInstanceCreation.map { validatedInstanceCreation =>
      validatedInstanceCreation.map { instanceCreation =>
        val eventuallyNewInstance = instanceService.ask(NewInstance(instanceCreation)).mapTo[Try[Instance]]
        eventuallyNewInstance.map { newInstance =>
          newInstance.map { instance =>
            Status(201)(Json.toJson(instance)).withHeaders(
              LOCATION -> s"/instances/${instance.id}" // TODO String constant
            )
          }.recover {
            case error => Status(400)(error.getMessage)
          }.get
        }
      }.recoverTotal { case error =>
        Future(Status(400)("Invalid JSON format: " + error.toString))
      }
    }.getOrElse(Future(Status(400)("Expected JSON data")))
  }

  def update(id: String) = Action.async { request =>
    // TODO check if validate fails
    val maybeValidatedExpectedInstanceStatus = request.body.asJson.map(_.validate[InstanceStatus])
    maybeValidatedExpectedInstanceStatus.map { validatedExpectedInstanceStatus =>
      validatedExpectedInstanceStatus.map { status =>
        if (status == InstanceStatus.Starting || status == InstanceStatus.Stopping) {
          val eventuallyChangedInstance = instanceService.ask(SetStatus(id, status)).mapTo[Option[Instance]]
          eventuallyChangedInstance.map { changedInstance =>
            changedInstance.map(i => Ok(Json.toJson(i))).getOrElse(NotFound)
          }
        } else {
          Future(Status(400)(s"Status can only be set to ${InstanceStatus.Starting} or ${InstanceStatus.Stopping}"))
        }
      }.recoverTotal { error =>
        Future(Status(400)("Invalid JSON format: " + error.toString))
      }
    }.getOrElse(Future(Status(400)("Expected JSON data")))
  }

}
