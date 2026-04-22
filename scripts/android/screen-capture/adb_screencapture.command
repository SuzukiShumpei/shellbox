#!/bin/bash

output_dir="$(pwd)/output"

mkdir -p "$output_dir"

read -p "保存するファイル名を入力してください（拡張子 .png は付けなくても可。付けた場合はそのまま使います）: " filename

# 拡張子が無ければ .png を付与
case "$filename" in
    *.png|*.PNG) outfile="$filename" ;;
    *) outfile="${filename}.png" ;;
esac
outpath="$output_dir/$outfile"

echo "スクリーンショットを取得しています..."
adb shell screencap -p /sdcard/screenshot.png

adb pull /sdcard/screenshot.png "$outpath"

adb shell rm /sdcard/screenshot.png

echo "保存しました: $outpath"

read -p "保存先の Finder を開きますか？ (y/n): " open_finder

if [ "$open_finder" = "y" ]; then
    open "$output_dir"
fi

exit
