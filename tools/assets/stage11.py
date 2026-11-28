"""Stage 11 assets: language strings for the soundtrack (track titles, music settings) and other presentation text.

Track data itself (OGG files, sounds.json, music_tracks.json) is produced by tools/music/compose.py.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "underdeep")


def main(_lang=None):
    lang = {
        "message.underdeep.music.status": "Underdeep music: %s, volume %s%%, %s. Now playing: %s",
        "message.underdeep.music.gaps": "with vanilla-style gaps",
        "message.underdeep.music.continuous": "continuous",
    }
    table = os.path.join(ROOT, "music_tracks.json")
    if os.path.exists(table):
        for t in json.load(open(table)):
            lang[f"music.underdeep.{t['id']}"] = t["title"]
    return lang


if __name__ == "__main__":
    print(len(main()))
