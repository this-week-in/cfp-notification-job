package com.example.cfpjob

import com.sendgrid.Request
import com.sendgrid.Response
import com.sendgrid.SendGrid
import freemarker.template.Configuration
import org.assertj.core.api.Assertions
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.function.discovery.aws.LambdaDiscoveryClient
import org.springframework.test.context.junit4.SpringRunner
import pinboard.Bookmark
import java.net.URI
import java.util.*

@SpringBootTest(classes = [(CfpJobApplication::class)])
@RunWith(SpringRunner::class)
class CfpNotificationJobTest {

	@Autowired
	val configuration: Configuration? = null

	@MockBean
	val discoveryClient: LambdaDiscoveryClient? = null

	@MockBean
	val sendGrid: SendGrid? = null

	// this has the effect of disabling
	// the runner from the application context.
	// we don't want it to run.
	@MockBean
	val runner: CfpJobRunner? = null


	@Autowired
	val job: CfpNotificationJob? = null

	@Test
	fun generateNotificationHtml() {
		val serviceId = "cfp-status-function"
		val statusFunctionUrl = "http://${serviceId}.aws.com/a/b/c"
		val si = Mockito.mock(ServiceInstance::class.java)
		Mockito.`when`(si.host).thenReturn("host")
		Mockito.`when`(si.port).thenReturn(20182)
		Mockito.`when`(si.uri).thenReturn(URI.create(statusFunctionUrl))
		Mockito.`when`(si.isSecure).thenReturn(true)
		Mockito.`when`(si.serviceId).thenReturn(serviceId)
		Mockito.`when`(this.discoveryClient!!.getInstances(serviceId)).thenReturn(mutableListOf(si))
		Mockito.`when`(this.sendGrid!!.api(Mockito.any(Request::class.java)))
				.thenReturn(Response(200, "Sent!", mapOf("a" to "b")))
		val template = this.configuration!!.getTemplate("/notifications.ftl")
		val bookmarks = mutableListOf(Bookmark("href1", "description", "extended",
				"hash1", "meta", Date(), false, false, arrayOf("a", "tag")))
		val emailHtml = this.job!!.generateNotificationHtml(template,
				"user@email.com", 2018, bookmarks)
		Assertions.assertThat(bookmarks.size).isGreaterThan(0)
		bookmarks.forEach {
			Assertions.assertThat(emailHtml.contains("${statusFunctionUrl}?id=${it.hash}"))
		}
	}
}