// GATE STUB — androidx.activity.result.contract.
package androidx.activity.result.contract

open class ActivityResultContract<I, O>

class ActivityResultContracts {
    class RequestPermission : ActivityResultContract<String, Boolean>()
    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>()
    class StartActivityForResult : ActivityResultContract<android.content.Intent, androidx.activity.result.ActivityResult>()
    class OpenDocument : ActivityResultContract<Array<String>, android.net.Uri?>()
    class CreateDocument(mimeType: String = "*/*") : ActivityResultContract<String, android.net.Uri?>()
}
