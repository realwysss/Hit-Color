<p align="center"><img src="icon.png" width="128"></p>

# Hit Color

change the red flash on mobs and players when they get hit. pick any color and opacity, or a gradient that runs from the feet to the head.

client side fabric mod for 1.21.11, 26.1 and 26.2. press **H** to open the settings (rebind it in Controls), or use Mod Menu. the settings menu is built into the mod, it only needs fabric api. color picker with hex field, a live preview, sliders and switches.

- **color**: any color and opacity
- **gradient**: a second color, blended from the bottom of the body to the top
- **armor**: tint the armor too, not just the skin
- **fade out**: the flash fades away instead of cutting off
- **show on**: choose players, mobs and yourself separately

## download

| jar | minecraft |
|---|---|
| `hit-color-mc1.21.11-1.0.0.jar` | 1.21.11 |
| `hit-color-mc26.1-1.0.0.jar` | 26.1 |
| `hit-color-mc26.2-1.0.0.jar` | 26.2 |

on the releases page. fabric loader 0.16 or newer, fabric api.

## pictures

| vanilla | default | blue |
|---|---|---|
| ![vanilla](screenshots/off.png) | ![default](screenshots/default.png) | ![blue](screenshots/blue.png) |

| gradient | gradient with armor |
|---|---|
| ![gradient](screenshots/gradient.png) | ![armor](screenshots/armor.png) |

![settings](screenshots/settings.png)

## why it does not lag

- the flash is the game's own hit overlay, the mod only changes its colors, so there is no extra drawing
- the overlay colors are rewritten only when you change a setting
- when the mod is off it does nothing

all rights reserved. made by wysss.
