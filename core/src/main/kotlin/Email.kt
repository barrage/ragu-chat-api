package net.barrage.llmao.core

import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.MultiPartEmail
import org.apache.commons.mail.SimpleEmail
import javax.activation.DataSource
import javax.mail.internet.InternetAddress

class Email(
    private val host: String,
    private val port: Int,
    private val auth: EmailAuthentication? = null,
) {
    fun sendEmailWithAttachment(
        /** The sender email. */
        sender: String,

        /** The email recipient. */
        recipient: String,

        /** The email subject. */
        subject: String?,

        /** The email body. */
        message: String,
        attachment: EmailAttachment,

        /** Email CC. */
        cc: List<String>? = null,

        /** Email BCC. */
        bcc: List<String>? = null,
    ) {
        val email = MultiPartEmail()
        //
        with(email) {
            attach(attachment.bytes(), attachment.name(), attachment.description())
            setHostName(host)
            setSmtpPort(port)
            auth?.let { auth ->
                setAuthenticator(
                    DefaultAuthenticator(
                        auth.username,
                        auth.password
                    )
                )
            }
            isSSLOnConnect = true
            setFrom(sender)
            setSubject(subject)
            setMsg(message)
            addTo(recipient)
            cc?.let { cc -> setCc(cc.map { InternetAddress(it) }) }
            bcc?.let { bcc -> setBcc(bcc.map { InternetAddress(it) }) }
        }
        email.send()
    }

    fun sendEmail(
        /** The sender email. */
        sender: String,

        /** The email recipient. */
        recipient: String,

        /** The email subject. */
        subject: String?,

        /** The email body. */
        message: String,

        /** Email CC. */
        cc: List<String>? = null,

        /** Email BCC. */
        bcc: List<String>? = null,
    ) {
        val email = SimpleEmail()
        with(email) {
            setHostName(host)
            setSmtpPort(port)
            auth?.let { auth ->
                setAuthenticator(
                    DefaultAuthenticator(
                        auth.username,
                        auth.password
                    )
                )
            }
            isSSLOnConnect = true
            setFrom(sender)
            setSubject(subject)
            setMsg(message)
            addTo(recipient)
            cc?.let { cc -> setCc(cc.map { InternetAddress(it) }) }
            bcc?.let { bcc -> setBcc(bcc.map { InternetAddress(it) }) }
        }
        email.send()
    }
}

data class EmailAuthentication(val username: String, val password: String)

interface EmailAttachment {
    fun bytes(): DataSource

    fun name(): String

    fun description(): String
}
