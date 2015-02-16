#!/bin/bash
# Sync the emulator and kill it to get rid of lock files
adb shell sync
adb emu kill
