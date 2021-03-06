package gospeak.infra.services.storage.sql

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import doobie.Fragments
import doobie.implicits._
import doobie.util.fragment.Fragment
import gospeak.core.domain.utils.UserCtx
import gospeak.core.domain.{Cfp, Talk, User}
import gospeak.core.services.storage.TalkRepo
import gospeak.infra.services.storage.sql.TalkRepoSql._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.Mappings._
import gospeak.infra.services.storage.sql.utils.DoobieUtils.{Field, Insert, Select, SelectPage, Update}
import gospeak.infra.services.storage.sql.utils.GenericRepo
import gospeak.libs.scala.Extensions._
import gospeak.libs.scala.domain._

class TalkRepoSql(protected[sql] val xa: doobie.Transactor[IO]) extends GenericRepo with TalkRepo {
  override def create(data: Talk.Data)(implicit ctx: UserCtx): IO[Talk] =
    find(data.slug).flatMap {
      case None => insert(Talk(data, Talk.Status.Public, NonEmptyList.one(ctx.user.id), ctx.info)).run(xa)
      case _ => IO.raiseError(CustomException(s"Talk slug '${data.slug}' is already used"))
    }

  override def edit(talk: Talk.Slug, data: Talk.Data)(implicit ctx: UserCtx): IO[Done] = {
    if (data.slug != talk) {
      find(data.slug).flatMap {
        case None => update(talk)(data, ctx.user.id, ctx.now).run(xa)
        case _ => IO.raiseError(CustomException(s"Talk slug '${data.slug}' is already used"))
      }
    } else {
      update(talk)(data, ctx.user.id, ctx.now).run(xa)
    }
  }

  override def editStatus(talk: Talk.Slug, status: Talk.Status)(implicit ctx: UserCtx): IO[Done] = updateStatus(talk)(status, ctx.user.id).run(xa)

  override def editSlides(talk: Talk.Slug, slides: Slides)(implicit ctx: UserCtx): IO[Done] = updateSlides(talk)(slides, ctx.user.id, ctx.now).run(xa)

  override def editVideo(talk: Talk.Slug, video: Video)(implicit ctx: UserCtx): IO[Done] = updateVideo(talk)(video, ctx.user.id, ctx.now).run(xa)

