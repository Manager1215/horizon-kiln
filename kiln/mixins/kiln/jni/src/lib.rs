//! Kiln's Java in the game's process (D-112): the JVM loaded from the Java home Kiln found and started through JNI, on
//! a thread of its own, running `kiln.Host` as the separate Java process does. horizon-client forbids `unsafe`
//! (D-018), so this is a crate of its own (horizon-mixins' `[[crate]]`), with nothing but `log` (horizon-mixins takes
//! only crates already in Horizon's lock) and a safe API: [`start`] with a [`Channel`] that hands Java the game's
//! command lines and takes Java's replies, the same lines the separate process reads and writes (kiln/java/kiln/Host.java).
//!
//! One JVM per process: JNI can't make a second one, nor start one again after it stopped. A second [`start`] fails,
//! and Kiln falls back on the separate process.

use std::cell::Cell;
use std::ffi::{CStr, CString, c_char, c_void};
use std::path::{Path, PathBuf};
use std::ptr::null_mut;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, OnceLock};

type JInt = i32;
type JObject = *mut c_void;
type JMethodId = *mut c_void;
type Env = *mut *const *const c_void;
type Vm = *mut *const *const c_void;

const JNI_OK: JInt = 0;
const JNI_EDETACHED: JInt = -2;
const JNI_VERSION_21: JInt = 0x0015_0000;

// JNINativeInterface_ (jni.h), counting its four reserved slots.
const FIND_CLASS: usize = 6;
const EXCEPTION_DESCRIBE: usize = 16;
const EXCEPTION_CLEAR: usize = 17;
const DELETE_LOCAL_REF: usize = 23;
const NEW_GLOBAL_REF: usize = 21;
const GET_STATIC_METHOD_ID: usize = 113;
const CALL_STATIC_OBJECT_METHOD_A: usize = 116;
const CALL_STATIC_VOID_METHOD_A: usize = 143;
const NEW_STRING_UTF: usize = 167;
const GET_ARRAY_LENGTH: usize = 171;
const NEW_OBJECT_ARRAY: usize = 172;
const NEW_BYTE_ARRAY: usize = 176;
const GET_BYTE_ARRAY_REGION: usize = 200;
const SET_BYTE_ARRAY_REGION: usize = 208;
const REGISTER_NATIVES: usize = 215;
const EXCEPTION_CHECK: usize = 228;
const GET_JAVA_VM: usize = 219;
// JNIInvokeInterface_, after its three reserved slots.
const GET_ENV: usize = 6;
const ATTACH_CURRENT_THREAD_AS_DAEMON: usize = 7;

/// Where the game's command lines come from and Java's replies go: the separate process's stdin and stdout, as calls.
pub trait Channel: Send + Sync + 'static {
    /// The next command line for Java, waiting for one; none ends `kiln.Host` (the game let Java go).
    fn next(&self) -> Option<String>;
    /// A reply line from Java.
    fn reply(&self, line: String);
    /// `kiln.Host` ended, or never started: why.
    fn ended(&self, why: String);
}

/// What the JVM starts with.
pub struct Config {
    /// The Java home Kiln found (its `lib/server/libjvm.so`, `lib/server/libjvm.dylib` or `bin/server/jvm.dll`).
    pub home: PathBuf,
    /// Kiln's runtime jar first (Kiln's Mixin, its Java agent: D-089), then the libraries' and the mods' jars.
    pub class_path: Vec<PathBuf>,
    /// The JVM's options besides the class path and the agent (`-Xmx`, `-XX:+UseZGC`, `-D...`).
    pub options: Vec<String>,
}

#[repr(C)]
struct JavaVmOption {
    option_string: *mut c_char,
    extra_info: *mut c_void,
}

#[repr(C)]
struct JavaVmInitArgs {
    version: JInt,
    n_options: JInt,
    options: *mut JavaVmOption,
    ignore_unrecognized: u8,
}

#[repr(C)]
struct NativeMethod {
    name: *const c_char,
    signature: *const c_char,
    function: *mut c_void,
}

unsafe fn jni<F: Copy>(table: *mut *const *const c_void, index: usize) -> F {
    unsafe {
        let f: *const c_void = *(*table).add(index);
        std::mem::transmute_copy::<*const c_void, F>(&f)
    }
}

static STARTED: AtomicBool = AtomicBool::new(false);
/// True while `JNI_CreateJavaVM` runs: a hook HotSpot calls then is the JVM giving up its start (D-190).
static MAKING: AtomicBool = AtomicBool::new(false);
/// Where [`start`] waits for the JVM: a hook that fires while it is being made answers there instead.
static WAITING: OnceLock<std::sync::mpsc::SyncSender<Result<(), String>>> = OnceLock::new();
static CHANNEL: OnceLock<Arc<dyn Channel>> = OnceLock::new();

/// What a call from any thread needs, once `kiln.Host` is about to run: global references, so they hold on every
/// thread.
struct Host {
    vm: Vm,
    host: JObject,
    direct: JMethodId,
}

// The JVM's pointers and global references are process-wide by JNI's contract.
unsafe impl Send for Host {}
unsafe impl Sync for Host {}

static HOST: OnceLock<Host> = OnceLock::new();

type Ask<'a> = dyn FnMut(&str) -> String + 'a;

thread_local! {
    /// The answers to Java's questions during this thread's [`call`] (`kiln.Jni.ask`).
    static ASK: Cell<Option<*mut Ask<'static>>> = const { Cell::new(None) };
}

/// Whether this process has a JVM of Kiln's (started, or starting): a second can't be made.
pub fn started() -> bool {
    STARTED.load(Ordering::Acquire)
}

/// A system whose Java homes Kiln knows, so each one's layout is a pure function that runs (and is tested) anywhere.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Os {
    Linux,
    MacOs,
    Windows,
}

impl Os {
    /// The system this was built for (Linux's layout on any other Unix).
    pub const HERE: Os = if cfg!(windows) {
        Os::Windows
    } else if cfg!(target_os = "macos") {
        Os::MacOs
    } else {
        Os::Linux
    };

