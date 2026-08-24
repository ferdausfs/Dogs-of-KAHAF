// GATE STUB — androidx.security.crypto.
package androidx.security.crypto

import android.content.Context
import android.content.SharedPreferences

class MasterKey private constructor() {

    enum class KeyScheme {
        AES256_GCM
    }

    class Builder(context: Context) {
        fun setKeyScheme(keyScheme: KeyScheme): Builder = this
        fun build(): MasterKey = MasterKey()
    }
}

class EncryptedSharedPreferences private constructor() {

    enum class PrefKeyEncryptionScheme {
        AES256_SIV
    }

    enum class PrefValueEncryptionScheme {
        AES256_GCM
    }

    companion object {
        fun create(
            context: Context,
            fileName: String,
            masterKey: MasterKey,
            keyEncryptionScheme: PrefKeyEncryptionScheme,
            valueEncryptionScheme: PrefValueEncryptionScheme
        ): SharedPreferences = throw RuntimeException("stub")
    }
}
