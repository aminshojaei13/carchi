#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-off catalogue importer for khodrobank.com (facts only).

Reads the public car sitemap, fetches each car page politely, and extracts a
small set of numeric / short factual specs. Page prose, user reviews, images
and logos are deliberately NOT stored.

Standard library only (Python 3.8+).

Usage:
    python3 tools/import_khodrobank.py --help
    python3 tools/import_khodrobank.py                 # full run, writes data/
    python3 tools/import_khodrobank.py --limit 20      # smoke test
"""

import argparse
import concurrent.futures
import gzip
import html as html_mod
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

SITEMAP_URL = "https://www.khodrobank.com/sitemap/cars_sitemap.xml.gz"
DEFAULT_UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)
ALLOWED_HOST = "www.khodrobank.com"

# Hard cap on parallelism so we never hammer the origin (spec: max 4).
MAX_WORKERS_HARD_LIMIT = 4
# Downloading ~636 pages of ~1.2 MB each: stay well below 1 req/s per worker.
MAX_RESPONSE_BYTES = 8 * 1024 * 1024

PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"
DIGIT_MAP = str.maketrans(PERSIAN_DIGITS + ARABIC_DIGITS, "0123456789" * 2)
ARABIC_TO_PERSIAN = str.maketrans({"ي": "ی", "ك": "ک", "ى": "ی"})

FUEL_VALUES = ("بنزین", "گازوئیل", "دوگانه سوز", "هیبرید", "برقی", "الکتریکی", "CNG")

_PLAIN_DIV = re.compile(r"<div>\s*([^<]+?)\s*</div>", re.S)
_TAG = re.compile(r"<[^>]+>")


# --------------------------------------------------------------------------- #
# small helpers
# --------------------------------------------------------------------------- #
def now_iso():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def log(msg):
    sys.stderr.write("[khodrobank] %s\n" % msg)
    sys.stderr.flush()


def strip_tags(fragment):
    """HTML fragment -> single-line plain text (short values only)."""
    if not fragment:
        return ""
    txt = _TAG.sub(" ", fragment)
    txt = html_mod.unescape(txt)
    txt = txt.replace("\u200c", " ").replace("\xa0", " ")
    return re.sub(r"\s+", " ", txt).strip()


def normalize_digits(text):
    return (text or "").translate(DIGIT_MAP)


def normalize_letters(text):
    """Unify Arabic kaf/yeh with Persian and drop ZWNJ for matching only."""
    return (text or "").translate(ARABIC_TO_PERSIAN).replace("\u200c", " ")


def clamp_text(value, limit):
    """Keep short categorical values short; never emit page prose."""
    if not value:
        return None
    value = strip_tags(value)
    if not value:
        return None
    value = re.sub(r"\s+", " ", value).strip(" -،,;")
    if not value:
        return None
    if len(value) > limit:
        value = value[:limit].rstrip()
    return value or None


def first_number(value):
    """Return the first number in a short string as float, Persian digits OK."""
    if not value:
        return None
    text = normalize_digits(strip_tags(value)).replace(",", "").replace("٬", "")
    match = re.search(r"\d+(?:[./]\d+)?", text)
    if not match:
        return None
    raw = match.group(0).replace("/", ".")
    try:
        return float(raw)
    except ValueError:
        return None


def slug(text):
    """Latin lowercase slug. Non-Latin input intentionally yields ''."""
    text = (text or "").strip().lower()
    text = re.sub(r"[^a-z0-9]+", "-", text)
    return text.strip("-")


def make_model_id(brand, name, site_id):
    """Stable id. Persian names cannot be transliterated honestly -> kb-{id}."""
    brand_slug, name_slug = slug(brand), slug(name)
    if brand_slug and name_slug:
        return "%s-%s-%s" % (brand_slug, name_slug, site_id)
    return "kb-%s" % site_id


# --------------------------------------------------------------------------- #
# networking
# --------------------------------------------------------------------------- #
def iri_to_uri(url):
    """Percent-encode Unicode path segments; keep existing escapes intact."""
    parts = urllib.parse.urlsplit(url)
    path = urllib.parse.quote(parts.path, safe="/%:@!$&'()*+,;=~-._")
    query = urllib.parse.quote(parts.query, safe="=&%+,;:@/?-._~")
    return urllib.parse.urlunsplit((parts.scheme, parts.netloc, path, query, parts.fragment))


def http_get(url, timeout, user_agent):
    request = urllib.request.Request(
        iri_to_uri(url),
        headers={
            "User-Agent": user_agent,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "fa-IR,fa;q=0.9,en;q=0.6",
            "Accept-Encoding": "gzip",
            "Connection": "close",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        data = response.read(MAX_RESPONSE_BYTES + 1)
    if len(data) > MAX_RESPONSE_BYTES:
        raise ValueError("response larger than %d bytes" % MAX_RESPONSE_BYTES)
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    return data


def http_get_with_retries(url, timeout, user_agent, attempts=3):
    last_error = None
    for attempt in range(1, attempts + 1):
        try:
            return http_get(url, timeout, user_agent)
        except urllib.error.HTTPError as exc:
            last_error = "HTTP %s" % exc.code
            # 4xx (except 429) will not improve on retry.
            if 400 <= exc.code < 500 and exc.code != 429:
                raise RuntimeError(last_error)
        except Exception as exc:  # noqa: BLE001 - network errors vary
            last_error = "%s: %s" % (type(exc).__name__, exc)
        if attempt < attempts:
            time.sleep(1.5 * attempt)
    raise RuntimeError(last_error or "unknown network error")


# --------------------------------------------------------------------------- #
# sitemap
# --------------------------------------------------------------------------- #
def load_sitemap_urls(sitemap_url, timeout, user_agent):
    raw = http_get_with_retries(sitemap_url, timeout, user_agent)
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    text = raw.decode("utf-8", "replace")
    locs = re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", text)
    urls, seen = [], set()
    for loc in locs:
        loc = html_mod.unescape(loc.strip())
        parsed = urllib.parse.urlsplit(loc)
        if parsed.netloc and parsed.netloc != ALLOWED_HOST:
            continue
        if "/cars/" not in parsed.path:
            continue
        if loc in seen:
            continue
        seen.add(loc)
        urls.append(loc)
    return urls


def parse_car_url(url):
    """https://www.khodrobank.com/cars/{brand}/{id}/{model} -> (brand, name, id)."""
    path = urllib.parse.urlsplit(url).path
    parts = [p for p in path.split("/") if p]
    if len(parts) < 4 or parts[0] != "cars":
        return None
    brand = urllib.parse.unquote(parts[1]).replace("-", " ").strip()
    site_id = urllib.parse.unquote(parts[2]).strip()
    model_slug = urllib.parse.unquote(parts[3]).replace("-", " ").strip()
    if not brand or not site_id:
        return None
    name = model_slug or brand
    prefix = brand + " "
    if name.startswith(prefix) and len(name) > len(prefix):
        name = name[len(prefix):].strip()
    if not name:
        name = model_slug or brand
    # Unify Arabic kaf/yeh with Persian forms (no transliteration, no guessing).
    brand = brand.translate(ARABIC_TO_PERSIAN)
    name = name.translate(ARABIC_TO_PERSIAN)
    return brand, re.sub(r"\s+", " ", name), site_id


