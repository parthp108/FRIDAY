// IInferenceDaemon.aidl
package com.health.friday.inference;

import android.graphics.Bitmap;
import com.health.friday.inference.IInferenceCallback;

interface IInferenceDaemon {
    boolean initializeEngine(String modelPath, String systemPromptPrefix);
    oneway void generateStream(String userPrompt, IInferenceCallback callback);
    oneway void generateVisionStream(String userPrompt, in Bitmap screenCapture, IInferenceCallback callback);
    oneway void enterLowMemoryIdle();
}
