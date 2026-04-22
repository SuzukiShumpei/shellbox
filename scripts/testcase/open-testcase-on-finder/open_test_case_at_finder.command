#!/bin/bash

mock_root="$(pwd)"

if [ "$(basename "$mock_root")" != "resona-smart-app-api-mock" ]; then
    echo "Shell Box の「実行 path」を resona-smart-app-api-mock リポジトリのルートに設定してから実行してください。"
    echo "現在のディレクトリ名: $(basename "$mock_root")（パス: $mock_root）"
    exit 1
fi

echo "検索ルート: $mock_root"
echo "探したいテストケースIDを入力してください:"
read -r testcase_id

dirs=()
while IFS= read -r line; do
    [ -n "$line" ] && dirs+=("$line")
done < <(find "$mock_root" -type d -name "*${testcase_id}*" 2>/dev/null)

count=${#dirs[@]}

if [ "$count" -eq 0 ]; then
    echo "該当するテストケースは見つかりませんでした。"
    exit 1
fi

if [ "$count" -eq 1 ]; then
    open "${dirs[0]}"
    exit 0
fi

echo "複数のテストケースが見つかりました。以下から選択してください："
for i in "${!dirs[@]}"; do
    echo "$((i + 1)): ${dirs[$i]}"
done

read -r choice
open "${dirs[$((choice - 1))]}"