    /// `base` and then `relative`, whose parts are apart by `/`, joined with this system's separator: what
    /// `Path::join` does on that system, also where another runs it (a Windows path in a test on Linux).
    pub fn join(self, base: &Path, relative: &str) -> PathBuf {
        let separator = if self == Os::Windows { "\\" } else { "/" };
        let mut joined = base.as_os_str().to_owned();
        let ends = joined.as_encoded_bytes().last().copied();
        if !joined.is_empty() && ends != Some(b'/') && !(self == Os::Windows && ends == Some(b'\\'))
        {
            joined.push(separator);
        }
        joined.push(relative.replace('/', separator));
        PathBuf::from(joined)
    }

    /// The JVM library inside a Java home: `bin\server\jvm.dll`, `lib/server/libjvm.dylib`, `lib/server/libjvm.so`
    /// (JDK 9 on; Kiln needs 21).
    pub fn libjvm(self, home: &Path) -> PathBuf {
        match self {
            Os::Windows => self.join(home, "bin/server/jvm.dll"),
            Os::MacOs => self.join(home, "lib/server/libjvm.dylib"),
            Os::Linux => self.join(home, "lib/server/libjvm.so"),
        }
    }

    /// Java's signal chaining library, loaded before the JVM's (D-117); Windows has none.
    pub fn jsig(self, home: &Path) -> Option<PathBuf> {
        match self {
            Os::Windows => None,
            Os::MacOs => Some(self.join(home, "lib/libjsig.dylib")),
            Os::Linux => Some(self.join(home, "lib/libjsig.so")),
        }
    }

    /// The C runtime a Windows JDK carries in its `bin`, which `jvm.dll` (in `bin\server`) needs and Windows doesn't
    /// look for there: Java's own launcher loads them first by their full paths, and so does Kiln. Each is loaded
    /// when the home has it (a machine with the Visual C++ runtime installed finds it anyway).
    pub fn runtime_libraries(self, home: &Path) -> Vec<PathBuf> {
        match self {
            Os::Windows => ["vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll"]
                .iter()
                .map(|dll| self.join(home, &format!("bin/{dll}")))
                .collect(),
            Os::MacOs | Os::Linux => Vec::new(),
        }
    }

    /// `path` without Windows's verbatim prefix (`\\?\C:\x` is `C:\x`, `\\?\UNC\host\share` is `\\host\share`), which
    /// `std::fs::canonicalize` adds and Java's paths refuse (`?` is no file name's character to them); Java adds it
    /// back itself for a path too long. Other systems' paths, and others on Windows, are unchanged.
    pub fn plain(self, path: &Path) -> PathBuf {
        if self != Os::Windows {
            return path.to_path_buf();
        }
        let bytes = path.as_os_str().as_encoded_bytes();
        let Some(rest) = bytes.strip_prefix(br"\\?\") else {
            return path.to_path_buf();
        };
        // SAFETY (for `from_encoded_bytes_unchecked` below): each cut is right after an ASCII byte of the original.
        let plain = if let Some(unc) = rest.strip_prefix(br"UNC\") {
            let mut v = br"\\".to_vec();
            v.extend_from_slice(unc);
            v
        } else if rest.len() >= 3
            && rest[0].is_ascii_alphabetic()
            && rest[1] == b':'
            && rest[2] == b'\\'
        {
            rest.to_vec()
        } else {
            // A device or volume GUID path: only the verbatim form names it.
            return path.to_path_buf();
        };
        PathBuf::from(unsafe { std::ffi::OsString::from_encoded_bytes_unchecked(plain) })
    }
}

/// The JVM library inside a Java home, on this system.
pub fn libjvm(home: &Path) -> PathBuf {
    Os::HERE.libjvm(home)
}

/// `path` without Windows's verbatim prefix, on this system ([`Os::plain`]).
pub fn plain(path: &Path) -> PathBuf {
    Os::HERE.plain(path)
}

/// Starts the JVM on a thread of its own and runs `kiln.Host` there with `channel`; returns once the JVM is made (its
/// agent run), or why it couldn't be. Once a process: a second call fails.
pub fn start(mut config: Config, channel: Arc<dyn Channel>) -> Result<(), String> {
    // Java's paths refuse Windows's verbatim prefix; the JVM finds its home from the library's own path.
    config.home = plain(&config.home);
    config.class_path = config.class_path.iter().map(|p| plain(p)).collect();
    if STARTED.swap(true, Ordering::AcqRel) {
        return Err("Java already ran in the game's process, and a process has one JVM".into());
    }
    if !libjvm(&config.home).is_file() {
        // Nothing loaded yet: a later start may try a Java home that has one.
        STARTED.store(false, Ordering::Release);
        return Err(format!("{} isn't there", libjvm(&config.home).display()));
    }
    let _ = CHANNEL.set(channel.clone());
    let (made, made_rx) = std::sync::mpsc::sync_channel::<Result<(), String>>(1);
    let _ = WAITING.set(made.clone());
    std::thread::Builder::new()
        .name("kiln-java".into())
        // Java's own threads get 1 MiB (-Xss); this one runs the mods' entrypoints and Mixin's transformer, as the
        // separate process's main thread does with the system's 8 MiB.
        .stack_size(16 << 20)
        .spawn(move || {
            let env = match unsafe { create(&config) } {
                Ok(env) => env,
                Err(e) => {
                    let _ = made.send(Err(e));
                    return;
                }
            };
            let _ = made.send(Ok(()));
            let why = match unsafe { run_host(env) } {
                Ok(()) => "it closed its output".to_owned(),
                Err(e) => e,
            };
            channel.ended(why);
        })
        .map_err(|e| e.to_string())?;
    made_rx
        .recv()
        .map_err(|_| "Java's thread ended before the JVM was made".to_owned())?
}

/// HotSpot's `abort` hook (JNI's invocation option): HotSpot calls it as it gives up (`os::abort`, after printing
/// why), then ends the process with `_exit(1)` when it returns. While the JVM is being made that is a start that
/// failed (a heap it can't reserve, a collector the system lacks: `vm_exit_during_initialization`), and the game must
/// go on with the separate process (D-190): the error goes to [`start`], and this thread, the one making the JVM,
/// waits here for good so HotSpot never ends the process. The JVM stays half-made and idle, its library loaded; a
/// process has one JVM anyway. After the start (a crash of Java's own, its `hs_err` written), it returns: the game
/// ends as it did without the hook, for the crash window to report (D-112).
extern "system" fn jvm_aborts() {
    if MAKING.load(Ordering::Acquire) {
        gave_up("it gave up while starting".to_owned());
    }
}

