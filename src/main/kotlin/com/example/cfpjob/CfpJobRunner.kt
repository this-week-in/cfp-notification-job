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
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.function.discovery.aws.LambdaDiscoveryClient
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
                   val client: PinboardClient) : ApplicationRunner {

	private val log = LogFactory.getLog(javaClass)

	override fun run(args: ApplicationArguments) {

		val year = Instant.now().atZone(ZoneId.systemDefault()).year
		val currentYearTag = Integer.toString(year)
		val posts = client.getAllPosts(tag = arrayOf("cfp"))
		val template = configuration.getTemplate("/notifications.ftl")
		val bookmarks = posts.filter { !it.tags.contains(currentYearTag) }
		val email = properties.destination!!
		val html = job.generateNotificationHtml(template, email.name, year, bookmarks)
		val subject = String.format(properties.subject!!, bookmarks.size, year)
		val response = job.notify(properties.source!!, email, subject, html)
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
class CfpNotificationJob(val sendGrid: SendGrid,
                         val lambdaDiscoveryClient: LambdaDiscoveryClient) {

	private val log = LogFactory.getLog(javaClass)

	fun generateNotificationHtml(
			template: Template,
			destinationName: String,
			year: Int,
			bookmarks: Collection<Bookmark>): String {

		Assert.notNull(bookmarks, "the bookmarks collection should not be empty or null.")

		val url = this.lambdaDiscoveryClient.getInstances("cfp-status-function")
				.first()
				.uri
				.toString()

		val dataModel = mapOf(
				"cfpStatusFunctionUrl" to url,
				"bookmarkCount" to bookmarks.size,
				"destinationName" to destinationName,
				"time" to Date(),
				"bookmarks" to bookmarks,
				"year" to year.toString()
		)

		return StringWriter().use {
			template.process(dataModel, it)
			return it.toString()
		}
	}

	fun notify(from: Email, to: Email, subject: String, html: String): Response {
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