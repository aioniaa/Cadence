use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use serde::Deserialize;
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet};

#[derive(Clone, Deserialize)]
struct ShiftTime {
    start: String,
    end: String,
}

#[derive(Clone, Deserialize)]
struct ShiftType {
    id: String,
    name: String,
    #[serde(default)]
    start: String,
    #[serde(default)]
    end: String,
    #[serde(default)]
    rest: bool,
}

#[derive(Clone, Deserialize)]
struct Segment {
    shift: String,
    days: i32,
}

#[derive(Clone, Deserialize)]
struct Rule {
    segments: Vec<Segment>,
    #[serde(rename = "anchorKey")]
    anchor_key: String,
    #[serde(rename = "anchorIndex")]
    anchor_index: i32,
}

#[derive(Deserialize, Default)]
struct State {
    #[serde(default)]
    shifts: HashMap<String, String>,
    #[serde(default)]
    rule: Option<Rule>,
    #[serde(rename = "shiftTimes", default)]
    shift_times: HashMap<String, ShiftTime>,
    #[serde(rename = "shiftTypes", default)]
    shift_types: Vec<ShiftType>,
    #[serde(default)]
    leaves: HashSet<String>,
    #[serde(rename = "autoEnabled", default = "enabled_default")]
    auto_enabled: bool,
}

fn enabled_default() -> bool {
    true
}

const LEGACY_SHIFT_IDS: [&str; 4] = ["morning", "middle", "night", "rest"];

fn default_time(id: &str) -> (i32, i32) {
    match id {
        "morning" => (8 * 60, 16 * 60),
        "middle" => (12 * 60, 20 * 60),
        "night" => (20 * 60, 8 * 60),
        _ => (0, 0),
    }
}

fn legacy_name(id: &str) -> &'static str {
    match id {
        "morning" => "白班",
        "middle" => "中班",
        "night" => "夜班",
        _ => "休息",
    }
}

fn shift_type<'a>(state: &'a State, id: &str) -> Option<&'a ShiftType> {
    state.shift_types.iter().find(|s| s.id == id)
}

fn shift_exists(state: &State, id: &str) -> bool {
    shift_type(state, id).is_some()
        || (state.shift_types.is_empty() && LEGACY_SHIFT_IDS.contains(&id))
}

fn is_rest(state: &State, id: &str) -> bool {
    shift_type(state, id).map(|s| s.rest).unwrap_or(id == "rest")
}

fn shift_name(state: &State, id: &str) -> String {
    shift_type(state, id)
        .map(|s| s.name.clone())
        .unwrap_or_else(|| legacy_name(id).to_string())
}

fn parse_clock(value: &str) -> Option<i32> {
    let mut parts = value.split(':');
    let hour: i32 = parts.next()?.parse().ok()?;
    let minute: i32 = parts.next()?.parse().ok()?;
    if !(0..24).contains(&hour) || !(0..60).contains(&minute) {
        return None;
    }
    Some(hour * 60 + minute)
}

fn time_for(state: &State, id: &str) -> (i32, i32) {
    if is_rest(state, id) {
        return (0, 0);
    }
    if let Some(def) = shift_type(state, id) {
        if let (Some(start), Some(end)) = (parse_clock(&def.start), parse_clock(&def.end)) {
            return (start, end);
        }
    }
    let fallback = default_time(id);
    match state.shift_times.get(id) {
        Some(t) => (
            parse_clock(&t.start).unwrap_or(fallback.0),
            parse_clock(&t.end).unwrap_or(fallback.1),
        ),
        None => fallback,
    }
}

fn days_from_civil(y: i32, m: i32, d: i32) -> i64 {
    let y = if m <= 2 { y - 1 } else { y } as i64;
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = y - era * 400;
    let mp = ((m + 9) % 12) as i64;
    let doy = (153 * mp + 2) / 5 + d as i64 - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146097 + doe - 719468
}

fn civil_from_days(z: i64) -> (i32, i32, i32) {
    let z = z + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = z - era * 146097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    ((y + if m <= 2 { 1 } else { 0 }) as i32, m as i32, d as i32)
}

fn key_from_days(days: i64) -> String {
    let (y, m, d) = civil_from_days(days);
    format!("{:04}-{:02}-{:02}", y, m, d)
}

