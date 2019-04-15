package fr.gospeak.web.pages.speaker.talks.proposals

import fr.gospeak.core.domain.{Cfp, Proposal, Talk}
import fr.gospeak.core.testingutils.Generators._
import fr.gospeak.libs.scalautils.domain.Page
import fr.gospeak.web.testingutils.TwirlSpec

class ListSpec extends TwirlSpec {
  private val talk = random[Talk]
  private val proposals = random[(Cfp, Proposal)](10)

  describe("list.scala.html") {
    it("should display a jumbotron on empty page") {
      html.list(talk, Page.empty[(Cfp, Proposal)], Seq())(b).toString should include("""<div class="jumbotron">""")
    }
    it("should display a list when non empty page") {
      val res = html.list(talk, Page.from(proposals), Seq())(b).toString
      res should not include """<div class="jumbotron">"""
      res should include("""<div class="list-group mt-3 mb-3">""")
      res should include(proposals.head._2.title.value)
    }
  }
}