# --------------------------------------------------------------------------- #
# page parsing (structured tiles only - no prose, no reviews)
# --------------------------------------------------------------------------- #
def parse_label_value_blocks(page, marker, value_first=False, chunk_size=900):
    """Parse the flat label/value tiles used by khodrobank pages."""
    result = {}
    for match in re.finditer(re.escape(marker), page):
        chunk = page[match.end(): match.end() + chunk_size]
        chunk = chunk.split(marker)[0]
        parts = [strip_tags(x) for x in _PLAIN_DIV.findall(chunk)]
        parts = [p for p in parts if p and not p.startswith("icon-")]
        if len(parts) < 2:
            continue
        if value_first:
            value, label = parts[0], parts[-1]
        else:
            label, value = parts[0], parts[-1]
        if not label or not value or label == value:
            continue
        result.setdefault(label, value)
    return result


def parse_json_ld(page):
    """Collect the schema.org objects embedded in the page.

    The page emits two concatenated JSON objects in one ld+json block, so a
    plain json.loads() fails; raw_decode lets us read them one by one.
    Only structured keys are read, never description/review text.
    """
    decoder = json.JSONDecoder()
    objects = []
    for block in re.findall(
        r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', page, re.S
    ):
        text = block.strip()
        index = 0
        while index < len(text):
            while index < len(text) and text[index] in " \t\r\n,":
                index += 1
            if index >= len(text):
                break
            try:
                obj, index = decoder.raw_decode(text, index)
            except ValueError:
                break
            if isinstance(obj, dict):
                objects.append(obj)
    return objects


