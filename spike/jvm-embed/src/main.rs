// Spike: start a JVM inside a Rust program and call both ways, through hand-written JNI and no crates, as a
// Horizon native mod must (its modules may use only horizon-client's own dependencies).
//
//   sh build-jar.sh
//   cargo run --release -- <path to libjvm> target/hello.jar
//
// Checks: the JVM starts in-process; Rust calls a static Java method and reads its String; Java calls
// Rust through RegisterNatives; reflection, lambdas and streams work; HotSpot's SIGSEGV-based null
// checks live with Rust's own signal handler; how long the start and a call take.

use std::ffi::{CStr, CString, c_char, c_void};
use std::ptr::null_mut;
use std::sync::atomic::{AtomicI64, Ordering};
use std::time::Instant;

type JInt = i32;
type JLong = i64;
type JObject = *mut c_void;
type JMethodId = *mut c_void;
/// `JNIEnv *`: a pointer to the pointer to the JNI function table.
type Env = *mut *const *const c_void;
/// `JavaVM *`, likewise for the invocation table.
type Vm = *mut *const *const c_void;

const JNI_OK: JInt = 0;
const JNI_VERSION_21: JInt = 0x0015_0000;
const RTLD_NOW: i32 = 2;

// Indices into JNINativeInterface_ (jni.h), counting its four reserved slots.
const FIND_CLASS: usize = 6;
const EXCEPTION_DESCRIBE: usize = 16;
const DELETE_LOCAL_REF: usize = 23;
const GET_STATIC_METHOD_ID: usize = 113;
const CALL_STATIC_OBJECT_METHOD_A: usize = 116;
const CALL_STATIC_INT_METHOD_A: usize = 131;
const CALL_STATIC_VOID_METHOD_A: usize = 143;
const GET_STRING_UTF_CHARS: usize = 169;
const RELEASE_STRING_UTF_CHARS: usize = 170;
const REGISTER_NATIVES: usize = 215;
const EXCEPTION_CHECK: usize = 228;

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
    fn_ptr: *mut c_void,
}

unsafe extern "C" {
    fn dlopen(path: *const c_char, mode: i32) -> *mut c_void;
    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
    fn dlerror() -> *const c_char;
}

/// The JNI function at `index` of `env`'s table, as the function type `F`.
unsafe fn jni<F: Copy>(env: Env, index: usize) -> F {
    unsafe {
        let f = *(*env).add(index);
        std::mem::transmute_copy::<*const c_void, F>(&f)
    }
}

fn dl_error() -> String {
    let e = unsafe { dlerror() };
    if e.is_null() {
        "unknown error".into()
    } else {
        unsafe { CStr::from_ptr(e) }.to_string_lossy().into_owned()
    }
}

/// A `java.lang.String` as Rust text (JNI's modified UTF-8, the same for text without NUL or emoji).
unsafe fn java_string(env: Env, s: JObject) -> String {
    if s.is_null() {
        return String::new();
    }
    type Get = unsafe extern "system" fn(Env, JObject, *mut u8) -> *const c_char;
    type Release = unsafe extern "system" fn(Env, JObject, *const c_char);
    unsafe {
        let chars = jni::<Get>(env, GET_STRING_UTF_CHARS)(env, s, null_mut());
        let text = CStr::from_ptr(chars).to_string_lossy().into_owned();
        jni::<Release>(env, RELEASE_STRING_UTF_CHARS)(env, s, chars);
        text
    }
}

/// A JVM in this process and the starting thread's `JNIEnv`.
struct Jvm {
    _vm: Vm,
    env: Env,
}

