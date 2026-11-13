"""Load provider configuration without leaking YAML credential lines on parse errors."""

from pathlib import Path
import yaml


def load_config(path):
    try:
        config = yaml.safe_load(Path(path).read_text(encoding="utf-8"))
    except yaml.YAMLError as error:
        mark = getattr(error, "problem_mark", None)
        location = f" at line {mark.line + 1}, column {mark.column + 1}" if mark else ""
        raise ValueError("Invalid provider YAML" + location + ". Check providers.local.yaml syntax.") from None
    if not isinstance(config, dict) or not isinstance(config.get("providers"), list):
        raise ValueError("Provider YAML must contain a providers list")
    for entry in config["providers"]:
        if not isinstance(entry, dict) or not all(isinstance(entry.get(k), str) and entry[k].strip()
                                                for k in ("name", "model", "protocol", "base_url")):
            raise ValueError("Each provider requires string name, model, protocol and base_url fields")
    return config