/// HotSpot's `exit` hook: it runs for an exit of Java's own once the JVM is made (`System.exit`, `Runtime.halt`:
/// Kiln's agent rewrites those, D-112, so only what gets past it), then HotSpot exits the process itself, as it did
/// without the hook. Kiln only says so in the log first. During the start (HotSpot calls it there for none of the
/// failures read in JDK 25, but it might) it is a failed start, as [`jvm_aborts`].
extern "system" fn jvm_exits(code: JInt) {
    if MAKING.load(Ordering::Acquire) {
        gave_up(format!("it exited while starting (code {code})"));
    }
    log::error!(
        "Kiln: Java ends the game's process (exit code {code}): something got past Kiln's agent"
    );
}

/// A JVM that gave up while being made: `why` goes to [`start`], and the calling thread never returns to HotSpot.
fn gave_up(why: String) -> ! {
    if let Some(waiting) = WAITING.get() {
        let _ = waiting.try_send(Err(format!(
            "the JVM wasn't made: {why}; Java's own words are in the game's output"
        )));
    }
    loop {
        std::thread::park();
    }
}

/// Loads the library, then the JVM made with `config`: this thread's `JNIEnv`.
unsafe fn create(config: &Config) -> Result<Env, String> {
    let lib = unsafe { load(&config.home)? };
    let created = unsafe { symbol(lib, c"JNI_CreateJavaVM")? };
    type Create = unsafe extern "system" fn(*mut Vm, *mut Env, *mut JavaVmInitArgs) -> JInt;
    let create: Create = unsafe { std::mem::transmute::<*mut c_void, Create>(created) };
    let joined = std::env::join_paths(&config.class_path).map_err(|e| e.to_string())?;
    let mut options = vec![format!("-Djava.class.path={}", joined.to_string_lossy())];
    // Kiln's runtime is a Java agent too (kiln.Mixins): only the runtime jar, named so, which comes first.
    if let Some(runtime) = config
        .class_path
        .first()
        .filter(|p| p.file_name() == Some("kiln-runtime.jar".as_ref()))
    {
        options.push(format!("-javaagent:{}", runtime.display()));
    }
    options.extend(config.options.iter().cloned());
    let strings: Vec<CString> = options
        .iter()
        .map(|o| platform_string(o))
        .collect::<Result<_, _>>()?;
    let mut raw: Vec<JavaVmOption> = strings
        .iter()
        .map(|s| JavaVmOption {
            option_string: s.as_ptr().cast_mut(),
            extra_info: null_mut(),
        })
        .collect();
    // The hooks (D-190): a JVM that gives up while being made is an error here, not the game's end.
    raw.push(JavaVmOption {
        option_string: c"abort".as_ptr().cast_mut(),
        extra_info: jvm_aborts as extern "system" fn() as *mut c_void,
    });
    raw.push(JavaVmOption {
        option_string: c"exit".as_ptr().cast_mut(),
        extra_info: jvm_exits as extern "system" fn(JInt) as *mut c_void,
    });
    let mut args = JavaVmInitArgs {
        version: JNI_VERSION_21,
        n_options: raw.len() as JInt,
        options: raw.as_mut_ptr(),
        ignore_unrecognized: 0,
    };
    let (mut vm, mut env): (Vm, Env) = (null_mut(), null_mut());
    MAKING.store(true, Ordering::Release);
    let code = unsafe { create(&mut vm, &mut env, &mut args) };
    MAKING.store(false, Ordering::Release);
    if code != JNI_OK {
        return Err(format!(
            "the JVM wasn't made (JNI_CreateJavaVM: {code}; Java's own words are in the game's output)"
        ));
    }
    Ok(env)
}

/// A JVM option as the JVM reads it: in the platform's encoding (`sun.jnu.encoding`), which decodes the `-D`
/// properties, the class path among them, and the agent's path. That is UTF-8 on macOS and on Linux with a UTF-8
/// locale (as the separate `java` reads its arguments), and the ANSI code page on Windows: a user folder named in
/// Cyrillic is code page 1251's bytes there, not UTF-8's. An option the code page can't write is refused, and the
/// separate process runs instead.
fn platform_string(option: &str) -> Result<CString, String> {
    #[cfg(windows)]
    {
        windows_ansi(option)
    }
    #[cfg(not(windows))]
    {
        CString::new(option).map_err(|e| format!("{option}: {e}"))
    }
}

#[cfg(windows)]
fn windows_ansi(option: &str) -> Result<CString, String> {
    unsafe extern "system" {
        fn GetACP() -> u32;
        fn WideCharToMultiByte(
            code_page: u32,
            flags: u32,
            wide: *const u16,
            wide_len: i32,
            bytes: *mut u8,
            bytes_len: i32,
            default: *const u8,
            used_default: *mut i32,
        ) -> i32;
    }
    const CP_ACP: u32 = 0;
    const CP_UTF8: u32 = 65001;
    const WC_NO_BEST_FIT_CHARS: u32 = 0x400;
    if option.contains('\0') {
        return Err(format!("{option}: a NUL in a JVM option"));
    }
    // A process whose manifest asks for UTF-8 (Windows 10 1903 on) has UTF-8 as its ANSI code page.
    if unsafe { GetACP() } == CP_UTF8 {
        return CString::new(option).map_err(|e| format!("{option}: {e}"));
    }
    let wide: Vec<u16> = option.encode_utf16().collect();
    let Ok(wide_len) = i32::try_from(wide.len()) else {
        return Err("a JVM option too long".into());
    };
    if wide.is_empty() {
        return Ok(CString::default());
    }
    let mut used_default = 0i32;
    let len = unsafe {
        WideCharToMultiByte(
            CP_ACP,
            WC_NO_BEST_FIT_CHARS,
            wide.as_ptr(),
            wide_len,
            null_mut(),
            0,
            std::ptr::null(),
            &mut used_default,
        )
    };
    if len <= 0 {
        return Err(format!(
            "{option}: Windows couldn't write it in its ANSI code page"
        ));
    }
    let mut bytes = vec![0u8; len as usize];
    let written = unsafe {
        WideCharToMultiByte(
            CP_ACP,
            WC_NO_BEST_FIT_CHARS,
            wide.as_ptr(),
            wide_len,
            bytes.as_mut_ptr(),
            len,
            std::ptr::null(),
            &mut used_default,
        )
    };
    if written != len || used_default != 0 {
        return Err(format!(
            "{option} has characters this Windows's ANSI code page ({}) can't write, and the JVM reads its options in it",
            unsafe { GetACP() }
        ));
    }
    CString::new(bytes).map_err(|e| format!("{option}: {e}"))
}

