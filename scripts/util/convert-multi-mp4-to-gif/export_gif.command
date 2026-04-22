#!/bin/bash

input_dir="$(pwd)/origin"
output_dir="$(pwd)/output"

mkdir -p "$output_dir"

for file in "$input_dir"/*.mp4; do
    filename=$(basename "$file" .mp4)
    ffmpeg -i "$file" -vf "fps=20,scale=640:-1:flags=lanczos" -c:v gif "$output_dir/$filename.gif"
done