impl Jvm {
    fn start(libjvm: &str, options: &[String]) -> Result<Jvm, String> {
        let path = CString::new(libjvm).map_err(|e| e.to_string())?;
        let lib = unsafe { dlopen(path.as_ptr(), RTLD_NOW) };
        if lib.is_null() {
            return Err(format!("can't load {libjvm}: {}", dl_error()));
        }
        let symbol = unsafe { dlsym(lib, c"JNI_CreateJavaVM".as_ptr()) };
        if symbol.is_null() {
            return Err(format!("{libjvm} has no JNI_CreateJavaVM: {}", dl_error()));
        }
        type Create = unsafe extern "system" fn(*mut Vm, *mut Env, *mut JavaVmInitArgs) -> JInt;
        let create: Create = unsafe { std::mem::transmute::<*mut c_void, Create>(symbol) };
        let strings: Vec<CString> = options
            .iter()
            .map(|o| CString::new(o.as_str()).map_err(|e| e.to_string()))
            .collect::<Result<_, _>>()?;
        let mut raw: Vec<JavaVmOption> = strings
            .iter()
            .map(|s| JavaVmOption {
                option_string: s.as_ptr().cast_mut(),
                extra_info: null_mut(),
            })
            .collect();
        let mut args = JavaVmInitArgs {
            version: JNI_VERSION_21,
            n_options: raw.len() as JInt,
            options: raw.as_mut_ptr(),
            ignore_unrecognized: 0,
        };
        let (mut vm, mut env): (Vm, Env) = (null_mut(), null_mut());
        let code = unsafe { create(&mut vm, &mut env, &mut args) };
        if code != JNI_OK {
            return Err(format!("JNI_CreateJavaVM failed with {code}"));
        }
        Ok(Jvm { _vm: vm, env })
    }

    /// Err when the last call threw; the exception's stack trace goes to stderr.
    fn thrown(&self, what: &str) -> Result<(), String> {
        type Check = unsafe extern "system" fn(Env) -> u8;
        type Describe = unsafe extern "system" fn(Env);
        unsafe {
            if jni::<Check>(self.env, EXCEPTION_CHECK)(self.env) != 0 {
                jni::<Describe>(self.env, EXCEPTION_DESCRIBE)(self.env);
                return Err(format!("{what} threw a Java exception"));
            }
        }
        Ok(())
    }

    fn find_class(&self, name: &CStr) -> Result<JObject, String> {
        type Find = unsafe extern "system" fn(Env, *const c_char) -> JObject;
        let class = unsafe { jni::<Find>(self.env, FIND_CLASS)(self.env, name.as_ptr()) };
        self.thrown(&format!("FindClass {name:?}"))?;
        Ok(class)
    }

    fn register(
        &self,
        class: JObject,
        methods: &[(&CStr, &CStr, *mut c_void)],
    ) -> Result<(), String> {
        type Register = unsafe extern "system" fn(Env, JObject, *const NativeMethod, JInt) -> JInt;
        let table: Vec<NativeMethod> = methods
            .iter()
            .map(|&(name, signature, fn_ptr)| NativeMethod {
                name: name.as_ptr(),
                signature: signature.as_ptr(),
                fn_ptr,
            })
            .collect();
        let code = unsafe {
            jni::<Register>(self.env, REGISTER_NATIVES)(
                self.env,
                class,
                table.as_ptr(),
                table.len() as JInt,
            )
        };
        self.thrown("RegisterNatives")?;
        if code != JNI_OK {
            return Err(format!("RegisterNatives failed with {code}"));
        }
        Ok(())
    }

    fn static_method(
        &self,
        class: JObject,
        name: &CStr,
        signature: &CStr,
    ) -> Result<JMethodId, String> {
        type Get =
            unsafe extern "system" fn(Env, JObject, *const c_char, *const c_char) -> JMethodId;
        let id = unsafe {
            jni::<Get>(self.env, GET_STATIC_METHOD_ID)(
                self.env,
                class,
                name.as_ptr(),
                signature.as_ptr(),
            )
        };
        self.thrown(&format!("GetStaticMethodID {name:?}"))?;
        Ok(id)
    }

    fn call_void(&self, class: JObject, method: JMethodId) -> Result<(), String> {
        type Call = unsafe extern "system" fn(Env, JObject, JMethodId, *const c_void);
        unsafe {
            jni::<Call>(self.env, CALL_STATIC_VOID_METHOD_A)(
                self.env,
                class,
                method,
                std::ptr::null(),
            )
        };
        self.thrown("a static void call")
    }

