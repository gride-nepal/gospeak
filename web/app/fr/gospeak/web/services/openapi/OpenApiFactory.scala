package fr.gospeak.web.services.openapi

import fr.gospeak.web.services.openapi.error.OpenApiError.{ErrorMessage, ValidationError}
import fr.gospeak.web.services.openapi.error.OpenApiErrors
import fr.gospeak.web.services.openapi.models.utils.{Email, Markdown, TODO, Url, Version}
import fr.gospeak.web.services.openapi.models.{Info, OpenApi, Server}
import fr.gospeak.web.utils.Extensions._
import play.api.libs.json._

object OpenApiFactory {
  def parseJson(json: JsValue): Either[OpenApiErrors, OpenApi] = {
    import Formats._
    json.validate[OpenApi].fold(
      errors => Left(OpenApiErrors(errors.map(formatError))),
      openApi => Right(openApi)
    )
  }

  def toJson(openApi: OpenApi): JsValue = {
    import Formats._
    Json.toJson(openApi)
  }

  object Formats {
    private val formatString: Format[String] = Format((json: JsValue) => json.validate[String], (o: String) => JsString(o))
    implicit val formatTODO: Format[TODO] = Format((_: JsValue) => JsSuccess(TODO()), (_: TODO) => JsNull)
    implicit val formatMarkdown: Format[Markdown] = formatString.imap(Markdown)(_.value)
    implicit val formatUrl: Format[Url] = formatString.validate(Url.from)(_.value)
    implicit val formatEmail: Format[Email] = formatString.validate(Email.from)(_.value)
    implicit val formatVersion: Format[Version] = formatString.validate(Version.from)(_.format)

    implicit val formatInfoContact: Format[Info.Contact] = Json.format[Info.Contact]
    implicit val formatInfoLicense: Format[Info.License] = Json.format[Info.License]
    implicit val formatInfo: Format[Info] = Json.format[Info]
    implicit val formatServerVariable: Format[Server.Variable] = Json.format[Server.Variable]
    implicit val formatServer: Format[Server] = Json.format[Server].check(_.validate)
    implicit val formatOpenApi: Format[OpenApi] = Json.format[OpenApi]
  }

  private def formatError(errs: (JsPath, Seq[JsonValidationError])): ValidationError =
    ValidationError(
      path = errs._1.path.map(_.toJsonString),
      errors = errs._2.map(err => ErrorMessage(err.message, err.args.toList)).toList)

}
