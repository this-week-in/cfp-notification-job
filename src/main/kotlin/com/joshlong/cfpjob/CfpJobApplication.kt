package com.joshlong.cfpjob

import org.apache.commons.logging.LogFactory
import org.springframework.boot.ExitCodeEvent
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.event.ContextStoppedEvent
import org.springframework.context.event.EventListener

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(CfpJobProperties::class)
class CfpJobApplication  {

	private val log = LogFactory.getLog(javaClass)


	@EventListener(ExitCodeEvent::class)
	fun exit(exitCodeEvent: ExitCodeEvent) {
		log.debug("exit code is ${exitCodeEvent.exitCode}, timestamp is " +
				"${exitCodeEvent.timestamp} and source is ${exitCodeEvent.source} ")
	}

	@EventListener(ContextStoppedEvent::class)
	fun stopped(ace: ContextStoppedEvent) {
		log.debug(
				"""
					|Stopping:  ${ace.applicationContext.javaClass.name}.
					|Source:    ${ace.source}
					| ${ace}
					""".trimMargin("|"))
	}

}