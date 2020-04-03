package com.joshlong.cfpjob

import com.sendgrid.Method
import com.sendgrid.Request
import com.sendgrid.Response
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.Content
import com.sendgrid.helpers.mail.objects.Email
import freemarker.template.Template
import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import pinboard.Bookmark
import java.io.StringWriter
import java.util.*

@Component
class CfpNotificationJob(private val sendGrid: SendGrid) {

	private val log = LogFactory.getLog(javaClass)

	fun generateNotificationHtml(
			template: Template,
			destinationName: String,
			year: Int,
			bookmarks: Collection<Bookmark>,
			cfpStatusFunctionUrl: String): String {

		Assert.notNull(bookmarks, "the bookmarks collection should not be empty or null.")

		val dataModel = mapOf(
				"cfpStatusFunctionUrl" to cfpStatusFunctionUrl,
				"bookmarkCount" to bookmarks.size,
				"destinationName" to destinationName,
				"time" to Date(),
				"bookmarks" to bookmarks,
				"year" to year.toString()
		)
		StringWriter().use {
			template.process(dataModel, it)
			return it.toString()
		}
	}

	fun notify(from: Email, to: Email, subject: String, html: String): Response {
		log.debug("about to notify..")
		val content = Content("text/html", html)
		val mail = Mail(from, subject, to, content)
		val request = Request().apply {
			method = Method.POST
			endpoint = "mail/send"
			body = mail.build()
		}
		return sendGrid.api(request)
	}
}