/// `line` (a server call's command line) to `kiln.Host.direct` on this thread, attached to the JVM the first time; Java's
/// questions during it (`kiln.Jni.ask`, tab-separated escaped fields) get `ask`'s answers. Java's answer line, none
/// when it gave none; an error when there is no JVM, or Java threw past Kiln (in java.log).
pub fn call(line: &str, ask: &mut Ask<'_>) -> Result<Option<String>, String> {
    let host = HOST
        .get()
        .ok_or("Java isn't running in the game's process")?;
    let env = unsafe { env_here(host.vm) }.ok_or("this thread couldn't attach to the JVM")?;
    // The closure lives across the call only; the previous one comes back after (a call inside an answer).
    let ask: *mut Ask<'static> =
        unsafe { std::mem::transmute::<*mut Ask<'_>, *mut Ask<'static>>(ask) };
    let previous = ASK.with(|a| a.replace(Some(ask)));
    struct Restore(Option<*mut Ask<'static>>);
    impl Drop for Restore {
        fn drop(&mut self) {
            ASK.with(|a| a.set(self.0));
        }
    }
    let _restore = Restore(previous);
    type CallObject = unsafe extern "system" fn(Env, JObject, JMethodId, *const u64) -> JObject;
    type Delete = unsafe extern "system" fn(Env, JObject);
    unsafe {
        let array = bytes_to_java(env, line.as_bytes());
        if array.is_null() {
            thrown(env, "no memory for the call's line")?;
            return Err("no memory for the call's line".into());
        }
        let arg = [array as u64];
        let answer = jni::<CallObject>(env, CALL_STATIC_OBJECT_METHOD_A)(
            env,
            host.host,
            host.direct,
            arg.as_ptr(),
        );
        jni::<Delete>(env, DELETE_LOCAL_REF)(env, array);
        thrown(env, "kiln.Host.direct threw")?;
        if answer.is_null() {
            return Ok(None);
        }
        let bytes = bytes_from_java(env, answer);
        jni::<Delete>(env, DELETE_LOCAL_REF)(env, answer);
        Ok(Some(utf8(bytes)))
    }
}

/// This thread's `JNIEnv`, attached as a daemon (named as the thread) the first time.
unsafe fn env_here(vm: Vm) -> Option<Env> {
    #[repr(C)]
    struct AttachArgs {
        version: JInt,
        name: *const c_char,
        group: JObject,
    }
    type GetEnv = unsafe extern "system" fn(Vm, *mut Env, JInt) -> JInt;
    type Attach = unsafe extern "system" fn(Vm, *mut Env, *mut AttachArgs) -> JInt;
    let mut env: Env = null_mut();
    unsafe {
        match jni::<GetEnv>(vm, GET_ENV)(vm, &mut env, JNI_VERSION_21) {
            JNI_OK => Some(env),
            JNI_EDETACHED => {
                let name =
                    CString::new(std::thread::current().name().unwrap_or("game").to_owned()).ok();
                let mut args = AttachArgs {
                    version: JNI_VERSION_21,
                    name: name.as_ref().map_or(std::ptr::null(), |n| n.as_ptr()),
                    group: null_mut(),
                };
                (jni::<Attach>(vm, ATTACH_CURRENT_THREAD_AS_DAEMON)(vm, &mut env, &mut args)
                    == JNI_OK)
                    .then_some(env)
            }
            _ => None,
        }
    }
}

unsafe fn bytes_to_java(env: Env, bytes: &[u8]) -> JObject {
    type NewBytes = unsafe extern "system" fn(Env, JInt) -> JObject;
    type SetBytes = unsafe extern "system" fn(Env, JObject, JInt, JInt, *const i8);
    let Ok(len) = JInt::try_from(bytes.len()) else {
        return null_mut();
    };
    unsafe {
        let array = jni::<NewBytes>(env, NEW_BYTE_ARRAY)(env, len);
        if !array.is_null() {
            jni::<SetBytes>(env, SET_BYTE_ARRAY_REGION)(env, array, 0, len, bytes.as_ptr().cast());
        }
        array
    }
}

unsafe fn bytes_from_java(env: Env, array: JObject) -> Vec<u8> {
    type Length = unsafe extern "system" fn(Env, JObject) -> JInt;
    type GetBytes = unsafe extern "system" fn(Env, JObject, JInt, JInt, *mut i8);
    unsafe {
        let len = jni::<Length>(env, GET_ARRAY_LENGTH)(env, array).max(0);
        let mut buffer = vec![0u8; len as usize];
        jni::<GetBytes>(env, GET_BYTE_ARRAY_REGION)(env, array, 0, len, buffer.as_mut_ptr().cast());
        buffer
    }
}

fn utf8(bytes: Vec<u8>) -> String {
    String::from_utf8(bytes).unwrap_or_else(|e| String::from_utf8_lossy(e.as_bytes()).into_owned())
}

/// `kiln.Jni`'s methods bound to the channel, then `kiln.Host.main`, until it returns.
unsafe fn run_host(env: Env) -> Result<(), String> {
    type Find = unsafe extern "system" fn(Env, *const c_char) -> JObject;
    type Register = unsafe extern "system" fn(Env, JObject, *const NativeMethod, JInt) -> JInt;
    type GetStatic =
        unsafe extern "system" fn(Env, JObject, *const c_char, *const c_char) -> JMethodId;
    type NewString = unsafe extern "system" fn(Env, *const c_char) -> JObject;
    type NewArray = unsafe extern "system" fn(Env, JInt, JObject, JObject) -> JObject;
    type CallVoid = unsafe extern "system" fn(Env, JObject, JMethodId, *const u64);
    unsafe {
        let native = jni::<Find>(env, FIND_CLASS)(env, c"kiln/Jni".as_ptr());
        thrown(env, "kiln.Jni isn't in Kiln's runtime jar")?;
        let methods = [
            NativeMethod {
                name: c"next".as_ptr(),
                signature: c"()[B".as_ptr(),
                function: native_next as *mut c_void,
            },
            NativeMethod {
                name: c"reply".as_ptr(),
                signature: c"([B)V".as_ptr(),
                function: native_reply as *mut c_void,
            },
            NativeMethod {
                name: c"ask".as_ptr(),
                signature: c"([B)[B".as_ptr(),
                function: native_ask as *mut c_void,
            },
        ];
        if jni::<Register>(env, REGISTER_NATIVES)(
            env,
            native,
            methods.as_ptr(),
            methods.len() as JInt,
        ) != JNI_OK
        {
            thrown(env, "kiln.Jni's methods")?;
            return Err("kiln.Jni's methods couldn't be bound".into());
        }
        let host = jni::<Find>(env, FIND_CLASS)(env, c"kiln/Host".as_ptr());
        thrown(env, "kiln.Host isn't in Kiln's runtime jar")?;
        let main = jni::<GetStatic>(env, GET_STATIC_METHOD_ID)(
            env,
            host,
            c"main".as_ptr(),
            c"([Ljava/lang/String;)V".as_ptr(),
        );
        thrown(env, "kiln.Host.main")?;
        type Global = unsafe extern "system" fn(Env, JObject) -> JObject;
        type GetVm = unsafe extern "system" fn(Env, *mut Vm) -> JInt;
        let direct = jni::<GetStatic>(env, GET_STATIC_METHOD_ID)(
            env,
            host,
            c"direct".as_ptr(),
            c"([B)[B".as_ptr(),
        );
        thrown(env, "kiln.Host.direct")?;
        let mut vm: Vm = null_mut();
        if jni::<GetVm>(env, GET_JAVA_VM)(env, &mut vm) != JNI_OK {
            return Err("the JVM's handle".into());
        }
        let global = jni::<Global>(env, NEW_GLOBAL_REF)(env, host);
        let _ = HOST.set(Host {
            vm,
            host: global,
            direct,
        });
        let string = jni::<NewString>(env, NEW_STRING_UTF)(env, c"".as_ptr());
        let string_class = jni::<Find>(env, FIND_CLASS)(env, c"java/lang/String".as_ptr());
        let args = jni::<NewArray>(env, NEW_OBJECT_ARRAY)(env, 0, string_class, string);
        thrown(env, "kiln.Host's arguments")?;
        let arg = [args as u64];
        jni::<CallVoid>(env, CALL_STATIC_VOID_METHOD_A)(env, host, main, arg.as_ptr());
        thrown(env, "kiln.Host.main threw")
    }
}

/// A pending Java exception, printed to Java's error stream (java.log) and cleared, as an error saying `what`.
unsafe fn thrown(env: Env, what: &str) -> Result<(), String> {
    type Check = unsafe extern "system" fn(Env) -> u8;
    type Plain = unsafe extern "system" fn(Env);
    unsafe {
        if jni::<Check>(env, EXCEPTION_CHECK)(env) != 0 {
            jni::<Plain>(env, EXCEPTION_DESCRIBE)(env);
            jni::<Plain>(env, EXCEPTION_CLEAR)(env);
            return Err(what.to_owned());
        }
    }
    Ok(())
}

/// `kiln.Jni.next()`: the next command line as UTF-8, null when the game let Java go.
extern "system" fn native_next(env: Env, _class: JObject) -> JObject {
    let line = std::panic::catch_unwind(|| CHANNEL.get().and_then(|c| c.next())).unwrap_or(None);
    let Some(line) = line else {
        return null_mut();
    };
    // Null with an OutOfMemoryError pending: Java sees it.
    unsafe { bytes_to_java(env, line.as_bytes()) }
}

/// `kiln.Jni.ask(byte[])`: Java's question during this thread's [`call`], its fields tab-separated and escaped; the
/// game's answer, null outside a call.
extern "system" fn native_ask(env: Env, _class: JObject, bytes: JObject) -> JObject {
    let Some(ask) = ASK.with(|a| a.get()) else {
        return null_mut();
    };
    if bytes.is_null() {
        return null_mut();
    }
    let what = utf8(unsafe { bytes_from_java(env, bytes) });
    // The closure is the caller's, alive for its call (`call`'s `Restore` takes it out after).
    let answer =
        std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe { (*ask)(&what) }));
    match answer {
        Ok(answer) => unsafe { bytes_to_java(env, answer.as_bytes()) },
        Err(_) => null_mut(),
    }
}

