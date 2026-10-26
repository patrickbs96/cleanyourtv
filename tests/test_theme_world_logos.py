"""A world's own logo picture lives beside the theme, not in it (Supernatural spec § 4.4, § 4.5).

`tools/theme.sh --logo <world> <picture.png>` puts `world_logos/world_logo_<world>.png` on the TV and
`--logo <world> --remove` takes it away again. The picture is the household's own and never comes from the
repository, so a theme push and `--reset` must leave the `world_logos/` folder exactly as it was: a reset
that dropped the logo would quietly bring back the drawn lettering.

The harness runs the real tools/theme.sh and tools/b64push.py against a stub tools/tv.py whose "TV shell"
is bash on this machine and a stub `content` command standing in for ThemeProvider, like
test_theme_push_ownership. Unlike that one it needs no symlink and spells every path the way the shell on
each side reads it, so it also runs under Git Bash on Windows.
"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
DEVICE_DIR = "/sdcard/Android/data/com.gathin.tvhome/files"
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\rIHDR" + b"\x00" * 17   # the signature and a header's worth of bytes

FAKE_TV = r'''
import json
import os
import subprocess
import sys

DEVICE_DIR = "/sdcard/Android/data/com.gathin.tvhome/files"


def local(command):
    return (command.replace(DEVICE_DIR, os.environ["FAKE_DIR_SH"])
            .replace("/data/local/tmp/cyt-theme", os.environ["FAKE_STAGE_SH"]))


class Device:
    max_chunk_size = 4096

    def shell(self, command, read_timeout_s=None):
        with open(os.environ["FAKE_LOG"], "a", encoding="utf-8") as f:
            f.write(json.dumps({"shell": command}) + "\n")
        script = "PATH=" + os.environ["FAKE_BIN_PATH"] + ":$PATH; " + local(command)
        return subprocess.run([os.environ["FAKE_SH"], "-c", script], capture_output=True, text=True).stdout

    def close(self):
        pass


def connect():
    return Device()


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--push":
        src, dst = args[1], local(args[2])
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(src, "rb") as f, open(dst, "wb") as g:
            g.write(f.read())
    else:
        sys.stdout.write(Device().shell(" ".join(args)))
'''

# Stands in for `content write/delete` against content://com.gathin.tvhome.theme, with ThemeFiles' rule for
# the logo folder: only world_logos/world_logo_<id>.png, never the folder as a whole.
FAKE_CONTENT = r'''
import json
import os
import sys

args = sys.argv[1:]
with open(os.environ["FAKE_CONTENT_LOG"], "a", encoding="utf-8") as f:
    f.write(json.dumps({"content": args}) + "\n")
action, uri = args[0], args[args.index("--uri") + 1]
path = os.path.join(os.environ["FAKE_DIR"], uri.split("com.gathin.tvhome.theme/", 1)[1])
if action == "write":
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(sys.stdin.buffer.read())
elif action == "delete":
    if os.path.isdir(path):
        if os.path.basename(path) != "wallpapers":
            sys.exit(0)
        for name in os.listdir(path):
            if name.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                os.remove(os.path.join(path, name))
    elif os.path.exists(path):
        os.remove(path)
'''


def bash():
    """Git Bash on Windows, bash elsewhere; None when there is none (or only WSL's, which sees other paths)."""
    found = shutil.which("bash")
    if not found:
        return None
    if os.name != "posix":
        probe = subprocess.run([found, "-c", "uname -o"], capture_output=True, text=True)
        if "Msys" not in probe.stdout and "Cygwin" not in probe.stdout:
            return None
    return found


class WorldLogoPushTest(unittest.TestCase):
    def setUp(self):
        self.sh = bash()
        if not self.sh:
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        for name in ("theme.sh", "b64push.py"):
            shutil.copy2(TOOLS / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV, encoding="utf-8")
        python = Path(sys.executable).as_posix()
        venv = self.tools / "venv" / "bin"
        venv.mkdir(parents=True)
        self.shim(venv / "python", f'exec "{python}" "$@"')
        self.bin = self.base / "bin"
        self.bin.mkdir()
        (self.base / "fake_content.py").write_text(FAKE_CONTENT, encoding="utf-8")
        self.shim(self.bin / "content", f'exec "{python}" "{(self.base / "fake_content.py").as_posix()}" "$@"')
        self.device = self.base / "device"
        self.device.mkdir()
        self.stage = self.base / "stage"
        self.log = self.base / "log.jsonl"
        self.content_log = self.base / "content.jsonl"
        self.theme = self.base / "aurora"
        self.theme.mkdir()
        (self.theme / "config.json").write_text(json.dumps({"background": "#101418"}), encoding="utf-8")
        self.logos = self.device / "world_logos"

    def shim(self, path, line):
        path.write_text("#!/bin/sh\n" + line + "\n", encoding="utf-8", newline="\n")
        path.chmod(0o755)

    def sh_path(self, path):
        """The path as the shell spells it in PATH: /tmp/... on Linux and macOS, /c/Users/... under Git Bash."""
        out = subprocess.run([self.sh, "-c", 'cd "$1" && pwd', "_", str(path)],
                             capture_output=True, text=True, check=True)
        return out.stdout.strip()

    def run_theme(self, *args):
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env.update({"TV_HOST": "192.0.2.10", "FAKE_LOG": str(self.log), "FAKE_DIR": str(self.device),
                    "FAKE_DIR_SH": self.device.as_posix(), "FAKE_STAGE_SH": self.stage.as_posix(),
                    "FAKE_CONTENT_LOG": str(self.content_log), "FAKE_SH": self.sh,
                    "FAKE_BIN_PATH": self.sh_path(self.bin)})
        return subprocess.run([self.sh, (self.tools / "theme.sh").as_posix(), *args], env=env, cwd=self.base,
                              capture_output=True, text=True, timeout=300, stdin=subprocess.DEVNULL)

    def shell_commands(self):
        if not self.log.exists():
            return []
        return [json.loads(line)["shell"] for line in self.log.read_text(encoding="utf-8").splitlines()]

    def provider_calls(self):
        if not self.content_log.exists():
            return []
        return [json.loads(line)["content"] for line in self.content_log.read_text(encoding="utf-8").splitlines()]

    def put_logo_on_the_tv(self):
        self.logos.mkdir()
        (self.logos / "world_logo_supernatural.png").write_bytes(PNG + b"on the TV")

    def test_theme_push_keeps_world_logos(self):
        self.put_logo_on_the_tv()
        (self.theme / "wallpapers").mkdir()
        (self.theme / "wallpapers" / "dusk.png").write_bytes(PNG)
        pushed = self.run_theme(self.sh_path(self.theme))
        self.assertEqual(pushed.returncode, 0, pushed.stdout + pushed.stderr)
        self.assertEqual((self.device / "wallpapers" / "dusk.png").read_bytes(), PNG, "the theme itself arrived")
        self.assertEqual((self.logos / "world_logo_supernatural.png").read_bytes(), PNG + b"on the TV",
                         "a theme push must not touch the world logos")
        reset = self.run_theme("--reset")
        self.assertEqual(reset.returncode, 0, reset.stdout + reset.stderr)
        self.assertFalse((self.device / "wallpapers" / "dusk.png").exists(), "--reset still clears the theme")
        self.assertEqual((self.logos / "world_logo_supernatural.png").read_bytes(), PNG + b"on the TV",
                         "--reset must not touch the world logos either")
        for command in self.shell_commands():
            self.assertNotIn("world_logo", command)

    def test_logo_flag_puts_and_removes(self):
        picture = self.base / "my logo dir"
        picture.mkdir()
        (picture / "logo.png").write_bytes(PNG + b"mine")
        (self.device / "launcher_choice").write_text("kept", encoding="utf-8")
        put = self.run_theme("--logo", "supernatural", "my logo dir/logo.png")
        self.assertEqual(put.returncode, 0, put.stdout + put.stderr)
        self.assertEqual((self.logos / "world_logo_supernatural.png").read_bytes(), PNG + b"mine")
        self.assertIn(["write", "--uri", "content://com.gathin.tvhome.theme/world_logos/world_logo_supernatural.png"],
                      self.provider_calls(), "written as the launcher, like every theme file")
        commands = " | ".join(self.shell_commands())
        self.assertNotIn("theme_revision", commands, "a logo is no theme push: on-TV layout choices stay")
        self.assertNotIn("config.json", commands)
        self.assertIn("am force-stop com.gathin.tvhome", commands, "Home restarts so the logo shows")
        self.assertFalse((self.device / "theme_revision").exists())

        self.log.unlink()
        removed = self.run_theme("--logo", "supernatural", "--remove")
        self.assertEqual(removed.returncode, 0, removed.stdout + removed.stderr)
        self.assertFalse((self.logos / "world_logo_supernatural.png").exists())
        self.assertIn(["delete", "--uri", "content://com.gathin.tvhome.theme/world_logos/world_logo_supernatural.png"],
                      self.provider_calls())
        self.assertNotIn("theme_revision", " | ".join(self.shell_commands()))

    def test_logo_flag_refuses_before_transfer(self):
        (self.base / "photo.jpg").write_bytes(b"\xff\xd8\xff\xe0 a jpeg")
        (self.base / "huge.png").write_bytes(PNG + b"\x00" * (2 * 1024 * 1024))
        (self.base / "ok.png").write_bytes(PNG)
        cases = {
            ("--logo", "supernatural", "photo.jpg"): "not a PNG",
            ("--logo", "supernatural", "huge.png"): "2 MB",
            ("--logo", "supernatural", "missing.png"): "cannot read",
            ("--logo", "supernatral", "ok.png"): "unknown world",
            ("--logo", "../x", "ok.png"): "unknown world",
            ("--logo", "supernatural"): "usage",
        }
        for args, message in cases.items():
            with self.subTest(args=args):
                result = self.run_theme(*args)
                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertIn(message, result.stderr)
                self.assertEqual(self.shell_commands(), [], "nothing may reach the TV")
        self.assertFalse(self.logos.exists())


class WorldLogoScriptsTest(unittest.TestCase):
    """Text checks that need no shell: the world list, the Windows twin and the docs."""

    def test_the_scripts_know_every_world(self):
        java = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/World.java").read_text(encoding="utf-8")
        worlds = re.findall(r'^\s+[A-Z]+\("([a-z]+)", R\.string', java, re.MULTILINE)
        self.assertIn("supernatural", worlds)
        sh = (TOOLS / "theme.sh").read_text(encoding="utf-8")
        listed = re.search(r'^WORLDS="([a-z ]+)"', sh, re.MULTILINE)
        self.assertIsNotNone(listed, "theme.sh names the worlds a logo may be for")
        self.assertEqual(listed.group(1).split(), worlds, "theme.sh's list follows World.java")
        ps1 = (TOOLS / "theme.ps1").read_text(encoding="utf-8")
        listed = re.search(r"^\$worlds = @\(([^)]*)\)", ps1, re.MULTILINE)
        self.assertIsNotNone(listed, "theme.ps1 names the worlds a logo may be for")
        self.assertEqual(re.findall(r"'([a-z]+)'", listed.group(1)), worlds)

    def test_the_windows_script_keeps_the_logo_out_of_theme_and_reset(self):
        ps1 = (TOOLS / "theme.ps1").read_text(encoding="utf-8")
        self.assertIn("[string]$Logo", ps1)
        self.assertIn("[switch]$Remove", ps1)
        self.assertIn("world_logos/world_logo_$Logo.png", ps1)
        reset = next(line for line in ps1.splitlines()
                     if line.strip().startswith("& $py -I tv.py \"mkdir -p $dir") and "Drop 'config.json'" in line)
        self.assertNotIn("world_logo", reset)
        self.assertNotIn("world_logo", ps1.split("$clearWallpapers = ", 1)[1].splitlines()[0])
        self.assertIn("2097152", ps1, "the same 2 MB limit as theme.sh and WorldLogo")

    def test_the_readme_explains_the_flag(self):
        readme = (ROOT / "themes" / "README.md").read_text(encoding="utf-8")
        self.assertIn("tools/theme.sh --logo supernatural", readme)
        self.assertIn("--remove", readme)
        self.assertIn("-Logo supernatural", readme)
        self.assertIn("world_logos/", readme)


if __name__ == "__main__":
    unittest.main()
