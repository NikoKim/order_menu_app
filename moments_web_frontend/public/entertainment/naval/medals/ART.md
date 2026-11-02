# Naval Streak Medals

Generated with the built-in image generator, matching the existing naval pixel-art
assets. Classic medals use blue-turquoise ribbons and anchors; military medals use
green ribbons, shields and crossed cannons. Bronze, silver and gold correspond to
3, 5 and 10 consecutive multiplayer wins.

Files are transparent 128 x 128 PNGs with centered artwork and safe padding:

- classic-3.png
- classic-5.png
- classic-10.png
- military-3.png
- military-5.png
- military-10.png

Raw generation retained at:
`E:/Codex/Home/generated_images/01a1976d-f463-7bc0-b1aa-0edaab807a1f/exec-421b20e2-6a10-4965-94a6-6f08475b782a.png`

The deterministic generate2dsprite processor keyed the magenta background,
split the 2 x 3 prop sheet, centered each asset, and exported transparent frames.
QC files and the raw/clean contact sheets are retained under
`.agents/naval-medal-art/`. Strict QC passed with no empty, clamped, or edge-touch
frames. These are newly generated game emblems, not real military insignia.
