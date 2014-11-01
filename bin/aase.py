#!/usr/bin/env python

import argparse
import os.path
import re
import subprocess
import sys
import time

NAME = 'Android App Schedule Enumerator'
VERSION = '0.1'
DESCRIPTION = NAME + ' version ' + VERSION
REPLAY_FILE = "events.trc"

options = []

def warn(msg):
  print "[warning] %s" % msg
  
def err(msg):
  print "[error] %s" % msg
  sys.exit()

def command(cmd, get_output=True):
  cs = cmd.split()
  if options.debug:
    print "[call] %s" % cmd
  if get_output:
    return subprocess.check_output(cmd.split())
  else:
    return subprocess.call(cmd.split(), stdout=open(os.devnull,'w'), stderr=subprocess.STDOUT) == 0

def adb(cmd, *args):
  return command("adb %s" % cmd, *args)
  
def aapt(cmd, *args):
  return command("aapt %s" % cmd, *args)

def package_name(app_path):
  m = re.search(r'package: name=\'([^\']*)\'', aapt("dump badging %s" % app_path))
  if m: return m.group(1)
  else: err("Could not find package name for " + app_path + ".")

def launchable_activity(app_path):
  m = re.search(r'launchable-activity: name=\'([^\']*)\'', aapt("dump badging %s" % app_path))
  if m: return m.group(1)
  else: err("Could not find launchable acitvity for " + app_path + ".")

def device_is_running():
  return adb("shell echo", False)

def app_is_installed(app_name):
  return "package:" in adb("shell pm list packages %s" % app_name)

def app_is_running(app_name):
  return app_name in adb("shell ps")

def recording_exists(app_name):
  return "No such file" not in adb("shell ls /data/data/%s/files/%s" % (app_name, REPLAY_FILE))

def install(app_name, app_path):
  print "Installing", app_name
  adb("install %s" % app_path, False)

def uninstall(app_name):
  print "Uninstalling", app_name
  adb("shell pm uninstall %s" % app_name)

def start(app_name, activity, *args):
  print "Starting", app_name
  adb("shell am start %s %s/%s" % (" ".join(map(lambda x: "-e " + x, args)), app_name, activity))

def stop(app_name):
  print "Stopping", app_name
  adb("shell am force-stop %s" % app_name)

def start_up(app_path, *args):
  if not device_is_running():
    err("Device is not running.")

  app_name = package_name(app_path)
  activity = launchable_activity(app_path)

  if not app_is_installed(app_name):
    install(app_name, app_path)

  if not app_is_running(app_name):
    start(app_name, activity, *args)

  return app_name

def wait_for_close(app_name):
  while app_is_running(app_name):
    time.sleep(1)

def tear_down(app_name, do_uninstall=False):
  if app_is_running(app_name):
    stop(app_name)

  if app_is_installed(app_name) and do_uninstall:
    uninstall(app_name)

def do_record():
  # TODO why does `mode record` require the `numDelays` argument?
  app_name = start_up(options.apkfile, "mode record", "numDelays 0")
  print "Running %s in record mode." % app_name
  wait_for_close(app_name)
  print "Recording completed."
  tear_down(app_name, options.uninstall)

def do_replay():
  # TODO why can't `mode replay` have a default `numDelays` argument?
  app_name = start_up(options.apkfile, "mode replay", "numDelays %d" % options.delays)
  print "Running %s in replay mode." % app_name
  if recording_exists(app_name):
    wait_for_close(app_name)
    print "Replay completed."
  else:
    warn("The replayer did not find a recording.")
  tear_down(app_name, options.uninstall)

def validate_apk_file(f):
  if os.path.exists(f) and os.path.splitext(f)[1] == '.apk':
    return f
  else:
    err("Expected the path to an Android app's APK file.")

def aase_parser():
  p = argparse.ArgumentParser(description=DESCRIPTION)

  p.add_argument('--version', action='version', version=DESCRIPTION)

  p.add_argument('--debug',
    dest='debug', action='store_true', default=False,
    help='turn on debugging')

  p.add_argument('apkfile', metavar='APK-FILE',
    type=lambda f: validate_apk_file(f),
    help='the APK file of an Android app')

  p.add_argument('--record',
    dest='mode', action='store_const', const='record', default='record',
    help='run the app in record mode (default)')

  p.add_argument('--replay',
    dest='mode', action='store_const', const='replay',
    help='run the app in replay mode')

  p.add_argument('--delays',
    dest='delays', metavar='K', type=int, default=0,
    help='number of scheduler delays')

  p.add_argument('--uninstall',
    dest='uninstall', action='store_true', default=False,
    help='uninstall the app after running')

  return p

if __name__ == '__main__':
  options = aase_parser().parse_args()

  if options.mode == 'replay':
    do_replay()

  else:
    do_record()
