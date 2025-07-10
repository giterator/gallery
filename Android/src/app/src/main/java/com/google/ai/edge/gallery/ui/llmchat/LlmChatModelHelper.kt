/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.common.cleanUpMediapipeTaskErrorMessage
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKey
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TEMPERATURE
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.MAX_IMAGE_COUNT
import com.google.ai.edge.gallery.data.Model
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

private const val TAG = "AGLlmChatModelHelper"

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit

typealias CleanUpListener = () -> Unit

data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)

object LlmChatModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()

  fun getModelStatus(model: Model): String {
    return when {
      model.instance == null -> "Not initialized"
      model.initializing -> "Initializing"
      else -> {
        val instance = model.instance as LlmModelInstance
        when {
          instance.session == null -> "Engine created but session is null"
          else -> "Ready"
        }
      }
    }
  }

  /**
   * Detects if the device supports GPU acceleration for LLM inference.
   * This is a simple heuristic - in a real app, you might want to do more sophisticated detection.
   */
  fun isGpuSupported(): Boolean {
    return try {
      // Try to create a minimal GPU-based inference instance to test GPU support
      // This is a simplified check - in practice, you might want to check device capabilities
      true // For now, assume GPU is supported and let the fallback handle failures
    } catch (e: Exception) {
      Log.d(TAG, "GPU support detection failed: ${e.message}")
      false
    }
  }

  /**
   * Detects if we're running on an emulator.
   * Emulators often have limited or no GPU support for OpenCL.
   */
  fun isEmulator(): Boolean {
    return try {
      val buildFingerprint = android.os.Build.FINGERPRINT
      val buildModel = android.os.Build.MODEL
      val buildProduct = android.os.Build.PRODUCT
      
      val isEmulator = buildFingerprint.contains("generic") ||
                      buildFingerprint.contains("unknown") ||
                      buildModel.contains("google_sdk") ||
                      buildModel.contains("Emulator") ||
                      buildModel.contains("Android SDK built for x86") ||
                      buildProduct.contains("sdk") ||
                      buildProduct.contains("google_sdk") ||
                      buildProduct.contains("sdk_gphone") ||
                      buildProduct.contains("vbox86p") ||
                      buildProduct.contains("emulator")
      
      Log.d(TAG, "Device detection - Build.FINGERPRINT: $buildFingerprint, Build.MODEL: $buildModel, Build.PRODUCT: $buildProduct, isEmulator: $isEmulator")
      isEmulator
    } catch (e: Exception) {
      Log.d(TAG, "Error detecting emulator: ${e.message}")
      false
    }
  }

  /**
   * Logs device information for debugging purposes.
   */
  fun logDeviceInfo() {
    try {
      Log.d(TAG, "Device Info:")
      Log.d(TAG, "  Build.FINGERPRINT: ${android.os.Build.FINGERPRINT}")
      Log.d(TAG, "  Build.MODEL: ${android.os.Build.MODEL}")
      Log.d(TAG, "  Build.PRODUCT: ${android.os.Build.PRODUCT}")
      Log.d(TAG, "  Build.MANUFACTURER: ${android.os.Build.MANUFACTURER}")
      Log.d(TAG, "  Build.BRAND: ${android.os.Build.BRAND}")
      Log.d(TAG, "  Build.DEVICE: ${android.os.Build.DEVICE}")
      Log.d(TAG, "  Build.HARDWARE: ${android.os.Build.HARDWARE}")
      Log.d(TAG, "  Is Emulator: ${isEmulator()}")
      Log.d(TAG, "  GPU Supported: ${isGpuSupported()}")
    } catch (e: Exception) {
      Log.e(TAG, "Error logging device info: ${e.message}")
    }
  }

  /**
   * Gets the recommended backend for the current device.
   */
  fun getRecommendedBackend(accelerator: String): LlmInference.Backend {
    return when (accelerator) {
      Accelerator.CPU.label -> LlmInference.Backend.CPU
      Accelerator.GPU.label -> {
        // If we're on an emulator, prefer CPU to avoid OpenCL issues
        if (isEmulator()) {
          Log.d(TAG, "Running on emulator, preferring CPU over GPU to avoid OpenCL issues")
          LlmInference.Backend.CPU
        } else if (isGpuSupported()) {
          LlmInference.Backend.GPU
        } else {
          Log.d(TAG, "GPU not supported, falling back to CPU")
          LlmInference.Backend.CPU
        }
      }
      else -> LlmInference.Backend.CPU // Default to CPU for safety
    }
  }

  /**
   * Provides a user-friendly error message for GPU initialization failures.
   */
  fun getGpuErrorMessage(originalError: String): String {
    return when {
      originalError.contains("OpenCL") || originalError.contains("libvndksupport.so") -> {
        "GPU acceleration not available on this device. The model will run on CPU instead."
      }
      originalError.contains("dlopen failed") -> {
        "GPU libraries not available. Falling back to CPU mode."
      }
      else -> {
        "GPU initialization failed: $originalError. Falling back to CPU mode."
      }
    }
  }

  fun isModelReady(model: Model): Boolean {
    if (model.instance == null) {
      return false
    }
    
    val instance = model.instance as LlmModelInstance
    return instance.session != null
  }

  fun initialize(context: Context, model: Model, onDone: (String) -> Unit) {
    // Log device information for debugging
    logDeviceInfo()
    
    // Validate model file exists before attempting initialization
    val modelPath = model.getPath(context = context)
    val modelFile = File(modelPath)
    
    if (!modelFile.exists()) {
      Log.e(TAG, "Model file does not exist: $modelPath")
      onDone("Model file not found. Please ensure the model is fully downloaded.")
      return
    }
    
    if (modelFile.length() == 0L) {
      Log.e(TAG, "Model file is empty: $modelPath")
      onDone("Model file is corrupted or empty. Please re-download the model.")
      return
    }
    
    Log.d(TAG, "Model file exists and has size: ${modelFile.length()} bytes")
    
    // Prepare options.
    val maxTokens =
      model.getIntConfigValue(key = ConfigKey.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val accelerator =
      model.getStringConfigValue(key = ConfigKey.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    Log.d(TAG, "Initializing model '${model.name}' with path: $modelPath")
    Log.d(TAG, "Model config - maxTokens: $maxTokens, topK: $topK, topP: $topP, temperature: $temperature, accelerator: $accelerator")
    
    val recommendedBackend = getRecommendedBackend(accelerator)
    Log.d(TAG, "Using recommended backend: $recommendedBackend for accelerator: $accelerator")
    
    val optionsBuilder =
      LlmInference.LlmInferenceOptions.builder()
        .setModelPath(modelPath)
        .setMaxTokens(maxTokens)
        .setPreferredBackend(recommendedBackend)
        .setMaxNumImages(if (model.llmSupportImage) MAX_IMAGE_COUNT else 0)
    val options = optionsBuilder.build()

    // Create an instance of the LLM Inference task and session.
    try {
      Log.d(TAG, "Creating LlmInference instance...")
      val llmInference = LlmInference.createFromOptions(context, options)
      Log.d(TAG, "LlmInference instance created successfully")

      Log.d(TAG, "Creating LlmInferenceSession...")
      val session =
        LlmInferenceSession.createFromOptions(
          llmInference,
          LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(topK)
            .setTopP(topP)
            .setTemperature(temperature)
            .setGraphOptions(
              GraphOptions.builder()
                .setEnableVisionModality(model.llmSupportImage)
                .build()
            )
            .build(),
        )
      Log.d(TAG, "LlmInferenceSession created successfully")
      
      model.instance = LlmModelInstance(engine = llmInference, session = session)
      Log.d(TAG, "Model '${model.name}' initialized successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize model '${model.name}' with preferred backend: ${e.message}", e)
      
      // If GPU initialization failed, try falling back to CPU
      if (recommendedBackend == LlmInference.Backend.GPU) {
        Log.d(TAG, "GPU initialization failed, trying CPU fallback for model '${model.name}'")
        try {
          val cpuOptionsBuilder =
            LlmInference.LlmInferenceOptions.builder()
              .setModelPath(modelPath)
              .setMaxTokens(maxTokens)
              .setPreferredBackend(LlmInference.Backend.CPU)
              .setMaxNumImages(if (model.llmSupportImage) MAX_IMAGE_COUNT else 0)
          val cpuOptions = cpuOptionsBuilder.build()
          
          Log.d(TAG, "Creating LlmInference instance with CPU backend...")
          val llmInference = LlmInference.createFromOptions(context, cpuOptions)
          Log.d(TAG, "LlmInference instance created successfully with CPU")

          Log.d(TAG, "Creating LlmInferenceSession with CPU backend...")
          val session =
            LlmInferenceSession.createFromOptions(
              llmInference,
              LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(topK)
                .setTopP(topP)
                .setTemperature(temperature)
                .setGraphOptions(
                  GraphOptions.builder()
                    .setEnableVisionModality(model.llmSupportImage)
                    .build()
                )
                .build(),
            )
          Log.d(TAG, "LlmInferenceSession created successfully with CPU")
          
          model.instance = LlmModelInstance(engine = llmInference, session = session)
          Log.d(TAG, "Model '${model.name}' initialized successfully with CPU fallback")
          
          // Update the model's accelerator setting to reflect the actual backend used
          model.configValues = model.configValues.toMutableMap().apply {
            put(ConfigKey.ACCELERATOR.label, Accelerator.CPU.label)
          }
          Log.d(TAG, "Updated model '${model.name}' accelerator setting to CPU")
          
          // Provide a user-friendly message about the fallback
          val gpuErrorMessage = getGpuErrorMessage(e.message ?: "Unknown GPU error")
          Log.i(TAG, gpuErrorMessage)
        } catch (cpuException: Exception) {
          Log.e(TAG, "CPU fallback also failed for model '${model.name}': ${cpuException.message}", cpuException)
          onDone("Failed to initialize model. GPU not available and CPU fallback failed: ${cleanUpMediapipeTaskErrorMessage(cpuException.message ?: "Unknown error")}")
          return
        }
      } else {
        onDone(cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error during model initialization"))
        return
      }
    }
    onDone("")
  }

  fun resetSession(model: Model) {
    try {
      Log.d(TAG, "Resetting session for model '${model.name}'")

      val instance = model.instance as LlmModelInstance? ?: run {
        Log.e(TAG, "Model instance is null for model '${model.name}'. Cannot reset session.")
        return
      }
      
      val session = instance.session ?: run {
        Log.e(TAG, "Model session is null for model '${model.name}'. Cannot reset session.")
        return
      }
      
      session.close()

      val inference = instance.engine
      val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature =
        model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
      val newSession =
        LlmInferenceSession.createFromOptions(
          inference,
          LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(topK)
            .setTopP(topP)
            .setTemperature(temperature)
            .setGraphOptions(
              GraphOptions.builder()
                .setEnableVisionModality(model.llmSupportImage)
                .build()
            )
            .build(),
        )
      instance.session = newSession
      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset session", e)
    }
  }

  fun cleanUp(model: Model) {
    if (model.instance == null) {
      return
    }

    val instance = model.instance as LlmModelInstance

    try {
      instance.session.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the LLM Inference session: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the LLM Inference engine: ${e.message}")
    }

    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null
    Log.d(TAG, "Clean up done.")
  }

  fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    images: List<Bitmap> = listOf(),
    audioClips: List<ByteArray> = listOf(),
  ) {
    // Validate model instance exists
    if (model.instance == null) {
      Log.e(TAG, "Model instance is null for model '${model.name}'. Cannot run inference.")
      resultListener("Error: Model not initialized", true)
      return
    }
    
    val instance = model.instance as LlmModelInstance
    
    // Validate session exists
    if (instance.session == null) {
      Log.e(TAG, "Model session is null for model '${model.name}'. Cannot run inference.")
      resultListener("Error: Model session not available", true)
      return
    }

    // Set listener.
    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    // Start async inference.
    //
    // For a model that supports image modality, we need to add the text query chunk before adding
    // image.
    val session = instance.session
    try {
      if (input.trim().isNotEmpty()) {
        session.addQueryChunk(input)
      }
      for (image in images) {
        session.addImage(BitmapImageBuilder(image).build())
      }
      for (audioClip in audioClips) {
        // Uncomment when audio is supported.
        // session.addAudio(audioClip)
      }
      val unused = session.generateResponseAsync(resultListener)
    } catch (e: Exception) {
      Log.e(TAG, "Error during inference for model '${model.name}': ${e.message}", e)
      resultListener("Error during inference: ${e.message}", true)
    }
  }
}
