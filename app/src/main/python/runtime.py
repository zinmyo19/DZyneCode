"""Embedded Python runner and constrained pure-Python wheel installer."""
import builtins
import email.parser
import importlib.metadata
import json
import os
import re
import shutil
import sys
import tempfile
import traceback
import urllib.error
import urllib.parse
import urllib.request
import zipfile

from com.dzynecode.app import PythonBridge


class _ConsoleStream:
    def __init__(self, is_error=False):
        self.is_error = is_error

    def write(self, value):
        if value:
            PythonBridge.write(str(value), self.is_error)
        return len(value)

    def flush(self):
        pass

    def isatty(self):
        return True

    @property
    def encoding(self):
        return "utf-8"


def _add_user_site(workspace):
    site_dir = os.path.join(workspace, "site-packages")
    os.makedirs(site_dir, exist_ok=True)
    if site_dir not in sys.path:
        sys.path.insert(0, site_dir)
    if workspace not in sys.path:
        sys.path.insert(0, workspace)
    return site_dir


def run_script(source, script_name, workspace):
    """Run editor source with an app-private working directory and input()."""
    os.makedirs(workspace, exist_ok=True)
    _add_user_site(workspace)
    os.chdir(workspace)
    old_stdout, old_stderr = sys.stdout, sys.stderr
    old_input = builtins.input
    sys.stdout = _ConsoleStream(False)
    sys.stderr = _ConsoleStream(True)
    builtins.input = lambda prompt="": PythonBridge.readInput(str(prompt))
    script_path = os.path.join(workspace, os.path.basename(script_name or "main.py"))
    namespace = {"__name__": "__main__", "__file__": script_path, "__package__": None}
    try:
        exec(compile(source, script_path, "exec"), namespace, namespace)
        PythonBridge.write("\n[Done] Script finished successfully.\n", False)
        return "ok"
    except BaseException:
        traceback.print_exc()
        PythonBridge.write("\n[Error] Script stopped with an exception.\n", True)
        return "error"
    finally:
        builtins.input = old_input
        sys.stdout, sys.stderr = old_stdout, old_stderr


def _metadata_from_wheel(data):
    with zipfile.ZipFile(__import__("io").BytesIO(data)) as wheel:
        for name in wheel.namelist():
            if name.endswith(".dist-info/METADATA"):
                return email.parser.BytesParser().parsebytes(wheel.read(name))
    raise ValueError("Wheel has no package metadata")


