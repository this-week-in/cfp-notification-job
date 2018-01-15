package com.example.cfpjob

import com.sendgrid.*
import freemarker.template.Configuration
import freemarker.template.Template
import org.apache.commons.logging.LogFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import pinboard.Bookmark
import pinboard.PinboardClient
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.util.*

fun main(args: Array<String>) {
	SpringApplicationBuilder()
			.web(WebApplicationType.NONE)
			.sources(CfpJobApplication::class.java)
			.run(*args)
}

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(CfpJobProperties::class)
class CfpJobApplication

@Component
class CfpJobRunner(val job: CfpNotificationJob,
                   val configuration: Configuration,
                   val properties: CfpJobProperties,
                   val client: PinboardClient,
                   val lambdaDiscoveryClient: DiscoveryClient) : ApplicationRunner {

	private val log = LogFactory.getLog(javaClass)

	private val cfpStatusFunctionName = "cfp-status-function"

	override fun run(args: ApplicationArguments) {

		val template = configuration.getTemplate("/notifications.ftl")
		log.info("temmplate is not null? ${template != null}")
		Assert.notNull(template, "the template must not be null")
		val year = Instant.now().atZone(ZoneId.systemDefault()).year
		val currentYearTag = Integer.toString(year)
		log.info("the year is $currentYearTag")
		val bookmarks = client.getAllPosts(tag = arrayOf("cfp")).filter { !it.tags.contains(currentYearTag) }
		log.info("client is not null? ${client == null}")
		log.info("found ${bookmarks.size} bookmarks")
		val email = properties.destination!!
		log.info("the destination email is ${email}")
		val url = this.lambdaDiscoveryClient.getInstances(cfpStatusFunctionName).first().uri.toString()
		log.info("the $cfpStatusFunctionName URL is $url")
		val html = job.generateNotificationHtml(template, email.name ?: email.email, year, bookmarks, url)
		log.info("generated HTML: ${html}")
		val subject = String.format(properties.subject!!, bookmarks.size, year)
		log.info("the subject is $subject")
		val response = job.notify(properties.source!!, email, subject, html)
		log.info("response status: ${response.statusCode} ")
		log.info("response body: ${response.body} ")
		log.info("response headers: ${response.headers} ")
		log.info(
				"""
					|Running CFP notification job.
					|Properties (subject): ${properties.subject}.
					|Properties (destination): ${properties.destination}.
					|Properties (source): ${properties.source}.
					|Sending email to  ${email.name} (${email.email}).
					|Going to send the following HTML: $html.
					|Response: $response.
					|Sent the notification @ ${Instant.now()}.
				"""
						.trimMargin("|")
						.trim()
						.trimIndent()
		)
	}
}

@ConfigurationProperties("cfp.notifications")
open class CfpJobProperties(var subject: String? = null,
                            var source: Email? = null,
                            var destination: Email? = null)

@Component
class CfpNotificationJob(val sendGrid: SendGrid) {

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
		log.info("about to notify..")
		val content = Content("text/html", html)
		val mail = Mail(from, subject, to, content)
		val request = Request().apply {
			method = Method.POST
			endpoint = "mail/send"
			body = mail.build()
		}
		return sendGrid.api(request)
				.let {
					val headers = it
							.headers
							.entries
							.map({ "${it.key}=${it.value}" })
							.joinToString(separator = ",")
					val statement = listOf("response status code: ${it.statusCode}", "body: ${it.body}", "headers: ${headers}")
							.joinToString(System.lineSeparator())
					log.info(statement)
					it
				}


	}
}