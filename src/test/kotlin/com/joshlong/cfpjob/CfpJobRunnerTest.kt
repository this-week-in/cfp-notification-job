package com.joshlong.cfpjob

import com.sendgrid.Response
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.objects.Email
import freemarker.template.Configuration
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.function.discovery.aws.LambdaDiscoveryClient
import org.springframework.test.context.junit4.SpringRunner
import pinboard.Bookmark
import pinboard.PinboardClient
import pinboard.PinboardClientAutoConfiguration
import java.net.URI
import java.util.*

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [PinboardClientAutoConfiguration::class, CfpJobApplication::class])
class CfpJobRunnerTest {

	val fnName = "cfp-status-function"

	val props = CfpJobProperties(
			subject = "subject",
			functionName = fnName,
			source = Email("source@email.com", "From"),
			destination = Email("to@email.com", "To"))

	@MockBean
	val runner: CfpJobRunner? = null

	@Autowired
	val config: Configuration? = null

	@Autowired
	val job: CfpNotificationJob? = null

	@Autowired
	val cfpJobProperties : CfpJobProperties ?  = null

	@MockBean
	val client: PinboardClient? = null

	@MockBean
	val sg: SendGrid? = null

	@MockBean
	val ldc: LambdaDiscoveryClient? = null

	@Test
	fun run() {

		val si = Mockito.mock(ServiceInstance::class.java)
		Mockito.`when`(si.uri).thenReturn(URI.create("http://a-uri.com/a/b/c"))
		Mockito.`when`(this.ldc!!.getInstances(fnName)).thenReturn(arrayListOf(si))
		Mockito.`when`(this.sg!!.api(any())).thenReturn(Mockito.mock(Response::class.java))
		val runner = CfpJobRunner(job!!, config!!, props, client!!, this.ldc!!)
		val bookmarks: Array<Bookmark> = 0.until(10)
				.map { i ->
					Bookmark("href$i", "description$i", "extended$i", "hash$i",
							"meta$i", Date(), false, false, arrayOf("cfp"))
				}
				.toTypedArray()
		Mockito.`when`(client!!.getAllPosts(tag = arrayOf("cfp"))).thenReturn(bookmarks)
		runner.run(DefaultApplicationArguments())
		Mockito.verify(this.client)!!.getAllPosts(tag = arrayOf("cfp"))
		Mockito.verify(this.sg)!!.api(any(com.sendgrid.Request::class.java))
	}
}