#!/usr/bin/env python3
"""
Read-only look at how the modem could be copied / updated. FINDS OUT, changes NOTHING.

Run in Termux while the phone is on the modem's Wi-Fi:
    pip install requests        (once)
    python modem_brain_probe.py

It only logs in and READS. It never writes a setting, never turns on debug/telnet/ADB,
never starts an update, never reboots. Every request is a GET except the login itself.
It tells you, for each way of getting the "brain" (the firmware) out:
  - whether this modem looks like it offers it, and
  - what the next (manual, your-own-risk) step would be.
Nothing it prints can brick the modem. Acting on what it finds is a separate decision.

Output: /sdcard/Download/MODEM_BRAIN.txt  (passwords/keys masked as ***)
"""
import base64, getpass, json, os, re, sys, time

try:
    import requests
except ImportError:
    sys.exit("first run:  pip install requests")

HOST = input("modem address [192.168.0.1]: ").strip() or "192.168.0.1"
PASS = getpass.getpass("modem admin password: ")
B = "http://" + HOST
OUT = "/sdcard/Download/MODEM_BRAIN.txt"
if not os.path.isdir("/sdcard/Download"):
    OUT = os.path.expanduser("~/MODEM_BRAIN.txt")

s = requests.Session()
s.headers.update({"Referer": B + "/index.html", "X-Requested-With": "XMLHttpRequest"})
out = open(OUT, "w", encoding="utf-8")
SECRET = re.compile(r"(pass|psk|wpapsk|key\d?str|_key$|secret|pin$|puk$|passtmp|enaes|encode$)", re.I)


def log(line=""):
    print(line)
    out.write(line + "\n")
    out.flush()


def head(title):
    log("\n" + "=" * 64 + "\n" + title + "\n" + "=" * 64)


def mask(obj):
    if isinstance(obj, dict):
        return {k: ("***" if SECRET.search(k) and isinstance(v, str) and v else mask(v)) for k, v in obj.items()}
    if isinstance(obj, list):
        return [mask(x) for x in obj]
    return obj


def get_cmd(*cmds, **extra):
    """Read one or more modem values. GET only."""
    cmd = ",".join(cmds)
    p = {"isTest": "false", "cmd": cmd, "multi_data": "1", "_": str(int(time.time() * 1000))}
    p.update(extra)
    try:
        r = s.get(B + "/goform/goform_get_cmd_process", params=p, timeout=15)
        try:
            return mask(r.json())
        except Exception:
            return {"_raw": r.text[:500]}
    except Exception as e:
        return {"_error": str(e)}


def probe_url(path, note):
    """Just see if a path answers. GET only, body is thrown away (only status + type kept)."""
    url = B + "/" + path.lstrip("/")
    try:
        r = s.get(url, timeout=12, stream=True)
        ct = r.headers.get("Content-Type", "?")
        ln = r.headers.get("Content-Length", "?")
        r.close()
        tag = "ANSWERS" if r.status_code == 200 else "no (%d)" % r.status_code
        log("  [%s] %-40s %s  type=%s len=%s" % (tag, path, note, ct, ln))
        return r.status_code == 200
    except Exception as e:
        log("  [err]     %-40s %s  (%s)" % (path, note, e))
        return False


# ---- log in -------------------------------------------------------------
r = s.post(B + "/goform/goform_set_cmd_process",
           data={"isTest": "false", "goformId": "LOGIN", "password": base64.b64encode(PASS.encode()).decode()}, timeout=15)
head("LOGIN")
log("login result: " + r.text + "   (0 or 4 = ok, 3 = wrong password)")

# ---- 1) what chip / platform is this -----------------------------------
head("1) WHAT IS THE BRAIN  (chip and firmware)")
info = get_cmd("cr_version", "wa_inner_version", "hw_version", "web_version", "wa_version",
               "mac_address", "imei", "model_name", "PRODUCT_TYPE")
for k, v in info.items():
    log("  %-22s %s" % (k, v))
cr = str(info.get("cr_version", "")) + str(info.get("hw_version", ""))
plat = "unknown"
if re.search(r"ZXIC|ZTE|RedCap|P42|M611|F231", cr, re.I):
    plat = "ZXIC/ZTE-family (very likely): firmware is one packed image, bootloader is usually locked"