  override def addSpeaker(talk: Talk.Id, by: User.Id)(implicit ctx: UserCtx): IO[Done] =
    find(talk).flatMap {
      case Some(talkElt) =>
        if (talkElt.speakers.toList.contains(ctx.user.id)) {
          IO.raiseError(new IllegalArgumentException("speaker already added"))
        } else {
          updateSpeakers(talkElt.slug)(talkElt.speakers.append(ctx.user.id), by, ctx.now).run(xa)
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def removeSpeaker(talk: Talk.Slug, speaker: User.Id)(implicit ctx: UserCtx): IO[Done] =
    find(talk).flatMap {
      case Some(talkElt) =>
        if (talkElt.info.createdBy == speaker) {
          IO.raiseError(new IllegalArgumentException("talk creator can't be removed"))
        } else if (talkElt.speakers.toList.contains(speaker)) {
          talkElt.speakers.filter(_ != speaker).toNel.map { speakers =>
            updateSpeakers(talk)(speakers, ctx.user.id, ctx.now).run(xa)
          }.getOrElse {
            IO.raiseError(new IllegalArgumentException("last speaker can't be removed"))
          }
        } else {
          IO.raiseError(new IllegalArgumentException("user is not a speaker"))
        }
      case None => IO.raiseError(new IllegalArgumentException("unreachable talk"))
    }

  override def find(talk: Talk.Id): IO[Option[Talk]] = selectOne(talk).runOption(xa)

  override def list(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(params).run(xa)

  override def listAll(user: User.Id, status: Talk.Status): IO[Seq[Talk]] = selectAll(user, status).runList(xa)

  override def listCurrent(params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(Talk.Status.current, params).run(xa)

  override def listCurrent(cfp: Cfp.Id, params: Page.Params)(implicit ctx: UserCtx): IO[Page[Talk]] = selectPage(cfp, Talk.Status.current, params).run(xa)

  override def find(talk: Talk.Slug)(implicit ctx: UserCtx): IO[Option[Talk]] = selectOne(ctx.user.id, talk).runOption(xa)

  override def findPublic(talk: Talk.Slug, speaker: User.Id): IO[Option[Talk]] = selectOne(speaker, talk, Talk.Status.Public).runOption(xa)

  override def exists(talk: Talk.Slug): IO[Boolean] = selectOne(talk).runExists(xa)

  override def listTags(): IO[Seq[Tag]] = selectTags().runList(xa).map(_.flatten.distinct)
}

object TalkRepoSql {
  private val _ = talkIdMeta // for intellij not remove DoobieUtils.Mappings import
  private val table = Tables.talks

  private[sql] def insert(e: Talk): Insert[Talk] = {
    val values = fr0"${e.id}, ${e.slug}, ${e.status}, ${e.title}, ${e.duration}, ${e.description}, ${e.message}, ${e.speakers}, ${e.slides}, ${e.video}, ${e.tags}, ${e.info.createdAt}, ${e.info.createdBy}, ${e.info.updatedAt}, ${e.info.updatedBy}"
    table.insert(e, _ => values)
  }

  private[sql] def update(talk: Talk.Slug)(d: Talk.Data, by: User.Id, now: Instant): Update = {
    val fields = fr0"slug=${d.slug}, title=${d.title}, duration=${d.duration}, description=${d.description}, message=${d.message}, slides=${d.slides}, video=${d.video}, tags=${d.tags}, updated_at=$now, updated_by=$by"
    table.update(fields, where(by, talk))
  }

  private[sql] def updateStatus(talk: Talk.Slug)(status: Talk.Status, by: User.Id): Update =
    table.update(fr0"status=$status", where(by, talk))

  private[sql] def updateSlides(talk: Talk.Slug)(slides: Slides, by: User.Id, now: Instant): Update =
    table.update(fr0"slides=$slides, updated_at=$now, updated_by=$by", where(by, talk))

  private[sql] def updateVideo(talk: Talk.Slug)(video: Video, by: User.Id, now: Instant): Update =
    table.update(fr0"video=$video, updated_at=$now, updated_by=$by", where(by, talk))

  private[sql] def updateSpeakers(talk: Talk.Slug)(speakers: NonEmptyList[User.Id], by: User.Id, now: Instant): Update =
    table.update(fr0"speakers=$speakers, updated_at=$now, updated_by=$by", where(by, talk))

  private[sql] def selectOne(talk: Talk.Id): Select[Talk] =
    table.select[Talk](fr0"WHERE t.id=$talk")

  private[sql] def selectOne(talk: Talk.Slug): Select[Talk] =
    table.select[Talk](fr0"WHERE t.slug=$talk")

  private[sql] def selectOne(user: User.Id, talk: Talk.Slug): Select[Talk] =
    table.select[Talk](where(user, talk))

  private[sql] def selectOne(user: User.Id, talk: Talk.Slug, status: Talk.Status): Select[Talk] =
    table.selectOne[Talk](fr0"WHERE t.speakers LIKE ${"%" + user.value + "%"} AND t.slug=$talk AND t.status=$status")

  private[sql] def selectPage(params: Page.Params)(implicit ctx: UserCtx): SelectPage[Talk, UserCtx] =
    table.selectPage[Talk, UserCtx](params, fr0"WHERE t.speakers LIKE ${"%" + ctx.user.id.value + "%"}")

  private[sql] def selectAll(user: User.Id, status: Talk.Status): Select[Talk] =
    table.select[Talk](fr0"WHERE t.speakers LIKE ${"%" + user.value + "%"} AND t.status=$status")

  private[sql] def selectPage(status: NonEmptyList[Talk.Status], params: Page.Params)(implicit ctx: UserCtx): SelectPage[Talk, UserCtx] =
    table.selectPage[Talk, UserCtx](params, fr0"WHERE t.speakers LIKE ${"%" + ctx.user.id.value + "%"} AND " ++ Fragments.in(fr"t.status", status))

  private[sql] def selectPage(cfp: Cfp.Id, status: NonEmptyList[Talk.Status], params: Page.Params)(implicit ctx: UserCtx): SelectPage[Talk, UserCtx] = {
    val cfpTalks = Tables.proposals.select[Talk.Id](Seq(Field("talk_id", "p")), fr0"WHERE p.cfp_id=$cfp").fr
    table.selectPage[Talk, UserCtx](params, fr0"WHERE t.speakers LIKE ${"%" + ctx.user.id.value + "%"} AND t.id NOT IN (" ++ cfpTalks ++ fr0") AND " ++ Fragments.in(fr"t.status", status))
  }

  private[sql] def selectTags(): Select[Seq[Tag]] =
    table.select[Seq[Tag]](Seq(Field("tags", "t")))

  private def where(user: User.Id, talk: Talk.Slug): Fragment =
    fr0"WHERE t.speakers LIKE ${"%" + user.value + "%"} AND t.slug=$talk"
}