/// `kiln.Jni.reply(byte[])`: a reply line as UTF-8.
extern "system" fn native_reply(env: Env, _class: JObject, bytes: JObject) {
    if bytes.is_null() {
        return;
    }
    let line = utf8(unsafe { bytes_from_java(env, bytes) });
    let _ = std::panic::catch_unwind(|| {
        if let Some(c) = CHANNEL.get() {
            c.reply(line);
        }
    });
}

/// The files [`remove_at_exit`] removes.
static AT_EXIT: std::sync::Mutex<Vec<PathBuf>> = std::sync::Mutex::new(Vec::new());
static AT_EXIT_SET: AtomicBool = AtomicBool::new(false);

unsafe extern "C" {
    fn atexit(callback: extern "C" fn()) -> i32;
}

extern "C" fn remove_files() {
    // Never waits: at a process's end on Windows the other threads are gone, and one may have held the lock.
    let files = match AT_EXIT.try_lock() {
        Ok(mut files) => std::mem::take(&mut *files),
        Err(std::sync::TryLockError::Poisoned(poisoned)) => {
            std::mem::take(&mut *poisoned.into_inner())
        }
        Err(std::sync::TryLockError::WouldBlock) => return,
    };
    for file in files {
        let _ = std::fs::remove_file(file);
    }
}

