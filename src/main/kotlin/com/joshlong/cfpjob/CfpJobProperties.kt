package com.joshlong.cfpjob

import com.sendgrid.helpers.mail.objects.Email
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "cfp.notifications")
class CfpJobProperties(var subject: String?? = null,
                       var source: Email? = null,
                       var destination: Email? = null,
                       var functionName: String? = null)