#include <jni.h>

#include <atomic>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <sys/stat.h>
#include <unistd.h>

#include "android-base/strings.h"
#include "android-base/unique_fd.h"
#include "adb.h"
#include "client/adb_client.h"
#include "client/commandline.h"
#include "fdevent/fdevent.h"
#include "sysdeps.h"

void adb_server_cleanup();

namespace {
JavaVM* g_vm = nullptr;
jclass g_repo_class = nullptr;
jmethodID g_on_output = nullptr;

std::string g_socket_spec = "localabstract:adbhost";
std::string g_base_dir;
std::atomic<bool> g_server_running{false};
std::mutex g_server_mutex;
std::thread g_server_thread;

const char* kAdbArgv[] = {"adb", nullptr};
const char* kAdbEnvp[] = {nullptr};

class JniThreadAttacher {
  public:
    JniThreadAttacher() {
        if (!g_vm) return;
        if (g_vm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6) != JNI_OK) {
            if (g_vm->AttachCurrentThread(&env_, nullptr) == JNI_OK) {
                attached_ = true;
            } else {
                env_ = nullptr;
            }
        }
    }

    ~JniThreadAttacher() {
        if (attached_ && g_vm) {
            g_vm->DetachCurrentThread();
        }
    }

    JNIEnv* env() const { return env_; }

  private:
    JNIEnv* env_ = nullptr;
    bool attached_ = false;
};

void EmitOutputLine(JNIEnv* env, const std::string& line) {
    if (!env || !g_repo_class || !g_on_output) return;
    jstring jline = env->NewStringUTF(line.c_str());
    if (!jline) return;
    env->CallStaticVoidMethod(g_repo_class, g_on_output, jline);
    env->DeleteLocalRef(jline);
}

int RunShellStreaming(const std::vector<std::string>& argv_storage) {
    std::string command;
    if (argv_storage.size() > 1) {
        command = android::base::Join(std::vector<std::string>(argv_storage.begin() + 1,
                                                              argv_storage.end()),
                                      " ");
    }
    std::string service = "shell:" + command;
    std::string error;
    android::base::unique_fd fd(adb_connect(service, &error));
    if (fd < 0) {
        JniThreadAttacher attacher;
        EmitOutputLine(attacher.env(), "error: " + error);
        return 1;
    }

    JniThreadAttacher attacher;
    JNIEnv* thread_env = attacher.env();
    std::string pending;
    char buffer[1024];
    ssize_t read_bytes = 0;
    while ((read_bytes = adb_read(fd.get(), buffer, sizeof(buffer))) > 0) {
        pending.append(buffer, buffer + read_bytes);
        while (true) {
            size_t pos = pending.find_first_of("\r\n");
            if (pos == std::string::npos) break;
            std::string line = pending.substr(0, pos);
            EmitOutputLine(thread_env, line);
            char sep = pending[pos];
            pending.erase(0, pos + 1);
            if (sep == '\r' && !pending.empty() && pending[0] == '\n') {
                pending.erase(0, 1);
            }
        }
    }
    if (!pending.empty()) {
        EmitOutputLine(thread_env, pending);
    }

    return 0;
}

void EnsureEnv(const std::string& base_dir, const std::string& cache_dir) {
    g_base_dir = base_dir;
    setenv("HOME", g_base_dir.c_str(), 1);
    setenv("ANDROID_DATA", g_base_dir.c_str(), 1);
    setenv("TMPDIR", cache_dir.c_str(), 1);
    setenv("ADB_SERVER_SOCKET", g_socket_spec.c_str(), 1);

    std::string android_dir = g_base_dir + "/.android";
    adb_mkdir(android_dir, 0700);

    __adb_argv = kAdbArgv;
    __adb_envp = kAdbEnvp;
}

