#include <jni.h>
#include <fstream>
#include <iterator>
#include <mutex>
#include <string>
#include <vector>
#include "needle.h"

namespace {
std::vector<unsigned char> modelBytes;
std::mutex engineMutex;
bool loaded = false;
bool initialized = false;

std::string fromJava(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* p = env->GetStringUTFChars(value, nullptr);
    std::string out = p ? p : "";
    if (p) env->ReleaseStringUTFChars(value, p);
    return out;
}
jstring toJava(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yukesh_needlepocket_NeedleEngine_nativeLoad(JNIEnv* env, jclass, jstring pathValue) {
    std::lock_guard<std::mutex> lock(engineMutex);
    std::ifstream file(fromJava(env, pathValue), std::ios::binary);
    if (!file) return toJava(env, "Could not open model file.");
    modelBytes.assign(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
    if (modelBytes.size() < 1024 * 1024) {
        modelBytes.clear();
        loaded = false;
        return toJava(env, "Model download is incomplete. Clear the download and retry.");
    }
    const int rc = needle_load(modelBytes.data(), static_cast<unsigned long long>(modelBytes.size()));
    if (rc < 0) {
        loaded = false;
        initialized = false;
        return toJava(env, "Needle model load failed (native error " + std::to_string(rc) + ").");
    }
    loaded = true;
    initialized = false;
    return toJava(env, "OK");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yukesh_needlepocket_NeedleEngine_nativeInit(JNIEnv* env, jclass, jstring systemValue, jstring toolsValue) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!loaded) return toJava(env, "Model has not loaded.");
    const std::string system = fromJava(env, systemValue);
    const std::string tools = fromJava(env, toolsValue);
    const int rc = needle_init(system.c_str(), tools.c_str(), nullptr);
    if (rc < 0) {
        initialized = false;
        return toJava(env, "Needle initialization failed (native error " + std::to_string(rc) + ").");
    }
    initialized = true;
    return toJava(env, "OK");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_yukesh_needlepocket_NeedleEngine_nativeComplete(JNIEnv* env, jclass, jstring promptValue, jint maxTokens) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!initialized) return toJava(env, "__NEEDLE_ERROR__:Engine is not initialized.");
    const std::string prompt = fromJava(env, promptValue);
    std::vector<char> output(256 * 1024, '\0');
    const int rc = needle_complete(prompt.c_str(), static_cast<int>(maxTokens), output.data(), static_cast<int>(output.size()));
    if (rc < 0) {
        return toJava(env, "__NEEDLE_ERROR__:Needle completion failed (native error " + std::to_string(rc) + ").");
    }
    if (output[0] == '\0') return toJava(env, "__NEEDLE_ERROR__:Needle returned an empty response.");
    return toJava(env, std::string(output.data()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_yukesh_needlepocket_NeedleEngine_nativeReset(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (initialized) needle_reset();
}