fn parse_key(key: &str) -> Option<i64> {
    let mut parts = key.split('-');
    let y: i32 = parts.next()?.parse().ok()?;
    let m: i32 = parts.next()?.parse().ok()?;
    let d: i32 = parts.next()?.parse().ok()?;
    if !(1..=12).contains(&m) || !(1..=31).contains(&d) {
        return None;
    }
    Some(days_from_civil(y, m, d))
}

fn add_days(key: &str, days: i64) -> String {
    parse_key(key)
        .map(|d| key_from_days(d + days))
        .unwrap_or_else(|| key.to_string())
}

fn days_between(from: &str, to: &str) -> i64 {
    match (parse_key(from), parse_key(to)) {
        (Some(a), Some(b)) => b - a,
        _ => 0,
    }
}

fn leave_days_before(anchor: &str, key: &str, leaves: &HashSet<String>) -> i64 {
    let (Some(start), Some(end)) = (parse_key(anchor), parse_key(key)) else {
        return 0;
    };
    if end <= start {
        return 0;
    }
    let mut day = start;
    let mut count = 0;
    while day < end {
        if leaves.contains(&key_from_days(day)) {
            count += 1;
        }
        day += 1;
    }
    count
}

fn auto_shift(rule: &Rule, key: &str, leaves: &HashSet<String>) -> Option<String> {
    if key < rule.anchor_key.as_str() {
        return None;
    }
    let raw_diff = days_between(&rule.anchor_key, key);
    if raw_diff < 0 {
        return None;
    }
    let diff = (raw_diff - leave_days_before(&rule.anchor_key, key, leaves)).max(0);
    let total: i32 = rule.segments.iter().map(|s| s.days.max(0)).sum();
    if total <= 0 {
        return None;
    }
    let mut index = (rule.anchor_index as i64 + diff).rem_euclid(total as i64) as i32;
    for segment in &rule.segments {
        if segment.days <= 0 {
            continue;
        }
        if index < segment.days {
            return Some(segment.shift.clone());
        }
        index -= segment.days;
    }
    None
}

fn effective_shift(state: &State, key: &str) -> Option<(String, &'static str)> {
    if state.leaves.contains(key) {
        return Some(("leave".to_string(), "leave"));
    }
    if let Some(id) = state.shifts.get(key) {
        if shift_exists(state, id) {
            return Some((id.clone(), "manual"));
        }
    }
    if !state.auto_enabled {
        return None;
    }
    state
        .rule
        .as_ref()
        .and_then(|r| auto_shift(r, key, &state.leaves))
        .map(|id| (id, "rule"))
}

fn in_month(key: &str, year: i32, month: i32) -> bool {
    key.starts_with(&format!("{:04}-{:02}-", year, month))
}

fn project_month_value(state: &State, year: i32, month: i32) -> Value {
    let first_days = days_from_civil(year, month, 1);
    // Monday = 0. 1970-01-01 was a Thursday, so add 3.
    let weekday = (first_days + 3).rem_euclid(7) as i64;
    let start = first_days - weekday;
    let mut days = Vec::with_capacity(42);
    for offset in 0..42 {
        let key = key_from_days(start + offset);
        let shift = effective_shift(state, &key);
        let is_leave = state.leaves.contains(&key);
        days.push(json!({
            "key": key,
            "day": civil_from_days(start + offset).2,
            "inMonth": in_month(&key, year, month),
            "shift": if is_leave { None } else { shift.as_ref().map(|s| s.0.clone()) },
            "source": shift.as_ref().map(|s| s.1),
            "leave": is_leave,
        }));
    }
    json!({
        "year": year,
        "month": month,
        "days": days,
    })
}

fn project_days_value(state: &State, start_key: &str, count: i32) -> Value {
    let mut days = Vec::with_capacity(count.max(0) as usize);
    for offset in 0..count.max(0) {
        let key = add_days(start_key, offset as i64);
        let shift = effective_shift(state, &key);
        let is_leave = state.leaves.contains(&key);
        days.push(json!({
            "key": key,
            "shift": if is_leave { None } else { shift.as_ref().map(|s| s.0.clone()) },
            "source": shift.as_ref().map(|s| s.1),
            "leave": is_leave,
        }));
    }
    Value::Array(days)
}

