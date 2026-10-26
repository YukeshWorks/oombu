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
jstring toJava(JNIEnv* env, const std::string& s) { return env->NewStringUTF(s.c_str()); }
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_yukesh_needlepocket_NeedleEngine_nativeLoad(JNIEnv* env, jclass, jstring pathValue) {
    std::lock_guard<std::mutex> lock(engineMutex);
    std::ifstream file(fromJava(env, pathValue), std::ios::binary);
    if (!file) return toJava(env, "Could not open the model file.");
    modelBytes.assign(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
    if (modelBytes.size() < 1024 * 1024) { modelBytes.clear(); return toJava(env, "Model is incomplete. Retry the download."); }
    int rc = needle_load(modelBytes.data(), static_cast<unsigned long long>(modelBytes.size()));
    if (rc < 0) {
        const char* msg = needle_last_error();
        loaded = false;
        return toJava(env, std::string("Needle load failed: ") + (msg ? msg : "unknown error"));
    }
    loaded = true; initialized = false;
    return toJava(env, "OK");
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_yukesh_needlepocket_NeedleEngine_nativeInit(JNIEnv* env, jclass, jstring systemValue, jstring toolsValue) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!loaded) return toJava(env, "Model has not loaded.");
    std::string system = fromJava(env, systemValue), tools = fromJava(env, toolsValue);
    int rc = needle_init(system.c_str(), tools.c_str(), nullptr);
    if (rc < 0) {
        const char* msg = needle_last_error(); initialized = false;
        return toJava(env, std::string("Needle init failed: ") + (msg ? msg : "unknown error"));
    }
    initialized = true;
    return toJava(env, "OK");
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_yukesh_needlepocket_NeedleEngine_nativeComplete(JNIEnv* env, jclass, jstring promptValue, jint maxTokens) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!initialized) return toJava(env, "__NEEDLE_ERROR__:Engine is not initialized.");
    std::string prompt = fromJava(env, promptValue);
    std::vector<char> output(512 * 1024, '\0');
    int rc = needle_complete(prompt.c_str(), nullptr, 0, static_cast<int>(maxTokens), output.data(), static_cast<int>(output.size()));
    if (rc < 0) {
        const char* msg = needle_last_error();
        std::string details = msg ? msg : "unknown engine error";
        if (output[0]) details += " | " + std::string(output.data());
        return toJava(env, "__NEEDLE_ERROR__:" + details);
    }
    return toJava(env, std::string(output.data()));
}
extern "C" JNIEXPORT void JNICALL
Java_com_yukesh_needlepocket_NeedleEngine_nativeReset(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (initialized) needle_reset();
}