elif re.search(r"MDM|Qualcomm|MSM|SDX", cr, re.I):
    plat = "Qualcomm: pulled with QPST/QFIL in 9008 (EDL) mode, needs a signed loader"
elif re.search(r"MT\d|Mediatek|MTK", cr, re.I):
    plat = "MediaTek: pulled with SP Flash Tool (readback), needs the scatter file"
log("  >> platform guess: " + plat)

# ---- 2) does the modem have its own update server (easiest) ------------
head("2) THE MODEM'S OWN UPDATE (FOTA) -- the safe, intended way")
fota = get_cmd("fota_updatemode", "fota_new_version_state", "fota_current_upgrade_state",
               "fota_pkg_total_size", "dm_last_check_time", "upgrade_result", "ota_update_success",
               "fota_updateintervalday", "fota_allowroamingupdate", "upgrade_notice_flag")
for k, v in fota.items():
    log("  %-28s %s" % (k, v))
log("  >> The maker already ships signed updates to this modem over the air.")
log("     This is the ONLY way to update without risking a brick. Building your own")
log("     image and flashing it bypasses that signature and can kill the modem.")

# ---- 3) is there a software door to the OS (adb / telnet / shell) ------
head("3) SOFTWARE DOOR TO THE OS  (read-only: we only check if the field exists)")
door = get_cmd("telnet_enable", "adb_enable", "usb_mode", "usb_pid", "dial_mode",
               "debug_level", "rndis_switch", "device_mode", "cpe_enable")
any_door = False
for k, v in door.items():
    present = not (isinstance(v, dict) and ("_error" in v or "_raw" in v)) and v != ""
    any_door = any_door or present
    log("  %-16s %s" % (k, v if present else "(modem did not return this field)"))
log("  >> If telnet_enable / adb_enable exist above, a hidden setting could switch")
log("     them on and you'd reach a root shell over USB or the network, then copy")
log("     the whole flash with `dd` or `cat /proc/mtd`. This probe does NOT switch")
log("     them on -- that is a deliberate next step, and a wrong one can lock you out.")

# ---- 4) recovery / download endpoints (GET only, body discarded) -------
head("4) RECOVERY / DOWNLOAD ENDPOINTS  (only checking if they answer)")
log("  (A 200 here just means the path exists; nothing is downloaded or changed.)")
for path, note in [
    ("cgi-bin/upload/upload.cgi", "the firmware upload target (POST to flash -- NOT used here)"),
    ("version.txt", "sometimes the build id"),
    ("web/version.json", "sometimes the build id"),
    ("mtd0", "raw flash, if the web server exposes it"),
    ("proc/mtd", "the flash partition map"),
    ("cgi-bin/ExportSettings.sh", "config backup (some ZTE models)"),
    ("goform/goform_get_cmd_process?cmd=Language", "sanity check"),
]:
    probe_url(path, note)

# ---- 5) where does it phone home for updates ---------------------------
head("5) WHERE THE MODEM LOOKS FOR UPDATES  (read-only)")
srv = get_cmd("fota_server_url", "dm_server", "ota_server", "update_server_url",
              "fota_upgrade_selector", "tr069_acs_url")
for k, v in srv.items():
    if v and not (isinstance(v, dict)):
        log("  %-22s %s" % (k, v))
log("  >> If a URL shows here, that host serves the maker's signed images. You could")
log("     watch it for a new official version, but you cannot sign your own for it.")

head("SUMMARY -- the honest picture")
log("""  * Safe:   let the modem update itself (section 2). Nothing to build.
  * Medium: IF section 3 shows a telnet/adb field, turning it on (a separate, manual
            step) gives a shell to COPY the firmware out read-only. Copying is safe;
            writing a home-made image back is what bricks it.
  * Hard:   no software door -> opening the case and using the serial pads or a flash
            reader. Tech-repair work, real brick risk, and it can disturb the radio
            calibration / IMEI (you said you do NOT want the modem damaged).
  * Note:   even a perfect copy does not make the internet faster -- band, speed and
            ping live in the locked radio firmware, not in a setting you can rewrite.

  This script changed nothing. Decide the next step with the results above.""")
out.close()
print("\nsaved:", OUT)
