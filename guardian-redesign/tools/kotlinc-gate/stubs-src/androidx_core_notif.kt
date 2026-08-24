// GATE STUB — NotificationCompat.
package androidx.core.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap

object NotificationCompat {
    const val PRIORITY_MIN: Int = -2
    const val PRIORITY_LOW: Int = -1
    const val PRIORITY_DEFAULT: Int = 0
    const val PRIORITY_HIGH: Int = 1
    const val PRIORITY_MAX: Int = 2
    const val CATEGORY_SERVICE: String = "service"
    const val CATEGORY_ALARM: String = "alarm"
    const val CATEGORY_STATUS: String = "status"

    open class Builder(protected val context: Context, protected val channelId: String) {
        constructor(context: Context) : this(context, "")

        open fun setSmallIcon(icon: Int): Builder = this
        open fun setContentTitle(title: CharSequence?): Builder = this
        open fun setContentText(text: CharSequence?): Builder = this
        open fun setContentIntent(intent: PendingIntent?): Builder = this
        open fun setOngoing(ongoing: Boolean): Builder = this
        open fun setAutoCancel(autoCancel: Boolean): Builder = this
        open fun setPriority(priority: Int): Builder = this
        open fun setCategory(category: String?): Builder = this
        open fun setLargeIcon(bitmap: Bitmap?): Builder = this
        open fun setStyle(style: Style?): Builder = this
        open fun addAction(action: Action?): Builder = this
        open fun addAction(icon: Int, title: CharSequence?, intent: PendingIntent?): Builder = this
        open fun setOnlyAlertOnce(onlyAlertOnce: Boolean): Builder = this
        open fun setForegroundServiceBehavior(behavior: Int): Builder = this
        open fun build(): Notification = Notification()
    }

    abstract class Style
    class BigTextStyle : Style() {
        fun bigText(cs: CharSequence?): BigTextStyle = this
    }
    class Action(icon: Int, title: CharSequence?, intent: PendingIntent?)
}