def json_ld_car(objects):
    car = next((o for o in objects if o.get("@type") == "Car"), None)
    if not car:
        return None, None
    engine = car.get("vehicleEngine")
    if not isinstance(engine, dict):
        engine = next((o for o in objects if o.get("@type") == "EngineSpecification"), None)
    return car, engine


def quant_value(obj):
    """schema.org QuantitativeValue -> (value_string, unit_string)."""
    if not isinstance(obj, dict):
        return (obj if isinstance(obj, str) else None), ""
    value = obj.get("value")
    unit = obj.get("unitCode") or obj.get("unitText") or ""
    return (str(value) if value is not None else None), str(unit)


def parse_spec_table(page):
    """The <div class="kb-tcl-hoder"> rows: right = label, left = value."""
    result = {}
    for match in re.finditer(r'<div class="kb-tcl-hoder">', page):
        chunk = page[match.end(): match.end() + 2000]
        chunk = chunk.split('<div class="kb-tcl-hoder">')[0]
        right = re.search(r'<div class="kb-tcl-right"[^>]*>(.*?)</div>', chunk, re.S)
        left = re.search(r'<div class="kb-tcl-left"[^>]*>(.*?)</div>', chunk, re.S)
        if not right or not left:
            continue
        label = strip_tags(right.group(1))
        value = strip_tags(left.group(1))
        if label and value and label != value:
            result.setdefault(label, value)
    return result


def to_engine_cc(value):
    if not value:
        return None
    text = normalize_digits(strip_tags(value)).lower()
    number = first_number(text)
    if number is None:
        return None
    if "لیتر" in text and "سی سی" not in text and "cc" not in text and number < 20:
        number *= 1000
    number = int(round(number))
    # Sanity window for passenger-car displacement.
    if 500 <= number <= 8500:
        return number
    return None


def to_power_hp(value):
    if not value:
        return None
    text = normalize_digits(strip_tags(value)).lower()
    number = first_number(text)
    if number is None:
        return None
    if "کیلووات" in text or "kw" in re.sub(r"[^a-z]", "", text):
        number = number * 1.35962
    number = int(round(number))
    if 20 <= number <= 2000:
        return number
    return None


def to_torque_nm(value):
    if not value:
        return None
    text = normalize_digits(strip_tags(value)).lower()
    number = first_number(text)
    if number is None:
        return None
    if "کیلوگرم" in text and "نیوتن" not in text:
        number = number * 9.80665
    number = int(round(number))
    if 40 <= number <= 1500:
        return number
    return None


def to_weight_kg(value):
    if not value:
        return None
    text = normalize_digits(strip_tags(value)).lower()
    number = first_number(text)
    if number is None:
        return None
    number = int(round(number))
    if 400 <= number <= 4000:
        return number
    return None


def normalize_transmission(value):
    if not value:
        return None
    text = normalize_letters(strip_tags(value))
    low = text.lower()
    if "دوکلاچه" in low or "دو کلاچه" in low or "dct" in low or "dsg" in low or "pdk" in low:
        return "دوکلاچه"
    if "dht" in low:
        return "DHT"
    if any(k in low for k in ("cvt", "واریاتور", "متغیر", "تیپ ترونیک", "xtronic", "ecvt")):
        return "CVT"
    if any(
        k in low
        for k in (
            "تک سرعته",
            "تکسرعته",
            "تک دنده",
            "تک مرحله",
            "اتصال مستقیم",
            "1 سرعته",
            "یک سرعته",
        )
    ):
        return "تکسرعته"
    if any(k in low for k in ("خودکار", "اتومات", "automatic", "amt", "استپ ترونیک")) or re.search(
        r"(?<![a-z])at(?![a-z])", low
    ):
        return "اتوماتیک"
    if "دستی" in low or "manual" in low:
        return "دستی"
    return clamp_text(text, 30)


