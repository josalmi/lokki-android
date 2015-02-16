#!/bin/bash
set -e
function do_magic() {
  echo "Doing magic - this might take a while"
  adb install -r travis-hacks.apk
  adb shell pm grant no.finn.android_emulator_hacks android.permission.SET_ANIMATION_SCALE
  adb shell am start -n no.finn.android_emulator_hacks/no.finn.android_emulator_hacks.HackActivity
  echo "Done"
}

# Check if there is no AVD in cache with same name
if [ ! -d "$HOME/.android/avd/$NAME.avd" ] || [ ! -f "$HOME/.android/avd/$NAME.ini" ]; then 
  echo "no" | android create avd --force -n "$NAME" -t "$TARGET" --abi "$ABI"
  emulator -avd "$NAME" -no-audio -no-window &
  android-wait-for-emulator
  do_magic 
else
  echo "Using AVD from cache"
  emulator -avd "$NAME" -no-audio -no-window &
  android-wait-for-emulator
fi
