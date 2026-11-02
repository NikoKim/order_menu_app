# Naval Pixel Art V2

Generated with the built-in image-generation tool on 2026-10-30. These are original generated raster game assets, not Canvas/SVG placeholder drawings. The equipment pack used the fleet raw sheet as a style reference.

## Runtime Contract

- `patrol.png`, `destroyer.png`, `cruiser.png`, `battleship.png`: 256 x 256 RGBA, bow faces up. The four ship sizes are 1x1, 1x2, 1x3 and 1x4. There is no carrier or 2x3 ship.
- `fighter.png`, `bomber.png`, `torpedo.png`, `mine.png`, `antiAir.png`, `radar.png`, `cannon.png`, `supply.png`: 256 x 256 RGBA.
- `torpedo-projectile.png`: 512 x 171 RGBA, actual underwater torpedo projectile, nose faces right. `torpedo.png` remains the torpedo aircraft.
- `explosion.png`, `splash.png`: 512 x 512 RGBA, 4 columns x 4 rows, 128 x 128 frame cells, row-major chronological order. Suggested playback 16 fps, one shot.
- `harbor.png`: 2172 x 724 panorama, no text or UI baked in.
- Static textures have safe transparent padding. Trim alpha at runtime before fitting into ship grid bounds. Use nearest-neighbor sampling.

## Processing

Raw files are preserved under `E:/Codex/Home/generated_images/01a18bba-9797-7e93-8207-1f39ecc93c24/`.

The bundled `generate2dsprite.py process` performs magenta keying, cell splitting, alpha cleanup and nearest-neighbor scaling. Static packs use fit-scale 0.90, component-mode largest, centered 256-pixel cells. FX use shared-scale, scale-strategy preserve, component-mode all and 128-pixel cells so temporal growth is not normalized away.

A deterministic final despill removes only pixels with `r > g + 30`, `b > g + 30` and `min(r,b) > 70`; this clears pink chroma fringes without removing red pennants or navy/grey outlines. Harbor uses palette quantization without dithering for a smaller crisp raster. Processing scripts and full QC outputs are in the ignored `.agents/naval-art` folder.

## Prompt Set

### Fleet Prop Pack

Raw: `exec-64c29640-725f-4e9a-bcf2-d14b021d84c0.png`

```text
Create a production-ready premium pixel-art naval tactics sprite prop pack. Image size 2048 by 1024 pixels, exactly 4 columns by 2 rows, each invisible cell 512 by 512. Solid perfectly uniform RGB #FF00FF magenta background everywhere outside sprites, no shadows, no sea, no ground, no labels, no text, no visible grid or borders.
ART STYLE: beautiful hand-pixelled high-quality 16-bit tactical strategy game art, chunky intentional pixel clusters, clean dark navy outlines, 4-tone volumetric steel-blue metal shading, warm golden wooden deck panels, ivory cabin windows, small vivid red pennants and red accent stripes. Confident cute military-cartoon silhouettes, meticulous legible pixel detail with little gun turrets, hatches, radar masts and rivets, balanced limited palette. Strict orthographic TOP-DOWN overhead view, all vehicles bow/nose facing UP, symmetric clean silhouettes. Not flat vector pictograms, not photorealistic, not smooth airbrush, not generic minimalist icons.
FIRST ROW left to right:
cell1: one compact chubby navy patrol boat for a 1x1 grid, recognizable bow and stern, squat rounded rectangular steelblue hull, warm wood deck, single small gun turret and tiny red flag.
cell2: one slender navy destroyer for a 1x2 grid, hull width 120 and height 280 logical pixels, wood deck with two gun turrets and central cream bridge.
cell3: one elongated navy cruiser for a 1x3 grid, hull width 95 and height 310 logical pixels, three gun turrets, detailed central bridge and red stern stripe.
cell4: one long powerful battleship for a 1x4 grid, hull width 80 and height 340 logical pixels, four little turrets, wood deck and steelblue bridge.
SECOND ROW left to right:
cell1 one steelblue small fighter aircraft, top-down nose UP, red wing tips and ivory cockpit, angular readable wings.
cell2 one larger two-engine steelblue bomber aircraft top-down nose UP, broad wings with red accents, engines and tiny propellers.
cell3 one steelblue torpedo aircraft top-down nose UP, slim wing shape, visible brass torpedo slung along belly, red tail accents.
cell4 one round dark steelblue sea mine, full topdown with 8 chunky contact prongs, one small red light and rich pixel shading.
Each subject is exactly centered in its own invisible cell, fully contained in central 70% safe area, consistent pixel scale. All 8 sprites must be separate, not touching. Largest ship extends only central70% cell height. Detailed enough for a polished indie naval game.
```