def normalize_fuel(value):
    if not value:
        return None
    text = normalize_letters(strip_tags(value))
    low = text.lower()
    if "هیبرید" in low:
        return "هیبرید"
    if "برقی" in low or "الکتریک" in low:
        return "برقی"
    if "دوگانه" in low or "cng" in low:
        return "دوگانه سوز"
    if "گازوئیل" in low or "دیزل" in low:
        return "گازوئیل"
    if "بنزین" in low:
        return "بنزین"
    return clamp_text(text, 30)


def normalize_body(value):
    if not value:
        return None
    text = normalize_letters(strip_tags(value))
    low = text.lower()
    for label, needles in (
        ("شاسی بلند", ("شاسی بلند", "suv")),
        ("کراس اوور", ("کراس اوور", "crossover")),
        ("هاچ بک", ("هاچ بک", "هاچبک", "hatch")),
        ("سدان", ("سدان", "sedan")),
        ("کوپه", ("کوپه", "coupe")),
        ("وانت", ("وانت", "pickup")),
        ("ون", ("ون ", "مینی ون", "van")),
    ):
        if any(n in low for n in needles):
            return label
    return clamp_text(text, 25)


def to_power_hp_unit(value, unit=""):
    """schema.org power: value string + unitCode (Horsepower / KWT)."""
    hp = to_power_hp(value)
    if hp is None:
        return None
    unit_low = re.sub(r"[^a-z]", "", (unit or "").lower())
    if unit_low in ("kwt", "kw", "kilowatt", "kilowatts"):
        hp = int(round(hp * 1.35962))
    return hp if 20 <= hp <= 2000 else None


def extract_specs(page):
    topspecs = parse_label_value_blocks(page, '<div class="car-single_topspecs_item">')
    title_specs = parse_label_value_blocks(
        page, '<div class="car-single_title_spec', value_first=True, chunk_size=700
    )
    table = parse_spec_table(page)
    car, engine = json_ld_car(parse_json_ld(page))

    ld_fuel = car.get("fuelType") if car else None
    ld_transmission = car.get("vehicleTransmission") if car else None
    ld_weight, ld_weight_unit = quant_value(car.get("weight")) if car else (None, "")
    ld_cc, _ = quant_value(engine.get("engineDisplacement")) if engine else (None, "")
    ld_power, ld_power_unit = quant_value(engine.get("enginePower")) if engine else (None, "")

    weight = to_weight_kg(table.get("وزن"))
    if weight is None and ld_weight:
        weight = to_weight_kg(ld_weight)

    specs = {
        "engine_cc": to_engine_cc(title_specs.get("حجم موتور") or table.get("حجم موتور"))
        or (to_engine_cc(ld_cc) if ld_cc else None),
        "power_hp": to_power_hp(topspecs.get("حداکثر توان") or table.get("حداکثر قدرت"))
        or (to_power_hp_unit(ld_power, ld_power_unit) if ld_power else None),
        "torque_nm": to_torque_nm(table.get("حداکثر گشتاور")),
        "transmission": normalize_transmission(
            topspecs.get("نوع جعبه دنده") or table.get("جعبه دنده") or ld_transmission
        ),
        "fuel": normalize_fuel(
            topspecs.get("نوع سوخت")
            or table.get("نوع سوخت")
            or topspecs.get("سوخت")
            or table.get("سوخت")
            or ld_fuel
        ),
        "body": normalize_body(topspecs.get("کلاس بدنه")),
        "weight_kg": weight,
    }
    return {k: v for k, v in specs.items() if v is not None}, {
        "top": len(topspecs),
        "title": len(title_specs),
        "table": len(table),
        "jsonld": bool(car),
    }


def parse_page(url, timeout, user_agent):
    parsed = parse_car_url(url)
    brand, site_id = None, None
    if parsed:
        brand, name, site_id = parsed
    raw = http_get_with_retries(url, timeout, user_agent)
    page = raw.decode("utf-8", "replace")
    if not parsed:
        raise ValueError("unexpected URL shape: %s" % url)
    specs, stats = extract_specs(page)
    if not specs and not (stats["top"] or stats["title"] or stats["table"] or stats["jsonld"]):
        raise RuntimeError("page has no structured spec blocks (soft error or template change)")
    record = {
        "id": make_model_id(brand, name, site_id),
        "brand": brand,
        "name": name,
        "chain": None,
        "source_url": url,
        "retrieved_at": now_iso(),
        "specs": specs,
    }
    return record, stats


