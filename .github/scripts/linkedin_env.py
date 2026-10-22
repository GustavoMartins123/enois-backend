"""Sincroniza as credenciais do LinkedIn no manifesto do GitOps (env do pod).
Roda no job de deploy com os valores vindos dos GitHub Secrets via env.
Upsert: se a variável existe no bloco env, substitui o value; se não existe,
insere o bloco depois do REDE_FRONTEND_URL."""
import os
import sys

path = os.environ["MANIFEST"]
vals = [
    ("LINKEDIN_CLIENT_ID", os.environ.get("LINKEDIN_CLIENT_ID", "")),
    ("LINKEDIN_CLIENT_SECRET", os.environ.get("LINKEDIN_CLIENT_SECRET", "")),
    ("LINKEDIN_REDIRECT_URI", os.environ.get("LINKEDIN_REDIRECT_URI", "")),
]

lines = open(path).read().split("\n")
out = []
i = 0
while i < len(lines):
    line = lines[i]
    stripped = line.strip()
    if stripped.startswith("- name: "):
        name = stripped[len("- name: "):]
        match = [v for v in vals if v[0] == name]
        if match:
            out.append(line)
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("- name:"):
                i += 1
            indent = line[: len(line) - len(line.lstrip())]
            out.append(indent + "  value: " + repr(match[0][1]))
            continue
    out.append(line)
    i += 1

content = "\n".join(out)
for name, value in vals:
    if "- name: " + name not in content:
        anchor = "        - name: REDE_FRONTEND_URL\n"
        block = "        - name: " + name + "\n          value: " + repr(value) + "\n"
        content = content.replace(anchor, anchor + block, 1)

open(path, "w").write(content)
sys.exit(0)
