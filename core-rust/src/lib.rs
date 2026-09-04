use core::ffi::c_void;

const CORE_VERSION: i32 = 1;

/// Kotlin側へRustコアのバージョンを返す。
#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_essential_app_core_EssentialCore_nativeCoreVersion(
    _environment: *mut c_void,
    _class: *mut c_void,
) -> i32 {
    CORE_VERSION
}

/// 将来の重い処理へ接続できることを確認するため、Rustで決定的な値を生成する。
#[unsafe(no_mangle)]
pub extern "system" fn Java_jp_essential_app_core_EssentialCore_nativePulse(
    _environment: *mut c_void,
    _class: *mut c_void,
    seed: i64,
) -> i64 {
    mix_seed(seed)
}

fn mix_seed(seed: i64) -> i64 {
    let mut value = seed as u64;
    value ^= value << 13;
    value ^= value >> 7;
    value ^= value << 17;
    value as i64
}

#[cfg(test)]
mod tests {
    use super::mix_seed;

    #[test]
    fn 同じ入力から同じ値を生成する() {
        assert_eq!(mix_seed(42), mix_seed(42));
    }

    #[test]
    fn 入力値を変えると結果も変わる() {
        assert_ne!(mix_seed(42), mix_seed(43));
    }
}