    fn call_int(&self, class: JObject, method: JMethodId) -> Result<JInt, String> {
        type Call = unsafe extern "system" fn(Env, JObject, JMethodId, *const c_void) -> JInt;
        let n = unsafe {
            jni::<Call>(self.env, CALL_STATIC_INT_METHOD_A)(
                self.env,
                class,
                method,
                std::ptr::null(),
            )
        };
        self.thrown("a static int call")?;
        Ok(n)
    }

    /// A static `String` method's answer; its local reference is freed (a game calls this every frame,
    /// with no Java frame around to free them).
    fn call_string(&self, class: JObject, method: JMethodId) -> Result<String, String> {
        type Call = unsafe extern "system" fn(Env, JObject, JMethodId, *const c_void) -> JObject;
        type Delete = unsafe extern "system" fn(Env, JObject);
        let s = unsafe {
            jni::<Call>(self.env, CALL_STATIC_OBJECT_METHOD_A)(
                self.env,
                class,
                method,
                std::ptr::null(),
            )
        };
        self.thrown("a static String call")?;
        let text = unsafe { java_string(self.env, s) };
        unsafe { jni::<Delete>(self.env, DELETE_LOCAL_REF)(self.env, s) };
        Ok(text)
    }
}

/// The "game's" tick counter, which Java reads through `kiln.Native.gameTicks()`.
static TICKS: AtomicI64 = AtomicI64::new(0);

unsafe extern "system" fn native_log(env: Env, _class: JObject, message: JObject) {
    let text = unsafe { java_string(env, message) };
    println!("[java] {text}");
}

unsafe extern "system" fn native_game_ticks(_env: Env, _class: JObject) -> JLong {
    TICKS.load(Ordering::Relaxed)
}

fn run(libjvm: &str, jar: &str) -> Result<(), String> {
    let started = Instant::now();
    let jvm = Jvm::start(
        libjvm,
        &[format!("-Djava.class.path={jar}"), "-Xmx128m".into()],
    )?;
    let boot = started.elapsed();

    let native = jvm.find_class(c"kiln/Native")?;
    jvm.register(
        native,
        &[
            (c"log", c"(Ljava/lang/String;)V", native_log as *mut c_void),
            (c"gameTicks", c"()J", native_game_ticks as *mut c_void),
        ],
    )?;
    let hello = jvm.find_class(c"kiln/hello/Hello")?;
    let init = jvm.static_method(hello, c"init", c"()V")?;
    let hud_text = jvm.static_method(hello, c"hudText", c"()Ljava/lang/String;")?;
    let npe_stress = jvm.static_method(hello, c"npeStress", c"()I")?;

    let t = Instant::now();
    jvm.call_void(hello, init)?;
    let init_took = t.elapsed();

    // As a game would: one call a frame, the tick moving on.
    const CALLS: i64 = 100_000;
    let t = Instant::now();
    let mut last = String::new();
    for frame in 0..CALLS {
        TICKS.store(frame / 3, Ordering::Relaxed);
        last = jvm.call_string(hello, hud_text)?;
    }
    let per_call = t.elapsed() / CALLS as u32;

    let t = Instant::now();
    let caught = jvm.call_int(hello, npe_stress)?;
    let npe_took = t.elapsed();

    println!("JVM start:      {boot:?}");
    println!("Hello.init:     {init_took:?}");
    println!("hudText:        {per_call:?} per call ({CALLS} calls), last: {last}");
    println!("npeStress:      {caught} caught in {npe_took:?}");
    Ok(())
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let [_, libjvm, jar] = args.as_slice() else {
        eprintln!("usage: kiln-spike-jvm-embed <path to libjvm> <hello.jar>");
        std::process::exit(2);
    };
    if let Err(e) = run(libjvm, jar) {
        eprintln!("spike failed: {e}");
        std::process::exit(1);
    }
}
