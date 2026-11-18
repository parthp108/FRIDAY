package com.health.friday.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.health.friday.FridayApplication

class FridayRecognitionService : RecognitionService() {

    override fun onStartListening(intent: Intent?, callback: Callback?) {
        val app = applicationContext as? FridayApplication
        val speechRecognizer = app?.container?.speechRecognizer
        if (speechRecognizer != null) {
            speechRecognizer.startListening(
                onResult = { result ->
                    val bundle = Bundle().apply {
                        putStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION,
                            arrayListOf(result)
                        )
                    }
                    runCatching { callback?.results(bundle) }
                },
                onError = { _ ->
                    runCatching { callback?.error(SpeechRecognizer.ERROR_NO_MATCH) }
                }
            )
        } else {
            runCatching { callback?.error(SpeechRecognizer.ERROR_CLIENT) }
        }
    }

    override fun onStopListening(callback: Callback?) {
        val app = applicationContext as? FridayApplication
        app?.container?.speechRecognizer?.stopListening()
    }

    override fun onCancel(callback: Callback?) {
        val app = applicationContext as? FridayApplication
        app?.container?.speechRecognizer?.stopListening()
    }
}