### Explosion

Raw: `exec-965aa587-5ce9-4a7e-98f5-78d86738f842.png`

```text
Production game asset: beautiful 16-bit pixel-art EXPLOSION animation sprite sheet, one continuous single naval artillery impact then fireball burst then smoke dissipation action. Exact4columns4rows,16 animation frames read left-to-right top-to-bottom, invisible square cell grid. Square 1024by1024 canvas, each cell256. Completely flat solid pure #FF00FF magenta chroma-key background between and around all frames. No gridlines, no borders, no labels, no numbers, no text, no ground, no vessel, no shadow, no weapon or aircraft. Frames are isolated explosion FX only. Entire sequence centered at same position in every cell with stable scale. Each entire burst including sparks and debris must be contained inside central70% of each cell.
STYLE: gorgeous hand-pixelled tactical war-game FX with chunky 3-5pixel pixel clusters, clean crisp hard pixel edges, intentional limited palette, expressive cartoon shapes. Navy/charcoal outlines for smoke, whitehot core, lemonyellow flash, amber/orange flame edges, deep red hot debris, violet-grey voluminous curling smoke. No smooth airbrush, no blurred glows, no photographic textures. Orthographic topdown overhead effect, seen as circular explosion on ocean.
FRAME PROGRESSION: frames1-2 tiny brilliant white-yellow impact flash and compact sparks; frames3-4 fiery compact expanding star; frames5-7 large round roiling orange/yellow fireball with blocky flame tongues and small flying dark debris; frames8-10 expanding billowing grey smoke pillows with shrinking orange embers; frames11-13 broken grey smoke puffs moving slightly outward; frames14-16 tiny fading smoke pieces and dark debris disappearing. Final frame almost empty, first frame small. One smooth believable energetic action, NOT16unrelated explosions. Bold polished indie-game asset, readable even reduced to128px cell.
```

### Splash

Raw: `exec-06ed3dac-9eab-4ad9-a713-ccfb867eec34.png`

```text
Create a premium 16-bit hand-pixelled game SPRITESHEET for one single continuous NAVAL SHELL WATER SPLASH animation. Square canvas1024by1024,exact4columnsby4rows of invisible equal256x256cells,16 chronological frames lefttoright toptobottom. Each frame on perfectly solid pure #FF00FF magenta background for chromakey cleanup. No text,labels,numbers,visible lines,borders,ship,bullet,land,shadow. TOPDOWN orthographic camera looking straight down at blue sea spray; there must be NO background sea tile, ONLY isolated SPLASH AND RIPPLES against magenta.
Art direction: crisp deliberate chunky pixel clusters, professional detailed SNES era cute naval tactical game effect. White ivory highlight foam, pale icycyan mids, saturated azure and darkteal deep droplets with darkoutline occasional. Bright readable energetic contact, not neon magical or crystal. Foam is natural irregular bubbling water, never ice shards. Pixel-perfect edges, no blur, no transparentglow gradients.
Motion: frame1 tiny white point impact with cyanring; frames2-4 quickly rising circular foamy impact crown throwing small droplets; frames5-7 larger whitecyan water crown spreading outward with irregular splash tongues; frames8-10 lower broad watery ripples, foam collapsing; frames11-13 expanding broken circular cyan/white foam rings; frames14-16 fading little separated foam pixels and droplets thenalmostempty. Ripples only as small thin sprite details, no full water square background. Same stable center in every frame, no positional drift. Entire effect including droplets inside central72% of each cell with ample magenta margin. Natural smooth one-shot sequence, pixel detail suited for being downsampled to128x128frames.
```

### Harbor

Raw: `exec-9805e0db-a616-4637-8c10-5d8273e0136f.png`

