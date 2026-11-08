#!/usr/bin/env python3
"""
Read-only dump of the modem, for building the app.
Run in Termux while the phone is on the modem's Wi-Fi:
    termux-setup-storage        (once)
    python modem_dump.py
It only logs in and READS (GET goform_get_cmd_process). It never changes a setting.
Passwords and keys are replaced with *** before saving.
Output: /sdcard/Download/MODEM_DUMP_ALL.txt
"""
import base64, getpass, json, os, re, sys, time

try:
    import requests
except ImportError:
    sys.exit("first run:  pip install requests")

HOST = input("modem address [192.168.0.1]: ").strip() or "192.168.0.1"
PASS = getpass.getpass("modem admin password: ")
B = "http://" + HOST
OUT = "/sdcard/Download/MODEM_DUMP_ALL.txt"
if not os.path.isdir("/sdcard/Download"):
    OUT = os.path.expanduser("~/MODEM_DUMP_ALL.txt")

s = requests.Session()
s.headers.update({"Referer": B + "/index.html", "X-Requested-With": "XMLHttpRequest"})
out = open(OUT, "w", encoding="utf-8")


def write(title, body):
    out.write("#" * 70 + "\n### " + title + "\n" + "#" * 70 + "\n" + body + "\n\n")
    out.flush()


# Values that must never leave the phone.
SECRET = re.compile(r"(pass|psk|wpapsk|key\d?str|_key$|secret|pin$|puk$|passtmp|enaes|encode$)", re.I)


def mask(obj):
    if isinstance(obj, dict):
        return {k: ("***" if SECRET.search(k) and isinstance(v, str) and v else mask(v)) for k, v in obj.items()}
    if isinstance(obj, list):
        return [mask(x) for x in obj]
    return obj


def get_file(path):
    try:
        r = s.get(B + "/" + path, timeout=15)
        if r.status_code == 200 and r.content:
            write("URL: " + B + "/" + path, r.content.decode("utf-8", "replace"))
            return r.text
        write("URL: " + B + "/" + path, "(HTTP %d)" % r.status_code)
    except Exception as e:
        write("URL: " + B + "/" + path, "(error %s)" % e)
    return ""


def get_cmd(cmd, multi, extra=None):
    p = {"isTest": "false", "cmd": cmd, "_": str(int(time.time() * 1000))}
    if multi:
        p["multi_data"] = "1"
    if extra:
        p.update(extra)
    try:
        r = s.get(B + "/goform/goform_get_cmd_process", params=p, timeout=15)
        try:
            return json.dumps(mask(r.json()), ensure_ascii=False, indent=1)
        except Exception:
            return r.text[:4000]
    except Exception as e:
        return "(error %s)" % e


# 1) log in (Base64 like the modem page; this modem has PASSWORD_ENCODE true)
r = s.post(B + "/goform/goform_set_cmd_process",
           data={"isTest": "false", "goformId": "LOGIN", "password": base64.b64encode(PASS.encode()).decode()}, timeout=15)
print("login:", r.text)
write("LOGIN RESULT", r.text)

# 2) every file of the web page
files = ["index.html", "js/main.js", "js/com.js", "js/set.js", "js/lib.js", "js/util.js", "js/sim_device.js", "js/net.js",
         "js/wifi.js", "js/ext/set.js", "js/ext/menu.js", "js/ext/menu_pppoe.js", "css/com.css", "css/csn.css",
         "i18n/Messages.properties", "i18n/Messages_en.properties", "i18n/Messages_fa.properties"]
texts = {}
for f in files:
    texts[f] = get_file(f)

pages = set(re.findall(r'path:"([a-z0-9_]+)"', texts.get("js/ext/menu.js", "") + texts.get("js/ext/menu_pppoe.js", "")))
pages |= {"sd", "sd_httpshare", "wifi_basic", "network_dial_set_cpe", "opmode_popup", "entry", "main", "locknet", "sim_abnormal"}
for p in sorted(pages):
    get_file("subpg/%s.html" % p)

# 3) every value the page reads: all cmd names in the scripts
allcode = "\n".join(texts.values())
names = set()
for m in re.finditer(r'cmd\s*[:=]\s*"([^"]+)"', allcode):
    names.update(x.strip() for x in m.group(1).split(",") if x.strip())
for m in re.finditer(r'nv\s*:\s*\[([^\]]+)\]', allcode):
    names.update(re.findall(r'"([A-Za-z0-9_]+)"', m.group(1)))
for m in re.finditer(r'var\s+\w+\s*=\s*\[((?:"[A-Za-z0-9_]+",?\s*){3,})\]', allcode):
    names.update(re.findall(r'"([A-Za-z0-9_]+)"', m.group(1)))
names = sorted(n for n in names if re.fullmatch(r"[A-Za-z0-9_]+", n))

# Reads that need extra parameters (same ones the page sends).
special = {
    "sms_data_total": {"page": "0", "data_per_page": "20", "mem_store": "1", "tags": "10", "order_by": "order by id desc"},
    "sms_status_rpt_data": {"page": "0", "data_per_page": "10"},
    "sms_cmd_status_info": {"sms_cmd": "1"},
    "pbm_data_total": {"mem_store": "2", "page": "0", "data_per_page": "20", "orderBy": "name", "isAsc": "true"},
    "pbm_data_info": {"mem_store": "1", "page": "0", "data_per_page": "20", "orderBy": "name", "isAsc": "true"},
}
singles = []
for n in names:
    if n in special:
        write("CMD: " + n, get_cmd(n, False, special[n]))
    else:
        singles.append(n)

# One at a time, so a name the modem does not know cannot hide the others.
lines = []
for i, n in enumerate(singles):
    lines.append(n + " = " + get_cmd(n, False))
    if i % 25 == 0:
        print("reading %d / %d" % (i, len(singles)))
write("CMD: all single values (%d)" % len(singles), "\n".join(lines))
write("CMD: pbm_capacity_info sim", get_cmd("pbm_capacity_info", False, {"pbm_location": "pbm_sim"}))
write("CMD: pbm_capacity_info native", get_cmd("pbm_capacity_info", False, {"pbm_location": "pbm_native"}))

out.close()
print("\ndone:", OUT)
