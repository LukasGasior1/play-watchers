package controllers

import javax.inject._
import models.{WatcherStatus, Watcher}
import play.api._
import play.api.i18n.{MessagesApi, I18nSupport}
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class WatcherFormData(url: String, expectedTitle: String)

@Singleton
class HomeController @Inject()(val messagesApi: MessagesApi, ws: WSClient) extends Controller with I18nSupport {

  val watcherForm = Form(
    mapping(
      "url" -> nonEmptyText.verifying("Url must start with http://", _.startsWith("http://")),
      "expectedTitle" -> nonEmptyText
    )(WatcherFormData.apply)(WatcherFormData.unapply)
  )

  var watchers = Seq(
    Watcher("http://wp.pl", "Wp"),
    Watcher("http://onet.pl", "Onet.pl"))

  def index = Action.async {
    val result = watchers.map { watcher =>
      checkStatus(watcher).map { status =>
        (watcher, status)
      }
    }

    Future.sequence(result).map { res =>
      Ok(views.html.index(res))
    }
  }

  def showForm = Action {
    Ok(views.html.form(watcherForm))
  }

  def submitForm = Action { implicit request =>
    watcherForm.bindFromRequest.fold(
      formWithErrors => {
        Ok(views.html.form(formWithErrors))
      },
      formData => {
        watchers :+= Watcher(formData.url, formData.expectedTitle)
        Redirect(routes.HomeController.index())
      }
    )
  }

  def checkStatus(watcher: Watcher): Future[WatcherStatus] = {

    val titleRe = "<title>(.*)</title>".r

    ws.url(watcher.url)
      .withRequestTimeout(3.seconds)
      .get()
      .map {
        case response if response.status == 200 =>
          val title = titleRe.findAllIn(response.body)
            .matchData
            .map(_.group(1))
            .toSeq
            .headOption

          if (title.contains(watcher.expectedTitle))
            WatcherStatus.Success
          else
            WatcherStatus.Failure("Title does not match")

        case response =>
          WatcherStatus.Failure(s"Status code is ${response.status}, expected 200")
      } recover { case error =>
        WatcherStatus.Failure("Unable to connect")
      }
  }

}
