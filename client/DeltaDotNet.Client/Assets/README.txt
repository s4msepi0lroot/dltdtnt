DeltaDotNet - Assets folder
===========================

Drop your own images here. Nothing is required for the build to succeed:
if a file is missing the client just falls back to a text logo / default icon.

File names the client looks for (all optional):

  logo.png       - big logo on the login screen and in the top bar
                   recommended: PNG with transparency, ~512x160 px
  app.ico        - Windows executable icon (multi-size .ico: 16/32/48/256)
  icon.png       - window icon / taskbar icon, 256x256 px
  heart.png      - the cursor / selection marker (Deltarune soul), 24x24 px
  bg.png         - default background image behind the menus, 1280x720 px
  banner.png     - optional wide banner shown on the lobby list, 1280x180 px

Fonts (optional):
  fonts\DTM.ttf  - put any pixel font here and select it in
                   Settings -> Interface -> Font

After adding files just rebuild (or copy them into the folder next to
DeltaDotNet.exe - they are read at runtime, not embedded).
