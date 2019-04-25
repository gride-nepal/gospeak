package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils._
import fr.gospeak.libs.scalautils.domain._

import scala.concurrent.duration.FiniteDuration

final case class Talk(id: Talk.Id,
                      slug: Talk.Slug,
                      status: Talk.Status,
                      title: Talk.Title,
                      duration: FiniteDuration,
                      description: Markdown,
                      speakers: NonEmptyList[User.Id],
                      slides: Option[Slides],
                      video: Option[Video],
                      info: Info) {
  def data: Talk.Data = Talk.Data(this)

  def users: Seq[User.Id] = (info.createdBy :: info.updatedBy :: speakers.toList).distinct
}

object Talk {
  def apply(data: Data,
            status: Status,
            speakers: NonEmptyList[User.Id],
            info: Info): Talk =
    new Talk(Id.generate(), data.slug, status, data.title, data.duration, data.description, speakers, data.slides, data.video, info)

  final class Id private(value: String) extends DataClass(value) with IId

  object Id extends UuidIdBuilder[Id]("Talk.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value) with ISlug

  object Slug extends SlugBuilder[Slug]("Talk.Slug", new Slug(_))

  final case class Title(value: String) extends AnyVal

  sealed trait Status extends Product with Serializable

  object Status extends EnumBuilder[Status]("Talk.Status") {

    case object Draft extends Status

    case object Private extends Status {
      def description = "Only you can see it, you can propose it to groups but organizers will not see it"
    }

    case object Public extends Status {
      def description = "Group organizers will be able to search for it and send you speaking proposals"
    }

    case object Archived extends Status {
      def description = "When your talk is not actual anymore. It will be hided by default everywhere"
    }

    val all: Seq[Status] = Seq(Draft, Private, Public, Archived)
    val active: NonEmptyList[Status] = NonEmptyList.of(Draft, Private, Public)
  }

  final case class Data(slug: Talk.Slug,
                        title: Talk.Title,
                        duration: FiniteDuration,
                        description: Markdown,
                        slides: Option[Slides],
                        video: Option[Video])

  object Data {
    def apply(talk: Talk): Data = Data(talk.slug, talk.title, talk.duration, talk.description, talk.slides, talk.video)
  }

}
