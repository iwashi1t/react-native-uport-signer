@file:Suppress("unused", "TooManyFunctions")

package me.uport.rnsigner

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Base64

import com.facebook.react.bridge.*

import com.uport.sdk.signer.UportSigner
import com.uport.sdk.signer.UportSigner.Companion.ERR_BLANK_KEY
import com.uport.sdk.signer.UportSigner.Companion.ERR_ENCODING_ERROR
import com.uport.sdk.signer.UportSigner.Companion.asGenericLabel
import com.uport.sdk.signer.UportSigner.Companion.asSeedLabel
import com.uport.sdk.signer.encryption.KeyProtection.Level
import me.uport.sdk.signer.keyToBase64

/**
 * This module deals with creating, importing, and using private keys.
 * It also provides methods to check for the security settings of the device
 */
open class RNUportSignerModule(reactContext: ReactApplicationContext)
    : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "RNUportSignerModule"

    @ReactMethod
    fun hasSecureKeyguard(promise: Promise) {
        UportSigner().hasSecuredKeyguard(currentActivity ?: reactApplicationContext) { promise.resolve(it) }
    }

    @ReactMethod
    fun hasFingerprintHardware(promise: Promise) {
        UportSigner().hasFingerprintHardware(currentActivity ?: reactApplicationContext) { promise.resolve(it) }
    }

    @ReactMethod
    fun hasSetupFingerprints(promise: Promise) {
        UportSigner().hasSetupFingerprints(currentActivity ?: reactApplicationContext) { promise.resolve(it) }
    }

    @ReactMethod
    fun launchSecuritySettings() {
        val securityIntent = Intent(Settings.ACTION_SECURITY_SETTINGS)
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)

        val intent = if (securityIntent.resolveActivity(reactApplicationContext.packageManager) != null) {
            securityIntent
        } else {
            settingsIntent
        }

        var context = currentActivity as Context?
        if (context == null) {
            context = reactApplicationContext!!
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    @ReactMethod
    fun createKeyPair(level: String?, promise: Promise?) {

        promise!!

        val keyLevel: Level = keyLevelFromString(level ?: "")

        UportSigner().createKey(
                currentActivity ?: reactApplicationContext,
                keyLevel
        ) { err, address, pubKey ->
            if (err != null) {
                return@createKey promise.reject(err)
            }
            val map = WritableNativeMap()
            map.putString("address", address)
            map.putString("pubKey", pubKey)
            return@createKey promise.resolve(map)
        }
    }

    @ReactMethod
    fun importKey(privateKey: String?, level: String?, promise: Promise?) {

        promise!!

        val keyLevel: Level = keyLevelFromString(level ?: "")
        if (TextUtils.isEmpty(privateKey)) {
            return promise.reject(ERR_BLANK_KEY, "Cannot import a null or empty private key")
        }

        val privKeyBytes = try {
            Base64.decode(privateKey, Base64.DEFAULT)
        } catch (exception: Exception) {
            return promise.reject(exception)
        }

        if (privKeyBytes.size != PRIVATE_KEY_SIZE) {
            return promise.reject(ERR_ENCODING_ERROR,
                    "Expecting a $PRIVATE_KEY_SIZE byte key, base64 encoded; " +
                            "managed to decode ${privKeyBytes.size} bytes")
        }

        UportSigner().saveKey(
                currentActivity ?: reactApplicationContext,
                keyLevel,
                privKeyBytes
        ) { err, address, pubKey ->
            if (err != null) {
                return@saveKey promise.reject(err)
            }
            val map = WritableNativeMap()
            map.putString("address", address)
            map.putString("pubKey", pubKey)
            return@saveKey promise.resolve(map)
        }
    }

    @ReactMethod
    fun deleteKey(address: String) {
        UportSigner().deleteKey(
                currentActivity ?: reactApplicationContext,
                address
        )
    }

    @ReactMethod
    fun signTx(address: String?, txPayload: String?, prompt: String?, promise: Promise?) {

        promise!!

        val addr = address ?: return promise.reject(IllegalArgumentException("address can't be null"))
        val payload = txPayload ?: return promise.reject(IllegalArgumentException("data can't be null"))

        val activity = currentActivity ?: reactApplicationContext

        UportSigner().signTransaction(
                activity,
                addr,
                payload,
                prompt ?: ""
        ) { err, sigData ->
            if (err != null) {
                return@signTransaction promise.reject(err)
            }
            val map = WritableNativeMap()
            map.putInt("v", sigData.v.toInt())
            map.putString("r", sigData.r.keyToBase64())
            map.putString("s", sigData.s.keyToBase64())
            return@signTransaction promise.resolve(map)
        }
    }

    @ReactMethod
    fun signJwt(address: String?, data: String?, prompt: String?, promise: Promise?) {

        promise!!

        val addr = address ?: return promise.reject(IllegalArgumentException("address can't be null"))
        val bundle = data ?: return promise.reject(IllegalArgumentException("data can't be null"))

        val activity = currentActivity ?: reactApplicationContext

        UportSigner().signJwtBundle(
                activity,
                addr,
                bundle,
                prompt ?: ""
        ) { err, sigData ->
            if (err != null) {
                return@signJwtBundle promise.reject(err)
            }
            val map = WritableNativeMap()
            val rec : Int = if (sigData.v.toInt() > 1) (sigData.v.toInt() + 1) % 2 else sigData.v.toInt()
            map.putInt("v", rec)
            map.putString("r", sigData.r.keyToBase64())
            map.putString("s", sigData.s.keyToBase64())
            return@signJwtBundle promise.resolve(map)
        }
    }

    @ReactMethod
    fun allAddresses(promise: Promise?) {

        promise!!

        UportSigner().allAddresses(
                currentActivity ?: reactApplicationContext
        ) { addresses ->
            val ret = WritableNativeArray()
            addresses.forEach { ret.pushString(it) }
            promise.resolve(ret)
        }
    }

    @ReactMethod
    fun storeEncryptionPrivateKey(publicKey: String?, privateKey: String?, level: String?, promise: Promise?) {

        promise!!

        val keyLevel: Level = keyLevelFromString(level ?: "")

        val pubKey = publicKey ?: return promise.reject(IllegalArgumentException(ERR_BLANK_KEY))
        val privKey = privateKey ?: return promise.reject(IllegalArgumentException(ERR_BLANK_KEY))

        UportSigner().storeEncryptedPayload(
                currentActivity ?: reactApplicationContext,
                keyLevel,
                asGenericLabel(pubKey),
                privKey.toByteArray(Charsets.UTF_8)
        ) { err, result ->
            if (err != null) {
                return@storeEncryptedPayload promise.reject(err)
            }
            return@storeEncryptedPayload promise.resolve(result)
        }
    }


    @ReactMethod
    fun loadEncryptionPrivateKey(publicKey: String?, promise: Promise?) {

        promise!!

        val activity = currentActivity ?: reactApplicationContext

        if (TextUtils.isEmpty(publicKey)) {
            return promise.reject(ERR_BLANK_KEY, "public key cannot be empty")
        }

        UportSigner().loadEncryptedPayload(
                activity,
                asGenericLabel(publicKey!!),
                ""
        ) { err, decrypted ->
            if (err != null) {
                return@loadEncryptedPayload promise.reject(err)
            }
            return@loadEncryptedPayload promise.resolve(String(decrypted))
        }
    }

    @ReactMethod
    fun storeRecoverySeed(address: String?, seed: String?, level: String?, promise: Promise?) {

        promise!!

        val keyLevel: Level = keyLevelFromString(level ?: "")

        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(seed)) {
            return promise.reject(ERR_BLANK_KEY, "neither address nor seed should be blank")
        }

        //these should never throw because of the check above
        seed!!
        address!!

        UportSigner().storeEncryptedPayload(
                currentActivity ?: reactApplicationContext,
                keyLevel,
                asSeedLabel(address),
                seed.toByteArray()
        ) { err, result ->
            if (err != null) {
                return@storeEncryptedPayload promise.reject(err)
            }
            return@storeEncryptedPayload promise.resolve(result)

        }
    }

    @ReactMethod
    fun loadRecoverySeed(address: String?, promise: Promise?) {

        promise!!

        val activity = currentActivity ?: reactApplicationContext

        if (TextUtils.isEmpty(address)) {
            return promise.reject(ERR_BLANK_KEY, "address cannot be empty")
        }

        address!!

        UportSigner().loadEncryptedPayload(
                activity,
                asSeedLabel(address),
                ""
        ) { err, decrypted ->
            if (err != null) {
                return@loadEncryptedPayload promise.reject(err)
            }
            return@loadEncryptedPayload promise.resolve(String(decrypted))
        }
    }

    internal fun keyLevelFromString(level: String): Level = when (level) {

    //Prompt is only asked once per session or period of time
        "singleprompt" -> Level.SINGLE_PROMPT

    //prompt - Prompt every time
        "prompt" -> Level.PROMPT

    //simple - Not hardware protected but you don't loose your key if you change pin
        "simple" -> Level.SIMPLE

    //cloud - Backed up in some cloud storage
        else -> {
            Level.SIMPLE
        }
    }

    companion object {
        const val PRIVATE_KEY_SIZE = 32
    }
}