#!/bin/bash

# Shell Box の「実行 path」（プロセスの cwd）直下に output を置く。
# スクリプトファイルの場所ではなく、作業ディレクトリ基準にする。
output_dir="$(pwd)/output"

mkdir -p "$output_dir"

echo "エミュレーターの画面収録を開始します..."
adb shell screenrecord /sdcard/screenrecord.mp4 &
RECORD_PID=$!

read -p "画面収録を停止するにはエンターキーを押してください。または、終了したい場合はeを入力してください: " input

if [ "$input" = "e" ]; then
    echo "終了します..."
    exit 0
fi

echo "画面収録を停止します..."
adb shell pkill -l SIGINT screenrecord

sleep 3

read -p "保存するファイル名を入力してください（拡張子不要）: " FILENAME

echo "ファイルをMP4のままにしますか？GIFに変換しますか？"
select format in "MP4" "GIF" "MP4とGIFの両方"; do
    case $format in
        MP4 ) 
            adb pull sdcard/screenrecord.mp4 "$output_dir/$FILENAME.mp4"
            break;;
        GIF )
            adb pull sdcard/screenrecord.mp4 "$output_dir/screenrecord.mp4"
            echo "MP4をGIFに変換しています..."
            ffmpeg -i "$output_dir/screenrecord.mp4" -vf "fps=20,scale=320:-1:flags=lanczos" -c:v gif "$output_dir/$FILENAME.gif"
            echo "GIFファイルが作成されました: $FILENAME.gif"
            rm "$output_dir/screenrecord.mp4"
            break;;
        MP4とGIFの両方 )
            adb pull sdcard/screenrecord.mp4 "$output_dir/$FILENAME.mp4"
            echo "MP4をGIFに変換しています..."
            ffmpeg -i "$output_dir/$FILENAME.mp4" -vf "fps=20,scale=320:-1:flags=lanczos" -c:v gif "$output_dir/$FILENAME.gif"
            echo "GIFファイルが作成されました: $FILENAME.gif"
            break;;
        * ) echo "無効な選択です。もう一度入力してください。";;
    esac
done

adb shell rm /sdcard/screenrecord.mp4

echo "処理が完了しました。"

# ユーザーに保存先のFinderを開くかどうか尋ねる
read -p "保存先のFinderを開きますか？ (y/n): " open_finder

# ユーザーの選択に基づいてFinderを開く
if [ "$open_finder" = "y" ]; then
    # 最後に保存したoutputフォルダをFinderで開く
    open "$output_dir"
fi

# シェルを終了
exit