# --------------------------------------------------------------------------- #
# validation / writing
# --------------------------------------------------------------------------- #
def validate_records(records):
    problems = []
    seen = set()
    spec_keys = {"engine_cc", "power_hp", "torque_nm", "transmission", "fuel", "body", "weight_kg"}
    for rec in records:
        if rec["id"] in seen:
            problems.append("duplicate id: %s" % rec["id"])
        seen.add(rec["id"])
        if not re.fullmatch(r"(?:kb-\d+|[a-z0-9]+(?:-[a-z0-9]+)*-\d+)", rec["id"]):
            problems.append("unexpected id: %s" % rec["id"])
        for field, limit in (("brand", 40), ("name", 90)):
            value = rec.get(field)
            if not isinstance(value, str) or not value:
                problems.append("%s: bad %s" % (rec["id"], field))
            elif len(value) > limit or "<" in value:
                problems.append("%s: %s too long or contains markup" % (rec["id"], field))
        if rec.get("chain") is not None:
            problems.append("%s: chain must stay null" % rec["id"])
        for key in rec.get("specs", {}):
            if key not in spec_keys:
                problems.append("%s: unexpected spec key %s" % (rec["id"], key))
        for key in ("transmission", "fuel", "body"):
            value = rec.get("specs", {}).get(key)
            if isinstance(value, str) and len(value) > 30:
                problems.append("%s: %s too long" % (rec["id"], key))
    return problems