/// Windows: Rust's `std::process::exit` is `ExitProcess`, which runs no C runtime `atexit` handler (tried: the file
/// stays), and Horizon's quits end there. What `ExitProcess` does run is each module's TLS callbacks with
/// `DLL_PROCESS_DETACH`, the game's own among them (Rust's standard library keeps its thread-locals' destructors there
/// too): so the files go there. Like `atexit` on Linux, it runs for an exit, a return from `main` and the exit a panic
/// ends in, and not for a crash or a kill (`TerminateProcess`): tried each on Windows 11.
#[cfg(windows)]
#[used]
#[unsafe(link_section = ".CRT$XLK")]
static AT_PROCESS_DETACH: unsafe extern "system" fn(*mut c_void, u32, *mut c_void) =
    at_process_detach;

#[cfg(windows)]
unsafe extern "system" fn at_process_detach(
    _module: *mut c_void,
    reason: u32,
    _reserved: *mut c_void,
) {
    const DLL_PROCESS_DETACH: u32 = 0;
    if reason == DLL_PROCESS_DETACH && AT_EXIT_SET.load(Ordering::SeqCst) {
        remove_files();
    }
}

/// `path` is removed when the process exits cleanly (`exit`, a return from `main`: the C runtime's `atexit`, and on
/// Windows a TLS callback, `at_process_detach`), and stays when it doesn't (a crash, a signal, a kill): D-112's
/// `running` note, which tells the crash window the game stopped without closing. Horizon has no quit hook for mods; its quits all end in `exit`, and so does a Rust panic it
/// reports itself (its crash report, exit code 101), which the crash window leaves to Horizon.
pub fn remove_at_exit(path: PathBuf) {
    AT_EXIT.lock().unwrap_or_else(|e| e.into_inner()).push(path);
    if !AT_EXIT_SET.swap(true, Ordering::SeqCst) {
        // SAFETY: `remove_files` is an `extern "C" fn()` that never unwinds (it only removes files and ignores errors).
        if unsafe { atexit(remove_files) } != 0 {
            log::warn!(
                "Kiln: atexit refused a handler: a clean exit will look like a crash to the crash window"
            );
        }
    }
}

/// The machine's memory in bytes, for the heap's limit; none where it can't be read.
pub fn physical_memory() -> Option<u64> {
    #[cfg(target_os = "linux")]
    {
        let info = std::fs::read_to_string("/proc/meminfo").ok()?;
        let line = info.lines().find(|l| l.starts_with("MemTotal:"))?;
        let kib: u64 = line.split_whitespace().nth(1)?.parse().ok()?;
        Some(kib * 1024)
    }
    #[cfg(target_os = "macos")]
    {
        unsafe extern "C" {
            fn sysctlbyname(
                name: *const c_char,
                old: *mut c_void,
                old_len: *mut usize,
                new: *mut c_void,
                new_len: usize,
            ) -> i32;
        }
        let mut bytes: u64 = 0;
        let mut len = std::mem::size_of::<u64>();
        let ok = unsafe {
            sysctlbyname(
                c"hw.memsize".as_ptr(),
                (&raw mut bytes).cast(),
                &mut len,
                null_mut(),
                0,
            )
        };
        (ok == 0).then_some(bytes)
    }
    #[cfg(windows)]
    {
        #[repr(C)]
        struct MemoryStatus {
            length: u32,
            load: u32,
            total_phys: u64,
            rest: [u64; 6],
        }
        unsafe extern "system" {
            fn GlobalMemoryStatusEx(status: *mut MemoryStatus) -> i32;
        }
        let mut status = MemoryStatus {
            length: std::mem::size_of::<MemoryStatus>() as u32,
            load: 0,
            total_phys: 0,
            rest: [0; 6],
        };
        (unsafe { GlobalMemoryStatusEx(&mut status) } != 0).then_some(status.total_phys)
    }
    #[cfg(not(any(target_os = "linux", target_os = "macos", windows)))]
    {
        None
    }
}

#[cfg(unix)]
mod library {
    use super::*;
    unsafe extern "C" {
        fn dlopen(path: *const c_char, mode: i32) -> *mut c_void;
        fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
        fn dlerror() -> *const c_char;
    }
    const RTLD_NOW: i32 = 2;
    #[cfg(target_os = "macos")]
    const RTLD_GLOBAL: i32 = 8;
    #[cfg(not(target_os = "macos"))]
    const RTLD_GLOBAL: i32 = 0x100;

    pub unsafe fn open(path: &Path) -> Result<*mut c_void, String> {
        let c = CString::new(path.as_os_str().as_encoded_bytes()).map_err(|e| e.to_string())?;
        let lib = unsafe { dlopen(c.as_ptr(), RTLD_NOW | RTLD_GLOBAL) };
        if lib.is_null() {
            let e = unsafe { dlerror() };
            let e = if e.is_null() {
                String::new()
            } else {
                unsafe { CStr::from_ptr(e) }.to_string_lossy().into_owned()
            };
            return Err(format!("can't load {}: {e}", path.display()));
        }
        Ok(lib)
    }

    pub unsafe fn find(lib: *mut c_void, name: &CStr) -> *mut c_void {
        unsafe { dlsym(lib, name.as_ptr()) }
    }
}

#[cfg(windows)]
mod library {
    use super::*;
    use std::os::windows::ffi::OsStrExt;
    unsafe extern "system" {
        fn LoadLibraryExW(path: *const u16, file: *mut c_void, flags: u32) -> *mut c_void;
        fn GetProcAddress(lib: *mut c_void, name: *const c_char) -> *mut c_void;
        fn GetLastError() -> u32;
    }
    /// The library's own folder is searched first for what it needs, not the game's.
    const LOAD_WITH_ALTERED_SEARCH_PATH: u32 = 0x8;