```text
A polished panoramic PIXEL ART harbor illustration for a naval tactics game lobby. Very wide3:1 landscape composition,1920by640canvas. BEAUTIFUL deliberate 16-bit pixel art, chunky crisp pixel clusters and confident darknavy outlines, limited harmonious palette, no anti-aliased vectorlook, no blur, no photorealistic or3Drender. Top-down near-orthographic overhead viewpoint as in a premium pixel strategy game map, visually rich and carefully hand-crafted, cozy militarycartoon style.
A charming small fortified naval port in bright clear daylight. Deep turquoise and jade water filling central and right two-thirds with many subtle tiny white pixelwavelets; sandycream stone quays and wooden jetties at left and along bottom; redroof compact naval warehouse, small bluewhite coastal controltower with brassradar dish, neat cargo crates and ropebollards. Three beautiful steel-blue warships with warm wooden decks, small cream bridges, navygun turrets and tiny redpennants moored in different visible docks: a compact patrolboat, a slender destroyer, one long battleship. Allships should be accurate top-down visible hulls rather than generic rectangles. A small steelblue fighter plane on a bottomleft taxiway, no carrier. Green trees on left edges and small orange navigation markers. Dark outlines, sunlit clean highlights, warm wooden decks contrasted against vivid tealwater and coralred roofs. Tiny detailed harbor props but no people, no creatures, no lettering, no flags with real-world symbols. No UI, no text, no logos, no frame, no border, no giant compass, no gradientbackground. Keep broadcentral open water for a clean readable game setting. Cinematic yet friendly pixel-art scene, extremelywellcrafted, clear approachable tactical game world.
```

### Equipment

Raw: `exec-d1a23ae7-e2d3-4de0-8f93-89fda951bb81.png`

```text
Use attached naval sprite image ONLY as a style and color reference. Create a NEW matching premium naval-tactics prop sprite pack, exactly2columnsby2rows invisibleequalcells on a square1024by1024canvas. Solid perfectly uniform RGB#FF00FF magenta background outside all4sprites; no shadows,sea,ground,letters,numbers,lines,labels,frame,border. Beautiful meticulous16-bit chunky PIXELART with crisp navy darkoutlines, purposeful pixelclusters, steelbluemetal with goldenbrass details, creamhighlights, little vividredaccent. Notvectoricons,notblurry,pixelpaintedindiegameprops. Strict orthographic topdown camera, allprops wellcentered and fullycontained central68% safearea of their512cells.
Top-left: one handsome naval ANTIAIRGUN turret: squat octagonal steel-blue armoredbase, rotating brasspivot, two parallel darksteel gunbarrels pointingUP, small creamshield panels, redpaintstripe, recognizablenavaldefenseartillery. Powerful gun silhouette, manyhandpixelleddetailedrivetclusters, nohuman,noaircraft,noattachedground.
Top-right: one small NAVAL RADAR station: steelblue octagonal pedestal, goldenbrass curved scanning dish and thin antennaarm pointingUP, palecreamhardware panels, smallgreenradarcontrolscreen inset inbase, tinyredindicator; compactcohesive fulltopdownsprite notflatgreenradarUI.
Bottom-left: one steelblue NAVAL CANNON turret with twin longbarrels facingUP, creamshield, brasspivot, redstripe, matchingfleetart.
Bottom-right: one sturdy wooden SUPPLYCRATE, warm woodplank panels, steelblue cornerbraces, goldenbrass latches, vividredcrosspaintmark but no letters,numbers,logos.
Eachspriteispolished,richerthanapictogram,cleanreadablesilhouette,allfullyincell,consistentstylewithreference. FOURSEPARATESPRITESONLY.
```

### Torpedo Projectile

Generated with the built-in image-generation tool on 2026-10-30, transparent background. Raw: `E:/Codex/Home/generated_images/01a18cd7-824b-7c00-99ea-b09fe2738816/exec-ca19cbed-bbde-456a-a452-fe8ad10c229e.png`. Deterministically resized with nearest-neighbor sampling from 2172 x 724 to 512 x 171. Runtime trims transparent margins and fits the projectile to 45 x 15 logical pixels.

```text
Create ONE production-ready transparent-background pixel-art naval TORPEDO PROJECTILE sprite for a polished 16-bit top-down naval tactics game. This is the weapon itself, NOT an aircraft, no wings and no ship. Strict orthographic overhead view; long horizontal cylindrical steel-blue torpedo body pointing RIGHT, a slightly pointed copper/red-orange warhead at the right, brass tail propeller and small stabilizing fins at the left, cream metal highlights, dark navy outlines, deliberate chunky pixel clusters and a small limited color palette. Body silhouette must be clearly readable at 44 by 14 logical pixels. No text, no numbers, no labels, no grid, no ground, no cast shadow, no bubbles, no trail, no explosion, no scenery. Exactly one clean centered torpedo with generous transparent margin around it. The body should occupy the central 75 percent of image width and central 28 percent of image height. Premium cartoon military strategy pixel sprite, crisp hard pixels, not a flat vector icon, not a photo, not blurred.
```