def write_json(path, payload):
    directory = os.path.dirname(os.path.abspath(path))
    if directory:
        os.makedirs(directory, exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
        handle.write("\n")
    os.replace(tmp, path)


FIELD_LABELS = {
    "engine_cc": "engine_cc (حجم موتور)",
    "power_hp": "power_hp (قدرت موتور)",
    "torque_nm": "torque_nm (گشتاور)",
    "transmission": "transmission (گیربکس)",
    "fuel": "fuel (نوع سوخت)",
    "body": "body (کلاس بدنه)",
    "weight_kg": "weight_kg (وزن)",
}


def build_report(total_urls, attempted_urls, records, failures, elapsed, requested_fields):
    total_ok = len(records)
    attempted_ok = max(0, attempted_urls - len(failures))
    partial = attempted_urls != total_urls
    successful = [r for r in records]
    lines = []
    lines.append("# گزارش استخراج کاتالوگ خودروها (khodrobank.com)")
    lines.append("")
    lines.append("- تاریخ اجرا: %s" % now_iso())
    lines.append("- منبع: `%s`" % SITEMAP_URL)
    lines.append("- مدت اجرا: %.1f دقیقه" % (elapsed / 60.0))
    lines.append("")
    lines.append("## خلاصه")
    lines.append("")
    lines.append("| شاخص | مقدار |")
    lines.append("| --- | --- |")
    lines.append("| URL کل در sitemap | %d |" % total_urls)
    lines.append("| مدل در خروجی نهایی | %d |" % total_ok)
    if partial:
        lines.append("| تلاش در این اجرا | %d |" % attempted_urls)
        lines.append("| موفق در این اجرا | %d |" % attempted_ok)
    lines.append("| ناموفق | %d |" % len(failures))
    if attempted_urls:
        lines.append(
            "| درصد موفقیت این اجرا | %.1f%% |"
            % (100.0 * attempted_ok / attempted_urls)
        )
    lines.append(
        "| پوشش کاتالوگ | %.1f%% |"
        % (100.0 * total_ok / total_urls if total_urls else 0.0)
    )
    lines.append("| مدل با حداقل یک مشخصه | %d |" % sum(1 for r in successful if r["specs"]))
    lines.append("| مدل بدون هیچ مشخصه | %d |" % sum(1 for r in successful if not r["specs"]))
    lines.append("")
    lines.append("## درصد استخراج هر فیلد")
    lines.append("")
    lines.append("درصد نسبت به مدل‌های موفق محاسبه شده است.")
    lines.append("")
    lines.append("| فیلد | تعداد | درصد |")
    lines.append("| --- | ---: | ---: |")
    for key in requested_fields:
        count = sum(1 for r in successful if r["specs"].get(key) is not None)
        pct = (100.0 * count / total_ok) if total_ok else 0.0
        lines.append("| %s | %d | %.1f%% |" % (FIELD_LABELS.get(key, key), count, pct))
    lines.append("| chain | %d | %.1f%% |" % (0, 0.0))
    lines.append("")
    lines.append("> `chain` (تسمه تایم) عمداً خالی مانده است: این داده در ساختار صفحات سایت وجود ندارد.")
    lines.append("")

    # distributions
    for key, title in (
        ("transmission", "توزیع گیربکس"),
        ("body", "توزیع کلاس بدنه"),
        ("fuel", "توزیع نوع سوخت"),
    ):
        counts = {}
        for r in successful:
            value = r["specs"].get(key)
            if value:
                counts[value] = counts.get(value, 0) + 1
        if not counts:
            continue
        lines.append("## %s" % title)
        lines.append("")
        lines.append("| مقدار | تعداد |")
        lines.append("| --- | ---: |")
        for value, count in sorted(counts.items(), key=lambda kv: -kv[1])[:10]:
            lines.append("| %s | %d |" % (value, count))
        lines.append("")

    lines.append("## ۱۰ نمونه")
    lines.append("")
    lines.append("| id | برند | مدل | cc | hp | Nm | گیربکس | سوخت | بدنه | وزن |")
    lines.append("| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | ---: |")
    for rec in successful[:10]:
        s = rec["specs"]
        lines.append(
            "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |"
            % (
                rec["id"],
                rec["brand"],
                rec["name"],
                s.get("engine_cc", "—"),
                s.get("power_hp", "—"),
                s.get("torque_nm", "—"),
                s.get("transmission", "—"),
                s.get("fuel", "—"),
                s.get("body", "—"),
                s.get("weight_kg", "—"),
            )
        )
    lines.append("")

    if failures:
        lines.append("## خطاها (حداکثر ۲۵ مورد)")
        lines.append("")
        lines.append("| URL | خطا |")
        lines.append("| --- | --- |")
        for url, error in failures[:25]:
            lines.append("| `%s` | %s |" % (url, str(error).replace("|", "/")[:160]))
        if len(failures) > 25:
            lines.append("")
            lines.append("… و %d خطای دیگر." % (len(failures) - 25))
        lines.append("")

    lines.append("## محدودیت‌ها")
    lines.append("")
    lines.append("- **بازه تعویض تسمه تایم در سایت نیست.** فیلد `chain` همیشه `null` است و هیچ حدسی زده نشده.")
    lines.append("- PDF دفترچه راهنما در صفحات لینک نشده است؛ بنابراین برنامه سرویس دوره‌ای از این منبع درنمی‌آید.")
    lines.append("- فقط فیلدهای ساختاریافته (کاشی‌های مشخصات و جدول فنی) خوانده می‌شوند؛ متن مقاله، مزایا/معایب و نظرات کاربران خوانده و ذخیره نمی‌شود.")
    lines.append("- «نوع سوخت» در اکثر صفحات برچسب ساختاریافته ندارد؛ پوشش پایین آن واقعی است، نه خطای پارسر.")
    lines.append("- برخی مدل‌ها فقط نسخه/تیپ خاص هستند (مثلاً «اتوماتیک») و سایت برای همه تیپ‌ها صفحه جدا ندارد.")
    lines.append("- داده‌ها اسنپ‌شات یک‌باره‌اند؛ مدل‌های جدید تا اجرای بعدی اضافه نمی‌شوند.")
    lines.append("")
    return "\n".join(lines)


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Extract a facts-only car catalogue from khodrobank.com into data/models.json",
    )
    parser.add_argument("--sitemap-url", default=SITEMAP_URL, help="car sitemap URL")
    parser.add_argument("--out", default="data/models.json", help="output JSON path")
    parser.add_argument("--report", default="data/import-report.md", help="output report path")
    parser.add_argument(
        "--workers",
        type=int,
        default=4,
        help="parallel fetchers (hard-capped at %d)" % MAX_WORKERS_HARD_LIMIT,
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=0.4,
        help="politeness delay before each request, seconds (default 0.4)",
    )
    parser.add_argument("--limit", type=int, default=0, help="only process first N URLs (0 = all)")
    parser.add_argument(
        "--resume",
        action="store_true",
        help="merge into an existing --out: only fetch URLs missing from it (or with empty specs)",
    )
    parser.add_argument("--timeout", type=float, default=45.0, help="per-request timeout, seconds")
    parser.add_argument("--user-agent", default=DEFAULT_UA, help="HTTP User-Agent")
    parser.add_argument("--quiet", action="store_true", help="less progress logging")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    started = time.time()

    workers = max(1, min(args.workers, MAX_WORKERS_HARD_LIMIT))
    if args.workers > MAX_WORKERS_HARD_LIMIT:
        log("workers capped at %d" % MAX_WORKERS_HARD_LIMIT)

    log("reading sitemap %s" % args.sitemap_url)
    try:
        urls = load_sitemap_urls(args.sitemap_url, args.timeout, args.user_agent)
    except Exception as exc:  # noqa: BLE001
        log("FATAL: cannot read sitemap: %s" % exc)
        return 2

    if not urls:
        log("FATAL: sitemap yielded 0 car URLs")
        return 2

    total_urls = len(urls)
    existing_records = []
    if args.resume:
        if os.path.exists(args.out):
            try:
                with open(args.out, encoding="utf-8") as handle:
                    existing_records = json.load(handle).get("models", [])
            except Exception as exc:  # noqa: BLE001
                log("WARN: --resume could not read %s (%s); starting fresh" % (args.out, exc))
                existing_records = []
        else:
            log("WARN: --resume but %s does not exist; starting fresh" % args.out)
        known = {r.get("source_url"): r for r in existing_records}
        urls = [u for u in urls if not known.get(u) or not known[u].get("specs")]
        log("resume: %d existing models, %d URLs need fetching" % (len(existing_records), len(urls)))

    if args.limit and args.limit > 0:
        urls = urls[: args.limit]
    log("found %d car URLs, processing %d with %d workers" % (total_urls, len(urls), workers))

    records, failures = [], []
    done = 0

    def work(url):
        # Stagger workers a little; politeness delay 0.3-0.5s per spec.
        # One extra attempt covers soft-200 error pages (empty templates).
        last_error = None
        for attempt in (1, 2):
            time.sleep(max(0.3, min(0.5, args.delay)) + random.uniform(0.0, 0.15))
            try:
                return parse_page(url, args.timeout, args.user_agent)
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                if attempt == 1:
                    time.sleep(2.0)
        raise last_error

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        future_map = {pool.submit(work, url): url for url in urls}
        for future in concurrent.futures.as_completed(future_map):
            url = future_map[future]
            done += 1
            try:
                record, stats = future.result()
            except Exception as exc:  # noqa: BLE001
                failures.append((url, str(exc)))
                if not args.quiet:
                    log("FAIL %s (%s)" % (url, exc))
            else:
                records.append(record)
                if not args.quiet and (done % 25 == 0 or done == len(urls)):
                    log("progress %d/%d ok=%d fail=%d" % (done, len(urls), len(records), len(failures)))

    attempted = len(urls)
    merged = {r["id"]: r for r in existing_records}
    for record in records:
        merged[record["id"]] = record
    records = sorted(merged.values(), key=lambda r: r["id"])
    problems = validate_records(records)
    if problems:
        log("VALIDATION PROBLEMS (%d):" % len(problems))
        for problem in problems[:20]:
            log("  - %s" % problem)

    elapsed = time.time() - started
    payload = {
        "generated_at": now_iso(),
        "source": "khodrobank.com",
        "license_note": "facts only; no text/images/reviews",
        "models": records,
    }
    write_json(args.out, payload)

    fields = ["engine_cc", "power_hp", "torque_nm", "transmission", "fuel", "body", "weight_kg"]
    report = build_report(total_urls, attempted, records, failures, elapsed, fields)
    os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
    with open(args.report, "w", encoding="utf-8") as handle:
        handle.write(report)
        handle.write("\n")

    ok = len(records)
    attempted_ok = max(0, attempted - len(failures))
    log(
        "done: %d/%d ok this run (%.1f%%), %d failed, catalogue=%d in %.1fs"
        % (
            attempted_ok,
            attempted,
            100.0 * attempted_ok / attempted if attempted else 0.0,
            len(failures),
            ok,
            elapsed,
        )
    )
    log("wrote %s and %s" % (args.out, args.report))
    return 0 if ok >= min(200, len(urls)) else 1


if __name__ == "__main__":
    sys.exit(main())