    pub unsafe fn open(path: &Path) -> Result<*mut c_void, String> {
        let wide: Vec<u16> = path.as_os_str().encode_wide().chain([0]).collect();
        let lib =
            unsafe { LoadLibraryExW(wide.as_ptr(), null_mut(), LOAD_WITH_ALTERED_SEARCH_PATH) };
        if lib.is_null() {
            return Err(format!("can't load {}: error {}", path.display(), unsafe {
                GetLastError()
            }));
        }
        Ok(lib)
    }

    pub unsafe fn find(lib: *mut c_void, name: &CStr) -> *mut c_void {
        unsafe { GetProcAddress(lib, name.as_ptr()) }
    }
}

/// The libraries [`load`] opens from `home`, in order: on Linux and macOS Java's signal chaining library first, so the
/// JVM knows the game's own handlers (a fault that isn't Java's goes to them); on Windows the C runtime the JDK
/// carries; each when the home has it. The JVM's library last, which must be there.
fn libraries(os: Os, home: &Path, has: impl Fn(&Path) -> bool) -> Vec<PathBuf> {
    let mut first: Vec<PathBuf> = os
        .jsig(home)
        .into_iter()
        .chain(os.runtime_libraries(home))
        .collect();
    first.retain(|p| has(p));
    first.push(os.libjvm(home));
    first
}

/// The JVM's library from `home`, after what it needs ([`libraries`]).
unsafe fn load(home: &Path) -> Result<*mut c_void, String> {
    let mut all = libraries(Os::HERE, home, Path::is_file);
    let jvm = all.pop().expect("the JVM's library is last");
    for first in all {
        // A failure here shows as the JVM's own, next, if it mattered.
        if let Err(e) = unsafe { library::open(&first) } {
            log::warn!("Kiln: {e}");
        }
    }
    unsafe { library::open(&jvm) }
}

