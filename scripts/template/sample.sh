#!/usr/bin/env bash
set -euo pipefail

echo "Shell Box の example-demo です。"
read -r -p "名前を入力してください: " name
echo "こんにちは、${name:-（未入力）} さん"