std::string MakeResult(bool ok, const std::string& message) {
    if (ok) return message;
    return std::string("ERROR:") + message;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass local = env->FindClass("com/torpos/aadb/state/AdbHostRepository");
    if (!local) {
        return JNI_ERR;
    }
    g_repo_class = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    if (!g_repo_class) {
        return JNI_ERR;
    }
    g_on_output = env->GetStaticMethodID(g_repo_class, "onNativeOutput", "(Ljava/lang/String;)V");
    if (!g_on_output) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_torpos_aadb_native_AdbNative_nativeInit(JNIEnv* env, jobject /*thiz*/,
                                                   jstring baseDir,
                                                   jstring cacheDir,
                                                   jstring socketSpec) {
    if (!baseDir || !cacheDir) return JNI_FALSE;
    const char* base_dir = env->GetStringUTFChars(baseDir, nullptr);
    const char* cache_dir = env->GetStringUTFChars(cacheDir, nullptr);
    const char* socket_spec = socketSpec ? env->GetStringUTFChars(socketSpec, nullptr) : nullptr;

    if (socket_spec && socket_spec[0] != '\0') {
        g_socket_spec = socket_spec;
    }

    EnsureEnv(base_dir, cache_dir);

    static std::once_flag socket_once;
    std::call_once(socket_once, []() { adb_set_socket_spec(g_socket_spec.c_str()); });
    adb_set_transport(kTransportAny, nullptr, 0);
    adb_set_reject_kill_server(true);

    env->ReleaseStringUTFChars(baseDir, base_dir);
    env->ReleaseStringUTFChars(cacheDir, cache_dir);
    if (socket_spec) {
        env->ReleaseStringUTFChars(socketSpec, socket_spec);
    }

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_torpos_aadb_native_AdbNative_nativeStartServer(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_server_mutex);
    if (g_server_running.load()) {
        return env->NewStringUTF("Server already running.");
    }

    g_server_running.store(true);
    if (g_server_thread.joinable()) {
        g_server_thread.join();
    }
    g_server_thread = std::thread([] {
        adb_server_main(false, g_socket_spec, nullptr, -1);
        g_server_running.store(false);
    });

    return env->NewStringUTF("Server started.");
}

JNIEXPORT jstring JNICALL
Java_com_torpos_aadb_native_AdbNative_nativeStopServer(JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_server_mutex);
    if (!g_server_running.load()) {
        return env->NewStringUTF("ERROR:Server is not running.");
    }
    fdevent_run_on_looper([] {
        adb_server_cleanup();
        fdevent_terminate_loop();
    });
    if (g_server_thread.joinable()) {
        g_server_thread.join();
    }
    g_server_running.store(false);
    fdevent_reset();
    return env->NewStringUTF("Server stopped.");
}

