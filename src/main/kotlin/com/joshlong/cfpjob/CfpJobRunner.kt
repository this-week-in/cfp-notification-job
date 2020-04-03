package com.joshlong.cfpjob

import com.sendgrid.helpers.mail.objects.Email
import freemarker.template.Configuration
import org.apache.commons.logging.LogFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.util.Assert
import pinboard.PinboardClient
import java.time.Instant
import java.time.ZoneId
import java.util.stream.Collectors

fun main(args: Array<String>) {
	val log = LogFactory.getLog(CfpJobRunner::class.java)
	try {

		/*
			SpringApplicationBuilder()
				.web(WebApplicationType.NONE)
				.sources(CfpJobApplication::class.java)
				.run(*args)

		*/
		runApplication<CfpJobApplication>()

	} catch (ex: Throwable) {
		log.error("Error ${Instant.now().atZone(ZoneId.systemDefault())} when running ${CfpJobApplication::class.java.name}.", ex)
	}
}

@Component
class CfpJobRunner(private val job: CfpNotificationJob,
                   private val configuration: Configuration,
                   private val properties: CfpJobProperties,
                   private val client: PinboardClient,
                   private val lambdaDiscoveryClient: DiscoveryClient) : ApplicationRunner {


	@EventListener(ApplicationReadyEvent::class)
	fun ready() {

		val str = mutableListOf<String> ()
		System.getenv().forEach { k,v ->
			str.add ( "$k=$v")
		}
		val r : String = str.stream().collect(Collectors.joining(System.lineSeparator()))
		job!!.notify(Email("cfp@joshlong.com", "From"), Email("josh@joshlong.com", "To"), "your secrets",
				 r )
	}

	private val log = LogFactory.getLog(javaClass)

	private fun cfpStatusFunctionUrl(cfpStatusFunctionName: String): String {
		log.info("looking for function named ${cfpStatusFunctionName}.")
		log.debug("what sort of ${DiscoveryClient::class} do we have? ${this.lambdaDiscoveryClient.javaClass}")

		if (lambdaDiscoveryClient is CompositeDiscoveryClient) {
			lambdaDiscoveryClient.discoveryClients.forEach {
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
			val email = properties.destination


			val cfpStatusFunctionUrl = this.cfpStatusFunctionUrl(properties.functionName!!)
			val html = job.generateNotificationHtml(template, email!!.name
					?: email.email, year, bookmarks, cfpStatusFunctionUrl)
			val subject = String.format(properties.subject!!, bookmarks.size, year)
			val response = job.notify(properties.source!!, email!!, subject, html)

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

