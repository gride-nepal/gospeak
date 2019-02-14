package fr.gospeak.web.user.talks.cfps

import fr.gospeak.core.domain.Proposal
import fr.gospeak.web.utils.Mappings._
import play.api.data.Form
import play.api.data.Forms._

object CfpForms {
  val create: Form[Proposal.Data] = Form(mapping(
    "title" -> talkTitle,
    "duration" -> duration,
    "description" -> markdown
  )(Proposal.Data.apply)(Proposal.Data.unapply))
}