JNIEXPORT jstring JNICALL
Java_com_torpos_aadb_native_AdbNative_nativePair(JNIEnv* env, jobject /*thiz*/,
                                                   jstring address,
                                                   jstring code) {
    if (!g_server_running.load()) {
        return env->NewStringUTF("ERROR:Server is not running.");
    }
    if (!address || !code) {
        return env->NewStringUTF("ERROR:Missing address or code.");
    }
    const char* addr = env->GetStringUTFChars(address, nullptr);
    const char* pwd = env->GetStringUTFChars(code, nullptr);

    std::string query = std::string("host:pair:") + pwd + ":" + addr;
    std::string result;
    std::string error;
    bool ok = adb_query(query, &result, &error);

    env->ReleaseStringUTFChars(address, addr);
    env->ReleaseStringUTFChars(code, pwd);

    return env->NewStringUTF(MakeResult(ok, ok ? result : error).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_torpos_aadb_native_AdbNative_nativeConnect(JNIEnv* env, jobject /*thiz*/,
                                                      jstring address) {
    if (!g_server_running.load()) {
        return env->NewStringUTF("ERROR:Server is not running.");
    }
    if (!address) {
        return env->NewStringUTF("ERROR:Missing address.");
    }
    const char* addr = env->GetStringUTFChars(address, nullptr);

    std::string query = std::string("host:connect:") + addr;
    std::string result;
    std::string error;
    bool ok = adb_query(query, &result, &error);

    env->ReleaseStringUTFChars(address, addr);

    return env->NewStringUTF(MakeResult(ok, ok ? result : error).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_torpos_aadb_native_AdbNative_nativeDisconnect(JNIEnv* env, jobject /*thiz*/,
                                                         jstring address) {
    if (!g_server_running.load()) {
        return env->NewStringUTF("ERROR:Server is not running.");
    }
    std::string addr_str;
    if (address) {
        const char* addr = env->GetStringUTFChars(address, nullptr);
        addr_str = addr;
        env->ReleaseStringUTFChars(address, addr);
    }

    std::string query = std::string("host:disconnect:") + addr_str;
    std::string result;
    std::string error;
    bool ok = adb_query(query, &result, &error);

    return env->NewStringUTF(MakeResult(ok, ok ? result : error).c_str());
}

JNIEXPORT jint JNICALL
Java_com_torpos_aadb_native_AdbNative_nativeRunCommandStreaming(JNIEnv* env, jobject /*thiz*/,
                                                                  jobjectArray args) {
    if (!g_server_running.load()) {
        return -1;
    }
    if (!args) {
        return -1;
    }

    const jsize argc = env->GetArrayLength(args);
    std::vector<std::string> argv_storage;
    argv_storage.reserve(argc);
    std::vector<const char*> argv;
    argv.reserve(argc + 1);

    for (jsize i = 0; i < argc; ++i) {
        auto* jstr = static_cast<jstring>(env->GetObjectArrayElement(args, i));
        if (!jstr) continue;
        const char* cstr = env->GetStringUTFChars(jstr, nullptr);
        argv_storage.emplace_back(cstr);
        env->ReleaseStringUTFChars(jstr, cstr);
        env->DeleteLocalRef(jstr);
    }

    for (const auto& arg : argv_storage) {
        argv.push_back(arg.c_str());
    }
    argv.push_back(nullptr);

    if (argv_storage.empty()) {
        return -1;
    }

    if (argv_storage[0] == "shell") {
        return RunShellStreaming(argv_storage);
    }

    int pipe_fds[2];
    if (pipe(pipe_fds) != 0) {
        return -1;
    }

    int stdout_fd = dup(STDOUT_FILENO);
    int stderr_fd = dup(STDERR_FILENO);
    if (stdout_fd < 0 || stderr_fd < 0) {
        adb_close(pipe_fds[0]);
        adb_close(pipe_fds[1]);
        if (stdout_fd >= 0) adb_close(stdout_fd);
        if (stderr_fd >= 0) adb_close(stderr_fd);
        return -1;
    }

    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);

    dup2(pipe_fds[1], STDOUT_FILENO);
    dup2(pipe_fds[1], STDERR_FILENO);
    adb_close(pipe_fds[1]);

    std::thread reader([read_fd = pipe_fds[0]]() {
        JniThreadAttacher attacher;
        JNIEnv* thread_env = attacher.env();
        std::string pending;
        char buffer[1024];
        ssize_t read_bytes = 0;
        while ((read_bytes = adb_read(read_fd, buffer, sizeof(buffer))) > 0) {
            pending.append(buffer, buffer + read_bytes);
            while (true) {
                size_t pos = pending.find_first_of("\r\n");
                if (pos == std::string::npos) break;
                std::string line = pending.substr(0, pos);
                EmitOutputLine(thread_env, line);
                char sep = pending[pos];
                pending.erase(0, pos + 1);
                if (sep == '\r' && !pending.empty() && pending[0] == '\n') {
                    pending.erase(0, 1);
                }
            }
        }
        if (!pending.empty()) {
            EmitOutputLine(thread_env, pending);
        }
        adb_close(read_fd);
    });

    int exit_code = adb_commandline(static_cast<int>(argv_storage.size()), argv.data());

    fflush(stdout);
    fflush(stderr);

    dup2(stdout_fd, STDOUT_FILENO);
    dup2(stderr_fd, STDERR_FILENO);
    adb_close(stdout_fd);
    adb_close(stderr_fd);

    if (reader.joinable()) {
        reader.join();
    }

    return exit_code;
}

}  // extern "C"
