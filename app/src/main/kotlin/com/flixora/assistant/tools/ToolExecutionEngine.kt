package com.flixora.assistant.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONObject
import java.net.URLEncoder

class ToolExecutionEngine(private val context: Context) {

    fun execute(name: String, args: JSONObject): JSONObject {
        return when (name) {
            "openApp" -> openApp(args.optString("packageName"))
            "searchAndCallContact" -> searchAndCallContact(args.optString("contactName"))
            "sendWhatsAppMessage" -> sendWhatsAppMessage(args.optString("contactName"), args.optString("message"))
            "sendGmail" -> sendGmail(args.optString("recipientEmail"), args.optString("subject"), args.optString("body"))
            else -> JSONObject().put("status", "error").put("message", "Tool not found")
        }
    }

    private fun openApp(packageName: String): JSONObject {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                JSONObject().put("status", "success").put("message", "Opened $packageName")
            } else {
                // Try searching by name if package not found
                JSONObject().put("status", "error").put("message", "App not found or not installed")
            }
        } catch (e: Exception) {
            JSONObject().put("status", "error").put("message", e.message)
        }
    }

    private fun searchAndCallContact(contactName: String): JSONObject {
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"),
                null
            )
            
            var number: String? = null
            if (cursor != null && cursor.moveToFirst()) {
                number = cursor.getString(0)
                cursor.close()
            }

            if (number != null) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                JSONObject().put("status", "success").put("message", "Calling $contactName")
            } else {
                JSONObject().put("status", "error").put("message", "Contact not found")
            }
        } catch (e: Exception) {
            JSONObject().put("status", "error").put("message", e.message)
        }
    }

    private fun sendWhatsAppMessage(contactName: String, message: String): JSONObject {
        return try {
            // This is a deep link. For a real app, you might want to find the contact first.
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://api.whatsapp.com/send?text=$encodedMessage")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            JSONObject().put("status", "success").put("message", "Opening WhatsApp for $contactName")
        } catch (e: Exception) {
            JSONObject().put("status", "error").put("message", e.message)
        }
    }

    private fun sendGmail(recipient: String, subject: String, body: String): JSONObject {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            JSONObject().put("status", "success").put("message", "Gmail composed for $recipient")
        } catch (e: Exception) {
            JSONObject().put("status", "error").put("message", e.message)
        }
    }
}
