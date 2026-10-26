package com.yukesh.needlepocket;

final class NeedleEngine {
    static { System.loadLibrary("needlebridge"); }
    private NeedleEngine() {}
    static native String nativeLoad(String modelPath);
    static native String nativeInit(String systemPrompt, String toolsJson);
    static native String nativeComplete(String prompt, int maxTokens);
    static native void nativeReset();
}