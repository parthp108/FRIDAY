package com.health.friday.router

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class AppFunctionBridge(private val context: Context) {

    companion object {
        private const val TAG = "FRIDAY_APP_FUNCTIONS"
    }

    /**
     * Executes an Android 16 (API 36+) AppFunction across package boundaries if supported
     * by the target application. Returns true if executed headlessly.
     */
    suspend fun tryExecuteAppFunction(
        targetPackage: String,
        functionIdentifier: String,
        stringParams: Map<String, String>
    ): Boolean {
        if (Build.VERSION.SDK_INT < 36) {
            return false
        }

        return suspendCancellableCoroutine { cont ->
            try {
                val managerClass = Class.forName("android.app.appfunctions.AppFunctionManager")
                val manager = context.getSystemService(managerClass)
                if (manager == null) {
                    cont.resume(false)
                    return@suspendCancellableCoroutine
                }

                // Construct GenericDocument parameters & ExecuteAppFunctionRequest dynamically
                val builderClass = Class.forName("android.app.appfunctions.ExecuteAppFunctionRequest\$Builder")
                val builderConstructor = builderClass.getConstructor(String::class.java, String::class.java)
                val requestBuilder = builderConstructor.newInstance(targetPackage, functionIdentifier)

                val buildMethod = builderClass.getMethod("build")
                val request = buildMethod.invoke(requestBuilder)

                val executeMethod = managerClass.getMethod(
                    "executeAppFunction",
                    request.javaClass,
                    Executor::class.java,
                    CancellationSignal::class.java,
                    OutcomeReceiver::class.java
                )

                val cancellationSignal = CancellationSignal()
                cont.invokeOnCancellation { cancellationSignal.cancel() }

                val receiver = object : OutcomeReceiver<Any, Throwable> {
                    override fun onResult(result: Any?) {
                        Log.i(TAG, "AppFunction $functionIdentifier succeeded on $targetPackage")
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(error: Throwable) {
                        Log.w(TAG, "AppFunction $functionIdentifier unavailable: ${error.message}")
                        if (cont.isActive) cont.resume(false)
                    }
                }

                executeMethod.invoke(manager, request, context.mainExecutor, cancellationSignal, receiver)
            } catch (e: Throwable) {
                Log.d(TAG, "AppFunction fallback to Tier 3 (${e.javaClass.simpleName})")
                if (cont.isActive) cont.resume(false)
            }
        }
    }
}
