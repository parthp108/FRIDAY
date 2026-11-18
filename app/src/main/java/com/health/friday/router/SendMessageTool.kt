package com.health.friday.router

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter

class SendMessageTool(
    private val router: CrossAppIntentRouter
) : AiTool {

    override val name = "send_message"

    override val description =
        "Sends a text message to a person via WhatsApp (default), Telegram, Instagram or SMS. " +
                "TWO STEPS: the first call returns a preview and sends nothing. Read the recipient " +
                "and text back to the user, wait for their yes, then call again with the identical " +
                "recipient and message and confirmed=true. Never preview and confirm in the same turn."

    override val parameters = listOf(
        AiToolParameter("recipient", "Contact name exactly as the user said it.", required = true),
        AiToolParameter("message", "The exact message text to send.", required = true),
        AiToolParameter("app", "whatsapp (default), telegram, instagram, or sms.", required = false),
        AiToolParameter("confirmed", "'true' only after the user approved the preview.", required = false)
    )

    private data class Pending(
        val recipient: String,
        val message: String,
        val app: String,
        val createdAtMs: Long
    )

    @Volatile
    private var pending: Pending? = null

    override suspend fun execute(arguments: Map<String, String>): String {
        val recipient = arguments["recipient"]?.trim().orEmpty()
        val message = arguments["message"]?.trim().orEmpty()
        val app = arguments["app"]?.trim()?.takeIf { it.isNotBlank() } ?: "whatsapp"
        val confirmed = arguments["confirmed"]?.trim().equals("true", ignoreCase = true)

        if (recipient.isBlank() || message.isBlank()) {
            return "Nothing sent. I need both a recipient and the message text."
        }

        val p = pending
        val matchesPreview = p != null &&
                p.recipient.equals(recipient, ignoreCase = true) &&
                p.message == message &&
                p.app.equals(app, ignoreCase = true) &&
                System.currentTimeMillis() - p.createdAtMs <= CONFIRM_WINDOW_MS

        if (!(confirmed && matchesPreview)) {
            pending = Pending(recipient, message, app, System.currentTimeMillis())
            return "PREVIEW ONLY - nothing has been sent. To: $recipient via $app. " +
                    "Message: \"$message\". Read this back to the user and ask them to confirm. " +
                    "After they say yes, call send_message again with the same recipient, " +
                    "message and confirmed=true."
        }

        pending = null
        val result = router.routeSendMessage(recipient, message, app)
        return when (result.status) {
            RouteStatus.COMPLETED_HEADLESS,
            RouteStatus.REQUIRES_GUI_COMMIT -> result.summaryMessage
            RouteStatus.FAILED -> "Nothing sent. ${result.summaryMessage}"
        }
    }

    private companion object {
        const val CONFIRM_WINDOW_MS = 120_000L
    }
}
