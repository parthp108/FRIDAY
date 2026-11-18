// IInferenceCallback.aidl
package com.health.friday.inference;

interface IInferenceCallback {
    oneway void onPartialToken(String tokenChunk);
    oneway void onComplete(String fullResponse, long ttftMs, float tokensPerSec);
    oneway void onError(String errorMessage);
}
