#!/usr/bin/env python3
"""Build the 60-second showcase from real app media. Needs macOS Swift and FFmpeg.

Run from anywhere: python3 Scripts/build-showcase.py
Raw simulator captures stay outside Git; Docs/media/ios-spin.mp4 is the trimmed source.
"""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MEDIA = ROOT / "Docs/media"
WORK = ROOT / ".build/showcase"


def run(*args):
    subprocess.run([str(x) for x in args], cwd=ROOT, check=True)


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    run("swift", "Scripts/GenerateShowcase.swift")
    scenes = [
        ("home", "ios-home.jpg", 6),
        ("spin", "ios-spin.mp4", 12),
        ("room", "android-room.png", 9),
        ("library", "ios-library.jpg", 8),
        ("receipt", "ios-offline-receipt.jpg", 9),
    ]
    output_options = ["-an", "-r", "30", "-c:v", "libx264", "-crf", "22", "-preset", "medium", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]
    for name, asset, seconds in scenes:
        source_args = ["-i", MEDIA / asset] if asset.endswith(".mp4") else ["-loop", "1", "-framerate", "30", "-i", MEDIA / asset]
        filters = (
            "[1:v]scale=430:930:force_original_aspect_ratio=decrease,setsar=1[phone];"
            "[0:v][phone]overlay=x=1240+(430-overlay_w)/2:y=68+(930-overlay_h)/2:shortest=1,"
            f"fade=t=in:st=0:d=0.25,fade=t=out:st={seconds - 0.25}:d=0.25[v]"
        )
        run("ffmpeg", "-y", "-v", "error", "-loop", "1", "-framerate", "30", "-i", WORK / f"{name}.png", *source_args,
            "-filter_complex", filters, "-map", "[v]", "-t", seconds, *output_options, WORK / f"{name}.mp4")
    run("ffmpeg", "-y", "-v", "error", "-loop", "1", "-framerate", "30", "-i", MEDIA / "architecture.png",
        "-vf", "fade=t=in:st=0:d=0.25,fade=t=out:st=15.75:d=0.25", "-t", "16", *output_options, WORK / "architecture.mp4")
    playlist = WORK / "concat.txt"
    playlist.write_text("".join(f"file '{name}.mp4'\n" for name in [s[0] for s in scenes] + ["architecture"]))
    run("ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", playlist, "-c", "copy", "-movflags", "+faststart", MEDIA / "food-run-demo.mp4")
    run("ffmpeg", "-y", "-v", "error", "-ss", "2", "-i", MEDIA / "food-run-demo.mp4", "-frames:v", "1", "-update", "1", MEDIA / "demo-poster.jpg")
    info = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration,size", "-of", "json", str(MEDIA / "food-run-demo.mp4")]))
    print(json.dumps(info["format"], indent=2))


if __name__ == "__main__":
    main()
