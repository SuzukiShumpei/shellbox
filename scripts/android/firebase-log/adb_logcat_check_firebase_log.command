#!/bin/bash

echo "------------------------------------------------"
echo "Firebase Analytics Logger (Live Stream Only)"
echo "------------------------------------------------"

# 1. 詳細ログ設定
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE

# 2. ログのクリアと監視開始
echo "Cleaning old logs and starting..."
echo "------------------------------------------------"
adb logcat -c && adb logcat -v time -s FA FA-SVC