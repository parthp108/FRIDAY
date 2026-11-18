package com.health.friday.os.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.HardwareBuffer
import android.os.IBinder
import android.util.Log
import com.health.friday.os.ipc.IFridayCoreOrchestrator

class CoreOrchestratorBridge(private val context: Context) {

    companion object {
        private const val TAG = "CoreOrchestratorBridge"
    }

    private var orchestratorBinder: IFridayCoreOrchestrator? = null
    private var isBound = false
    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, ":core_orchestrator process died. Re-binding automatically...")
        orchestratorBinder = null
        isBound = false
        bindCoreService()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                service?.linkToDeath(deathRecipient, 0)
                orchestratorBinder = IFridayCoreOrchestrator.Stub.asInterface(service)
                isBound = true
                Log.i(TAG, "Successfully bound to :core_orchestrator process anchor")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link death recipient to :core_orchestrator", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, ":core_orchestrator service disconnected")
            orchestratorBinder = null
            isBound = false
        }
    }

    fun bindCoreService() {
        if (isBound) return
        try {
            val intent = Intent(context, FridayCoreService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Could not bind to FridayCoreService", e)
        }
    }

    fun pushScreenContext(packageName: String, domHash: String, layoutHierarchy: String) {
        bindCoreService()
        val binder = orchestratorBinder ?: return
        try {
            binder.pushScreenContext(packageName, domHash, layoutHierarchy)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to push screen context to :core_orchestrator", e)
        }
    }

    fun pushSemanticScreenshot(buffer: HardwareBuffer, packageName: String) {
        bindCoreService()
        val binder = orchestratorBinder ?: return
        try {
            binder.pushSemanticScreenshot(buffer, packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to push screenshot buffer to :core_orchestrator", e)
        }
    }

    fun unbind() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding from FridayCoreService", e)
            }
            isBound = false
            orchestratorBinder = null
        }
    }
}
