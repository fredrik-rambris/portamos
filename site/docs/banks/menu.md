---
layout: default
title: Menu bank
parent: Banks
nav_order: 8
---

# Menu bank

A Menu bank holds an AMOS menu definition tree used with the `MENU` command family.

## Commands

```bash
# Export to JSON
portamos disasm Menu.Abk output/menu.json

# Reassemble
portamos asm output/menu.json Menu.Abk
```

## JSON schema

```json
{
  "type": "Menu",
  "bankNumber": 1,
  "chipRam": false,
  "items": [
    {
      "style": "TEXT",
      "normal": "File",
      "pen": 1,
      "paper": 0,
      "items": [
        {
          "style": "TEXT",
          "normal": "Open",
          "selected": "Open",
          "separate": false,
          "inactive": false
        },
        {
          "style": "TEXT",
          "normal": "Quit",
          "separate": true
        }
      ]
    }
  ]
}
```

## Field reference

### Top level

| Field        | Type    | Description                           |
|--------------|---------|---------------------------------------|
| `type`       | string  | Always `"Menu"`                       |
| `bankNumber` | integer | Bank slot (1-based)                   |
| `chipRam`    | boolean | `true` = chip RAM, `false` = fast RAM |
| `items`      | array   | Top-level menu bar items              |

### `items[]` (recursive)

| Field                                            | Type    | Description                                                                  |
|--------------------------------------------------|---------|------------------------------------------------------------------------------|
| `style`                                          | string  | Item style: `"TEXT"`, `"ITEM"`, etc.                                         |
| `normal`                                         | string  | Label in normal state                                                        |
| `selected`                                       | string  | Label when selected (defaults to `normal` if omitted)                        |
| `inactiveDisplay`                                | string  | Label when inactive                                                          |
| `separate`                                       | boolean | Draw a separator above this item                                             |
| `inactive`                                       | boolean | Item is greyed out                                                           |
| `static`                                         | boolean | Item position is fixed                                                       |
| `itemMovable`                                    | boolean | Item can be moved by the user                                                |
| `x`, `y`                                         | integer | Position for movable items                                                   |
| `pen`, `paper`, `outline`                        | integer | Colours for normal state                                                     |
| `penSel`, `paperSel`, `outlineSel`               | integer | Colours for selected state                                                   |
| `font`                                           | string  | Font name                                                                    |
| `keyFlag`, `keyAscii`, `keyScancode`, `keyShift` | integer | Keyboard shortcut definition                                                 |
| `items`                                          | array   | Sub-items (dropdown entries under a menu title)                              |
| `flags`                                          | integer | Raw flags integer (legacy; takes precedence over semantic fields if present) |
