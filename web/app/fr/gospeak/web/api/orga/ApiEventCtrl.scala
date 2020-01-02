package fr.gospeak.web.api.orga

import cats.data.OptionT
import com.mohiva.play.silhouette.api.Silhouette
import fr.gospeak.core.domain.{Event, Group}
import fr.gospeak.core.services.storage.{OrgaEventRepo, OrgaGroupRepo, OrgaProposalRepo, OrgaUserRepo}
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.AppConf
import fr.gospeak.web.api.domain.ApiEvent
import fr.gospeak.web.api.domain.utils.ApiResponse
import fr.gospeak.web.auth.domain.CookieEnv
import fr.gospeak.web.utils.ApiCtrl
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class ApiEventCtrl(cc: ControllerComponents,
                   silhouette: Silhouette[CookieEnv],
                   conf: AppConf,
                   val groupRepo: OrgaGroupRepo,
                   eventRepo: OrgaEventRepo,
                   proposalRepo: OrgaProposalRepo,
                   userRepo: OrgaUserRepo) extends ApiCtrl(cc, silhouette, conf) with ApiCtrl.OrgaAction {
  def list(group: Group.Slug, params: Page.Params): Action[AnyContent] = OrgaAction(group) { implicit req =>
    for {
      events <- eventRepo.listFull(params)
      proposals <- proposalRepo.list(events.items.flatMap(_.talks))
      users <- userRepo.list(events.items.flatMap(_.users) ++ proposals.flatMap(_.users))
    } yield ApiResponse.from(events, ApiEvent.orga(_, proposals, users))
  }

  def detail(group: Group.Slug, event: Event.Slug): Action[AnyContent] = OrgaAction(group) { implicit req =>
    (for {
      eventElt <- OptionT(eventRepo.findFull(event))
      proposals <- OptionT.liftF(proposalRepo.list(eventElt.talks))
      users <- OptionT.liftF(userRepo.list(eventElt.users ++ proposals.flatMap(_.users)))
      res = ApiResponse.from(ApiEvent.orga(eventElt, proposals, users))
    } yield res).value.map(_.getOrElse(ApiResponse.notFound(s"No event '${event.value}' in group '${group.value}'")))
  }
}