unsafe fn symbol(lib: *mut c_void, name: &CStr) -> Result<*mut c_void, String> {
    let f = unsafe { library::find(lib, name) };
    if f.is_null() {
        return Err(format!(
            "the JVM's library has no {}",
            name.to_string_lossy()
        ));
    }
    Ok(f)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// This test, run again as a child process (`KILN_TEST_AT_EXIT` names its file and how it ends).
    fn child(how: &str, file: &Path) -> std::process::ExitStatus {
        std::process::Command::new(std::env::current_exe().unwrap())
            .args([
                "--exact",
                "tests::a_file_kept_for_exit_goes_with_a_clean_exit_only",
                "--nocapture",
            ])
            .env("KILN_TEST_AT_EXIT", format!("{how}|{}", file.display()))
            .stderr(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .status()
            .unwrap()
    }

    #[test]
    fn a_file_kept_for_exit_goes_with_a_clean_exit_only() {
        // The child: the note is the game's `running` note, then the process ends as told.
        if let Ok(told) = std::env::var("KILN_TEST_AT_EXIT") {
            let (how, file) = told.split_once('|').unwrap();
            remove_at_exit(PathBuf::from(file));
            match how {
                "exit" => std::process::exit(0),
                "abort" => std::process::abort(),
                _ => return,
            }
        }
        let dir = std::env::temp_dir().join(format!("kiln-at-exit-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        // Horizon's quits end in `exit` (on Windows `ExitProcess`): the note goes, as when `main` returns.
        for how in ["exit", "return"] {
            let note = dir.join(how);
            std::fs::write(&note, "running").unwrap();
            child(how, &note);
            assert!(
                !note.exists(),
                "{how}: the note stayed, and the crash window would take it for a crash"
            );
        }
        // A crash: it stays, for the crash window.
        let note = dir.join("abort");
        std::fs::write(&note, "running").unwrap();
        assert!(!child("abort", &note).success());
        assert!(note.exists(), "a crash removed the note");
        let _ = std::fs::remove_dir_all(&dir);
    }

    struct Nothing;
    impl Channel for Nothing {
        fn next(&self) -> Option<String> {
            None
        }
        fn reply(&self, _: String) {}
        fn ended(&self, _: String) {}
    }

    /// A JDK for the tests that make a JVM: `JAVA_HOME` (check.sh sets it), else none.
    fn java_home() -> Option<PathBuf> {
        let home = PathBuf::from(std::env::var_os("JAVA_HOME")?);
        libjvm(&home).is_file().then_some(home)
    }

    #[test]
    fn a_jvm_that_gives_up_starting_is_an_error_and_the_process_goes_on() {
        // The child: a heap ZGC can't have (it supports 16 TiB) makes HotSpot give up while starting
        // (`vm_exit_during_initialization`), which ended the whole process before the abort hook (D-190).
        if std::env::var_os("KILN_TEST_GIVE_UP").is_some() {
            let config = Config {
                home: java_home().unwrap(),
                class_path: Vec::new(),
                options: vec!["-XX:+UseZGC".into(), "-Xmx20t".into(), "-Xrs".into()],
            };
            let e = start(config, Arc::new(Nothing)).unwrap_err();
            println!("start: {e}");
            // A second start in this process is refused: the half-made JVM is still its one JVM.
            assert!(started());
            println!("the process goes on");
            std::process::exit(0);
        }
        let Some(home) = java_home() else {
            println!("no JAVA_HOME with a JVM: nothing to check");
            return;
        };
        let out = std::process::Command::new(std::env::current_exe().unwrap())
            .args([
                "--exact",
                "tests::a_jvm_that_gives_up_starting_is_an_error_and_the_process_goes_on",
                "--nocapture",
            ])
            .env("KILN_TEST_GIVE_UP", "1")
            .env("JAVA_HOME", &home)
            .output()
            .unwrap();
        let stdout = String::from_utf8_lossy(&out.stdout);
        println!("child: {stdout}{}", String::from_utf8_lossy(&out.stderr));
        assert!(
            out.status.success(),
            "the process ended with the JVM: {:?}",
            out.status
        );
        assert!(
            stdout.contains("start: the JVM wasn't made: it gave up while starting"),
            "{stdout}"
        );
        assert!(stdout.contains("the process goes on"), "{stdout}");
        // HotSpot said why itself, as it does in the game's output.
        assert!(stdout.contains("Java heap too large"), "{stdout}");
    }

    #[test]
    fn a_home_without_a_jvm_is_refused_and_leaves_the_jvm_unmade() {
        let home = std::env::temp_dir().join(format!("kiln-jni-no-jvm-{}", std::process::id()));
        let config = Config {
            home: home.clone(),
            class_path: Vec::new(),
            options: Vec::new(),
        };
        let e = start(config, Arc::new(Nothing)).unwrap_err();
        assert!(e.contains("isn't there"), "{e}");
        assert!(!started());
    }

    fn p(s: &str) -> PathBuf {
        PathBuf::from(s)
    }

    #[test]
    fn each_system_joins_with_its_own_separator() {
        assert_eq!(
            Os::Windows.join(&p(r"C:\jdk"), "bin/server"),
            p(r"C:\jdk\bin\server")
        );
        assert_eq!(Os::Windows.join(&p(r"C:\jdk\"), "bin"), p(r"C:\jdk\bin"));
        assert_eq!(Os::Windows.join(&p("C:/jdk/"), "bin"), p("C:/jdk/bin"));
        assert_eq!(
            Os::Linux.join(&p("/usr/lib/jvm/x"), "lib/server"),
            p("/usr/lib/jvm/x/lib/server")
        );
        assert_eq!(Os::MacOs.join(&p("/x/"), "lib"), p("/x/lib"));
        // A backslash is a name's character on Unix, not a separator.
        assert_eq!(Os::Linux.join(&p(r"/a\"), "b"), p(r"/a\/b"));
        // On this system, as `Path::join`.
        let home = std::env::temp_dir();
        assert_eq!(
            Os::HERE.join(&home, "lib/server"),
            home.join("lib").join("server")
        );
    }

    #[test]
    fn each_systems_jvm_library_is_where_its_jdk_keeps_it() {
        // Temurin 21 on Windows, the Minecraft launcher's runtime on macOS (its jre.bundle), a distribution's on Linux.
        let windows = p(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot");
        assert_eq!(
            Os::Windows.libjvm(&windows),
            p(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot\bin\server\jvm.dll")
        );
        let mac = p(
            "/Users/a/Library/Application Support/minecraft/runtime/java-runtime-delta/mac-os-arm64/\
                     java-runtime-delta/jre.bundle/Contents/Home",
        );
        assert_eq!(Os::MacOs.libjvm(&mac), mac.join("lib/server/libjvm.dylib"));
        assert_eq!(Os::MacOs.jsig(&mac), Some(mac.join("lib/libjsig.dylib")));
        let linux = p("/usr/lib/jvm/java-21-openjdk");
        assert_eq!(Os::Linux.libjvm(&linux), linux.join("lib/server/libjvm.so"));
        assert_eq!(Os::Linux.jsig(&linux), Some(linux.join("lib/libjsig.so")));
        assert_eq!(Os::Windows.jsig(&windows), None);
        assert_eq!(libjvm(&linux), Os::HERE.libjvm(&linux));
    }

    #[test]
    fn what_loads_first_is_what_the_home_has_and_the_jvm_comes_last() {
        let home = p(r"C:\Users\Никита\jdk");
        let has_vcruntime = |l: &Path| l == Path::new(r"C:\Users\Никита\jdk\bin\vcruntime140.dll");
        assert_eq!(
            libraries(Os::Windows, &home, has_vcruntime),
            [
                p(r"C:\Users\Никита\jdk\bin\vcruntime140.dll"),
                p(r"C:\Users\Никита\jdk\bin\server\jvm.dll")
            ]
        );
        assert_eq!(
            libraries(Os::Windows, &home, |_| true),
            [
                p(r"C:\Users\Никита\jdk\bin\vcruntime140.dll"),
                p(r"C:\Users\Никита\jdk\bin\vcruntime140_1.dll"),
                p(r"C:\Users\Никита\jdk\bin\msvcp140.dll"),
                p(r"C:\Users\Никита\jdk\bin\server\jvm.dll"),
            ]
        );
        let mac = p("/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home");
        assert_eq!(
            libraries(Os::MacOs, &mac, |_| true),
            [
                mac.join("lib/libjsig.dylib"),
                mac.join("lib/server/libjvm.dylib")
            ]
        );
        // A home without libjsig: the JVM alone (D-117: it then can't chain the game's handlers).
        assert_eq!(
            libraries(Os::Linux, &p("/j"), |_| false),
            [p("/j/lib/server/libjvm.so")]
        );
    }

    #[test]
    fn windows_verbatim_paths_lose_their_prefix_for_java() {
        assert_eq!(
            Os::Windows.plain(&p(r"\\?\C:\Games\Horizon\kiln")),
            p(r"C:\Games\Horizon\kiln")
        );
        assert_eq!(
            Os::Windows.plain(&p(r"\\?\UNC\nas\games\kiln")),
            p(r"\\nas\games\kiln")
        );
        assert_eq!(Os::Windows.plain(&p(r"\\?\д:\x")), p(r"\\?\д:\x"));
        // A volume GUID path has no other form; nor has a device's.
        let volume = p(r"\\?\Volume{0b1c2d3e-0000-0000-0000-000000000000}\jdk");
        assert_eq!(Os::Windows.plain(&volume), volume);
        assert_eq!(Os::Windows.plain(&p(r"C:\jdk")), p(r"C:\jdk"));
        assert_eq!(Os::Windows.plain(&p(r"\\?\C:")), p(r"\\?\C:"));
        // Elsewhere a path is a path.
        assert_eq!(Os::Linux.plain(&p(r"\\?\C:\x")), p(r"\\?\C:\x"));
        assert_eq!(Os::MacOs.plain(&p("/Users/a")), p("/Users/a"));
    }

    #[cfg(not(windows))]
    #[test]
    fn options_reach_the_jvm_in_utf8_off_windows() {
        let o = platform_string("-Dkiln.dir=/home/никита/kiln").unwrap();
        assert_eq!(o.as_bytes(), "-Dkiln.dir=/home/никита/kiln".as_bytes());
        assert!(platform_string("-Da=\0").is_err());
    }

    #[test]
    fn the_machine_has_memory() {
        assert!(physical_memory().is_some_and(|m| m > 256 << 20));
    }
}
