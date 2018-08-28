package com.joshlong.cfpjob

import com.sendgrid.*
import freemarker.template.Configuration
import freemarker.template.Template
import org.apache.commons.logging.LogFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeEvent
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient
import org.springframework.context.event.ApplicationContextEvent
import org.springframework.context.event.ContextStoppedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import pinboard.Bookmark
import pinboard.PinboardClient
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.util.*

fun main(args: Array<String>) {
	val log = LogFactory.getLog(CfpJobRunner::class.java)
	try {
		SpringApplicationBuilder()
				.web(WebApplicationType.NONE)
				.sources(CfpJobApplication::class.java)
				.run(*args)
	} catch (ex: Throwable) {
		log.error("Error ${Instant.now().atZone(ZoneId.systemDefault())} when running ${CfpJobApplication::class.java.name}.", ex)
	}
}

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(CfpJobProperties::class)
class CfpJobApplication {

	private val log = LogFactory.getLog(javaClass)

	@EventListener(ExitCodeEvent::class)
	fun exit(exitCodeEvent: ExitCodeEvent) {
		log.debug("exit code is ${exitCodeEvent.exitCode}, timestamp is " +
				"${exitCodeEvent.timestamp} and source is ${exitCodeEvent.source} ")
	}

	@EventListener(ApplicationContextEvent::class)
	fun error(ace: ApplicationContextEvent) {
		if (ace is ContextStoppedEvent) {
			log.debug(
					"""
					|Stopping:  ${ace.applicationContext.javaClass.name}.
					|Source:    ${ace.source}
					| ${ace}
					""".trimMargin("|"))

		}
	}
}

@Component
class CfpJobRunner(val job: CfpNotificationJob,
                   val configuration: Configuration,
                   val properties: CfpJobProperties,
                   val client: PinboardClient,
                   val lambdaDiscoveryClient: DiscoveryClient) : ApplicationRunner {

	private val log = LogFactory.getLog(javaClass)

	private fun cfpStatusFunctionUrl(cfpStatusFunctionName: String): String {
		log.info("looking for function named ${cfpStatusFunctionName}.")
		log.debug("what sort of ${DiscoveryClient::class} do we have? ${this.lambdaDiscoveryClient.javaClass}")
		if (this.lambdaDiscoveryClient is CompositeDiscoveryClient) {
			(this.lambdaDiscoveryClient as CompositeDiscoveryClient).discoveryClients.forEach {
				log.debug("\tfound ${it.description()}.")
			}
		}
		val instances = this.lambdaDiscoveryClient.getInstances(cfpStatusFunctionName)
		log.debug("we found ${instances.size} instances of the $cfpStatusFunctionName service.")
		Assert.isTrue(instances.size > 0, "there should be at least one instance of the service.")
		return instances.first().uri.toString()
	}

	override fun run(args: ApplicationArguments) {
		try {
			val template = configuration.getTemplate("/notifications.ftl")
			Assert.notNull(template, "the template must not be null")
			val year = Instant.now().atZone(ZoneId.systemDefault()).year
			val currentYearTag = Integer.toString(year)
			val bookmarks = client.getAllPosts(tag = arrayOf("cfp")).filter { !it.tags.contains(currentYearTag) }
			val email = properties.destination!!
			val cfpStatusFunctionUrl = this.cfpStatusFunctionUrl(properties.functionName!!)
			val html = job.generateNotificationHtml(template, email.name
					?: email.email, year, bookmarks, cfpStatusFunctionUrl)
			val subject = String.format(properties.subject!!, bookmarks.size, year)
			val response = job.notify(properties.source!!, email, subject, html)

			log.debug("""
				generated HTML:   ${html}
				response status:  ${response.statusCode}
				response body:    ${response.body}
				response headers:
			""".trimMargin())
			(response.headers ?: mapOf()).forEach {
				log.debug("response header: ${it.key} = ${it.value}")
			}
		} catch (e: Throwable) {
			log.error("ERROR!", e)
		}
	}
}

@ConfigurationProperties(prefix = "cfp.notifications")
open class CfpJobProperties(var subject: String? = null,
                            var source: Email? = null,
                            var destination: Email? = null,
                            var functionName: String? = null)

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