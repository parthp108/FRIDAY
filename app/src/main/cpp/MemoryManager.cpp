#include <jni.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>

void* model_buffer = nullptr;
size_t model_size = 0;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_health_friday_os_inference_NativeInference_loadModelMmap(JNIEnv* env, jobject, jstring filepath) {
    const char* path = env->GetStringUTFChars(filepath, nullptr);
    int fd = open(path, O_RDONLY);

    model_size = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);

    // Map model from storage to virtual memory. MAP_SHARED ensures file-backed memory.
    model_buffer = mmap(nullptr, model_size, PROT_READ, MAP_SHARED, fd, 0);
    close(fd);
    env->ReleaseStringUTFChars(filepath, path);

    return (model_buffer != MAP_FAILED);
}

extern "C" JNIEXPORT void JNICALL
Java_com_health_friday_os_inference_NativeInference_setIdleState(JNIEnv*, jobject) {
    if (model_buffer != nullptr) {
        // Tells the Android Linux Kernel: "Reclaim these physical RAM pages."
        // OS sees the app as low-memory and ignores it during lmkd sweeps.
        madvise(model_buffer, model_size, MADV_DONTNEED);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_health_friday_os_inference_NativeInference_setActiveState(JNIEnv*, jobject) {
    if (model_buffer != nullptr) {
        // Force the kernel to aggressively page the model back into physical RAM.
        madvise(model_buffer, model_size, MADV_WILLNEED);
    }
}