fn next_shift_start(state: &State, today_key: &str, now_seconds: i32) -> Option<(String, i32, String)> {
    for offset in 0..15 {
        let key = add_days(today_key, offset);
        let Some((id, _)) = effective_shift(state, &key) else {
            continue;
        };
        if id == "leave" || is_rest(state, &id) {
            continue;
        }
        let (start, end) = time_for(state, &id);
        let start_abs = offset * 86400 + (start * 60) as i64;
        let now_abs = now_seconds as i64;
        if start_abs > now_abs {
            let meta = format!(
                "{} · {:02}:{:02}–{:02}:{:02}",
                shift_name(state, &id),
                start / 60,
                start % 60,
                end / 60,
                end % 60,
            );
            return Some((id, (start_abs - now_abs) as i32, meta));
        }
    }
    None
}

fn countdown_value(state: &State, today_key: &str, now_seconds: i32) -> Value {
    let now = now_seconds.clamp(0, 86399);
    let yesterday = add_days(today_key, -1);

    if let Some((id, source)) = effective_shift(state, &yesterday) {
        if !is_rest(state, &id) {
            let (start, end) = time_for(state, &id);
            let end_seconds = end * 60;
            if end <= start && now < end_seconds {
                let total = 86400 - start * 60 + end_seconds;
                let remaining = end_seconds - now;
                return json!({
                    "kind": "active",
                    "shift": id,
                    "source": source,
                    "label": "距离下班",
                    "seconds": remaining,
                    "progress": remaining as f64 / total.max(1) as f64,
                    "meta": format!("{} · {:02}:{:02}–{:02}:{:02}", shift_name(state, &id), start / 60, start % 60, end / 60, end % 60),
                });
            }
        }
    }

    let Some((id, source)) = effective_shift(state, today_key) else {
        return json!({"kind": "none", "label": "今天还没有排班", "seconds": 0, "progress": 0.0, "meta": "去排班"});
    };
    if id == "leave" {
        return json!({
            "kind": "leave",
            "source": "leave",
            "label": "今天休假",
            "seconds": 0,
            "progress": 0.0,
            "meta": "轮班暂停",
        });
    }
    if is_rest(state, &id) {
        if let Some((_next_id, seconds, meta)) = next_shift_start(state, today_key, now) {
            return json!({
                "kind": "rest",
                "shift": id,
                "source": source,
                "label": "距离下次上班",
                "seconds": seconds,
                "progress": 0.0,
                "meta": meta,
            });
        }
        return json!({"kind": "rest", "shift": id, "source": source, "label": "今天休息", "seconds": 0, "progress": 0.0, "meta": "全天"});
    }
    let (start_min, end_min) = time_for(state, &id);
    let start = start_min * 60;
    let end = end_min * 60;
    let real_end = if end <= start { end + 86400 } else { end };
    let meta = format!("{} · {:02}:{:02}–{:02}:{:02}", shift_name(state, &id), start_min / 60, start_min % 60, end_min / 60, end_min % 60);

    if now < start {
        return json!({
            "kind": "before",
            "shift": id,
            "source": source,
            "label": "距离上班",
            "seconds": start - now,
            "progress": 0.0,
            "meta": meta,
        });
    }
    if now < real_end {
        let total = real_end - start;
        let remaining = real_end - now;
        return json!({
            "kind": "active",
            "shift": id,
            "source": source,
            "label": "距离下班",
            "seconds": remaining,
            "progress": remaining as f64 / total.max(1) as f64,
            "meta": meta,
        });
    }
    json!({
        "kind": "after",
        "shift": id,
        "source": source,
        "label": "今日已下班",
        "seconds": 0,
        "progress": 0.0,
        "meta": meta,
    })
}

fn parse_state(raw: &str) -> State {
    serde_json::from_str(raw).unwrap_or_default()
}

fn read_jstring(env: &mut JNIEnv, value: JString) -> String {
    env.get_string(&value)
        .map(|s| s.into())
        .unwrap_or_else(|_| "{}".to_string())
}

