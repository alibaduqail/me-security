# Artwork workflow

The three `security_terminal_bright.png`, `security_terminal_medium.png`, and
`security_terminal_dark.png` files in `src/main/resources/assets/ae2security/textures/part/`
are the maintained source layers for the terminal face. Edit those 16×16 RGBA
layers directly in a pixel editor, preserving their separate transparency and
the model references in both `security_terminal_on.json` and
`security_terminal_off.json`. `blank.png` is deliberately transparent.

The retired `tools/generate_textures.py` drew an obsolete single face and must
not be used to update these layers. Run `python3 tools/validate_assets.py` after
editing resources; it reads and checks the current layers without changing them.
