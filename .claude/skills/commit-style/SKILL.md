---
name: commit-style
description: Convención de mensajes de commit de Kanban Live (cola-tabla). Úsala siempre que vayas a hacer un commit en este repo.
---

# Commits en Kanban Live

## Formato
```
Feature (<parte>): <qué se ha realizado>
```
- Tipo: `Feature`, `Fix`, etc., con inicial mayúscula.
- `<parte>` = la parte trabajada: `backend`, `front`, `infra`, `docs`.
- Descripción: qué se ha realizado, concreta y en una línea. Ej.: `Feature (backend): fase 4 conflictos y reconexion`.
- Opcional: cuerpo con viñetas breves tras una línea en blanco.

## Reglas
- **Sin co-autor**: nunca añadir `Co-Authored-By` ni líneas de atribución a Claude, tampoco en PRs.
- Solo hacer commit cuando el usuario lo pida.
- No commitear secretos (`.env`, credenciales).
- Antes de commitear, `git status` y `git log -5 --format=%s` para seguir el estilo.
- Prefiere crear commits nuevos; no amend ni `--no-verify` salvo petición expresa.