fn to_jstring(env: &mut JNIEnv, value: String) -> jstring {
    match env.new_string(value) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_banbiao_app_core_NativeCore_projectMonth(
    mut env: JNIEnv,
    _class: JClass,
    state_json: JString,
    year: jint,
    month: jint,
) -> jstring {
    let state = parse_state(&read_jstring(&mut env, state_json));
    to_jstring(&mut env, project_month_value(&state, year, month).to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_banbiao_app_core_NativeCore_projectDays(
    mut env: JNIEnv,
    _class: JClass,
    state_json: JString,
    start_key: JString,
    count: jint,
) -> jstring {
    let state = parse_state(&read_jstring(&mut env, state_json));
    let start = read_jstring(&mut env, start_key);
    to_jstring(&mut env, project_days_value(&state, &start, count).to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_banbiao_app_core_NativeCore_countdown(
    mut env: JNIEnv,
    _class: JClass,
    state_json: JString,
    today_key: JString,
    now_minutes: jint,
) -> jstring {
    let state = parse_state(&read_jstring(&mut env, state_json));
    let today = read_jstring(&mut env, today_key);
    to_jstring(&mut env, countdown_value(&state, &today, now_minutes).to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn overnight_countdown_is_not_midnight() {
        let state = parse_state(r#"{"shiftTimes":{"night":{"start":"20:00","end":"08:00"}},"shifts":{"2026-11-03":"night"}}"#);
        let value = countdown_value(&state, "2026-11-03", 23 * 3600 + 2 * 60);
        assert_eq!(value["kind"], "active");
        assert_eq!(value["seconds"], 8 * 3600 + 58 * 60);
    }

    #[test]
    fn custom_shift_time_and_name() {
        let state = parse_state(r#"{"shiftTypes":[{"id":"early","name":"早班","start":"06:30","end":"14:30","rest":false},{"id":"off","name":"休息","rest":true}],"shifts":{"2026-11-03":"early"}}"#);
        let value = countdown_value(&state, "2026-11-03", 7 * 3600);
        assert_eq!(value["kind"], "active");
        assert_eq!(value["shift"], "early");
        assert!(value["meta"].as_str().unwrap().contains("早班"));
        assert_eq!(value["seconds"], 7 * 3600 + 30 * 60);
    }

    #[test]
    fn leave_days_pause_rotation_and_resume() {
        let state = parse_state(r#"{"rule":{"segments":[{"shift":"morning","days":4},{"shift":"rest","days":4}],"anchorKey":"2026-11-01","anchorIndex":0},"shiftTypes":[{"id":"morning","name":"白班","start":"08:00","end":"16:00"},{"id":"rest","name":"休息","rest":true}],"leaves":["2026-11-02","2026-11-03"]}"#);
        let d1 = effective_shift(&state, "2026-11-02").unwrap();
        assert_eq!(d1.0, "leave");
        let d5 = effective_shift(&state, "2026-11-05").unwrap();
        assert_eq!(d5.0, "morning");
        let leave = countdown_value(&state, "2026-11-02", 9 * 3600);
        assert_eq!(leave["kind"], "leave");
    }

    #[test]
    fn auto_rotation_can_be_paused() {
        let state = parse_state(r#"{"autoEnabled":false,"rule":{"segments":[{"shift":"morning","days":1}],"anchorKey":"2026-11-01","anchorIndex":0},"shiftTypes":[{"id":"morning","name":"白班","start":"08:00","end":"16:00"}]}"#);
        assert!(effective_shift(&state, "2026-11-03").is_none());
    }

    #[test]
    fn rest_counts_down_to_next_shift() {
        let state = parse_state(r#"{"rule":{"segments":[{"shift":"rest","days":1},{"shift":"morning","days":1}],"anchorKey":"2026-11-01","anchorIndex":0},"shiftTypes":[{"id":"morning","name":"白班","start":"08:00","end":"16:00"},{"id":"rest","name":"休息","rest":true}]}"#);
        let value = countdown_value(&state, "2026-11-01", 20 * 3600);
        assert_eq!(value["kind"], "rest");
        assert_eq!(value["label"], "距离下次上班");
        assert_eq!(value["seconds"], 12 * 3600);
    }

    #[test]
    fn month_starts_monday_and_has_42_days() {
        let state = State::default();
        let value = project_month_value(&state, 2026, 11);
        assert_eq!(value["days"].as_array().unwrap().len(), 42);
        assert_eq!(value["days"][0]["key"], "2026-10-26");
    }
}