def _pypi_json(path):
    request = urllib.request.Request(
        "https://pypi.org/pypi/" + path.rstrip("/") + "/json",
        headers={"User-Agent": "DZyne-Code/1.0 (Android pure-Python package installer)"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = response.read(12 * 1024 * 1024 + 1)
    if len(payload) > 12 * 1024 * 1024:
        raise ValueError("PyPI metadata response is too large")
    return json.loads(payload.decode("utf-8"))


def _pure_wheel(file_info):
    filename = file_info.get("filename", "")
    return bool(re.search(r"-(?:py3|py2\.py3)-none-any\.whl$", filename, re.IGNORECASE))


def _installed_version(name):
    try:
        return importlib.metadata.version(name)
    except importlib.metadata.PackageNotFoundError:
        return None
    except Exception:
        return None


def _read_installed_versions(site_dir):
    versions = {}
    try:
        for entry in os.listdir(site_dir):
            metadata_path = os.path.join(site_dir, entry, "METADATA")
            if entry.endswith(".dist-info") and os.path.isfile(metadata_path):
                with open(metadata_path, "rb") as stream:
                    metadata = email.parser.BytesParser().parse(stream)
                if metadata.get("Name") and metadata.get("Version"):
                    from packaging.utils import canonicalize_name
                    versions[canonicalize_name(metadata["Name"])] = metadata["Version"]
    except OSError:
        pass
    return versions


def _safe_extract(wheel_data, destination, remaining_limit):
    import stat
    total = 0
    with zipfile.ZipFile(__import__("io").BytesIO(wheel_data)) as wheel:
        for item in wheel.infolist():
            name = item.filename.replace("\\", "/")
            parts = name.split("/")
            if name.startswith("/") or any(part in ("", "..") for part in parts[:-1]) or ".." in parts:
                raise ValueError("Unsafe path inside downloaded wheel")
            mode = item.external_attr >> 16
            if stat.S_ISLNK(mode):
                raise ValueError("Symbolic links are not accepted in package wheels")
            total += item.file_size
            if total > remaining_limit:
                raise ValueError("Package unpacked size exceeds the installer limit")
            # Wheels may store Python files beneath .data/purelib; relocate these
            # files to the import root, as pip does for pure-Python wheels.
            clean_parts = parts[:]
            if len(clean_parts) >= 3 and clean_parts[0].endswith(".data") and clean_parts[1] in ("purelib", "platlib"):
                clean_parts = clean_parts[2:]
            elif len(clean_parts) >= 2 and clean_parts[0].endswith(".data"):
                continue
            out_path = os.path.realpath(os.path.join(destination, *clean_parts))
            if not out_path.startswith(os.path.realpath(destination) + os.sep):
                raise ValueError("Unsafe path inside downloaded wheel")
            if item.is_dir():
                os.makedirs(out_path, exist_ok=True)
                continue
            os.makedirs(os.path.dirname(out_path), exist_ok=True)
            with wheel.open(item) as source, open(out_path, "wb") as target:
                shutil.copyfileobj(source, target)
    return total


def install_package(package_spec, workspace):
    """Install a package and dependencies only when PyPI provides pure wheels.

    This intentionally is not pip: no source builds, VCS URLs, native Android
    binaries, or platform-specific wheels are accepted.
    """
    from packaging.markers import default_environment
    from packaging.requirements import Requirement
    from packaging.specifiers import SpecifierSet
    from packaging.utils import canonicalize_name
    from packaging.version import InvalidVersion, Version

    raw = str(package_spec).strip()
    if not raw or len(raw) > 180 or "\n" in raw:
        raise ValueError("Enter a package name such as 'textblob' or 'package==1.2.3'")
    try:
        root_requirement = Requirement(raw)
    except Exception as exc:
        raise ValueError("Invalid package name/version requirement") from exc
    if root_requirement.url or root_requirement.extras:
        raise ValueError("Use a package name and optional version only; URLs/extras are not supported")

    site_dir = _add_user_site(workspace)
    staging = tempfile.mkdtemp(prefix="dzyne-wheel-", dir=workspace)
    from_versions = _read_installed_versions(site_dir)
    chosen = {}
    downloaded = 0
    total_unpacked = 0
    environment = default_environment()
    environment.update({"python_version": "3.11", "python_full_version": "3.11.0", "implementation_name": "cpython", "extra": ""})

    def fetch_requirement(requirement):
        nonlocal downloaded, total_unpacked
        if requirement.marker and not requirement.marker.evaluate(environment):
            return
        name = canonicalize_name(requirement.name)
        if requirement.url or requirement.extras:
            raise ValueError("A dependency uses a direct URL or optional extras, which this installer does not support")
        installed = from_versions.get(name) or _installed_version(name)
        if installed and (not requirement.specifier or requirement.specifier.contains(installed, prereleases=True)):
            return
        if installed:
            raise ValueError("Version conflict: " + name + " " + installed + " is already bundled/installed")
        if name in chosen:
            if requirement.specifier and not requirement.specifier.contains(chosen[name], prereleases=True):
                raise ValueError("Dependency version conflict for " + name)
            return
        if len(chosen) >= 30:
            raise ValueError("Dependency tree is larger than the 30-package safety limit")

        project = _pypi_json(urllib.parse.quote(name, safe="-_."))
        candidates = []
        for version_text, files in project.get("releases", {}).items():
            try:
                version = Version(version_text)
            except InvalidVersion:
                continue
            if requirement.specifier and not requirement.specifier.contains(version, prereleases=True):
                continue
            wheels = [info for info in files if info.get("packagetype") == "bdist_wheel" and _pure_wheel(info)]
            if wheels:
                candidates.append((version, wheels))
        for version, files in sorted(candidates, key=lambda item: item[0], reverse=True):
            wheel_info = next((f for f in files if f.get("yanked") is not True), files[0])
            requires_python = wheel_info.get("requires_python")
            if requires_python:
                try:
                    if not SpecifierSet(requires_python).contains(Version("3.11.0"), prereleases=True):
                        continue
                except Exception:
                    continue
            wheel_url = wheel_info.get("url", "")
            if not wheel_url.startswith("https://files.pythonhosted.org/"):
                continue
            PythonBridge.write("[Package] Downloading " + name + " " + str(version) + "…\n", False)
            request = urllib.request.Request(wheel_url, headers={"User-Agent": "DZyne-Code/1.0"})
            with urllib.request.urlopen(request, timeout=45) as response:
                data = response.read(40 * 1024 * 1024 + 1)
            if len(data) > 40 * 1024 * 1024:
                raise ValueError("Wheel is larger than the 40 MB per-package limit")
            downloaded += len(data)
            if downloaded > 120 * 1024 * 1024:
                raise ValueError("Dependency downloads exceed the 120 MB safety limit")
            metadata = _metadata_from_wheel(data)
            total_unpacked += _safe_extract(data, staging, 160 * 1024 * 1024 - total_unpacked)
            chosen[name] = str(version)
            for dependency in metadata.get_all("Requires-Dist", []):
                fetch_requirement(Requirement(dependency))
            return
        raise ValueError("No compatible pure-Python wheel found for " + name + " (native Android wheels are not supported by this installer)")

    try:
        PythonBridge.write("[Package] Resolving pure-Python wheels from PyPI…\n", False)
        fetch_requirement(root_requirement)
        if not chosen:
            result = "Already available: " + root_requirement.name
            PythonBridge.write("[Package] " + result + "\n", False)
            return result
        shutil.copytree(staging, site_dir, dirs_exist_ok=True)
        if site_dir not in sys.path:
            sys.path.insert(0, site_dir)
        summary = "Installed " + ", ".join(name + " " + version for name, version in sorted(chosen.items()))
        PythonBridge.write("[Package] " + summary + "\n", False)
        return summary
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            raise ValueError("Package not found on PyPI") from exc
        raise ValueError("PyPI returned HTTP " + str(exc.code)) from exc
    except urllib.error.URLError as exc:
        raise ValueError("Network error contacting PyPI: " + str(exc.reason)) from exc
    finally:
        shutil.rmtree(staging, ignore_errors=True)
