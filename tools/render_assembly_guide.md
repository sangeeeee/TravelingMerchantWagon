# Assembly illustrations

Six successive assembly views of the ordinary oak cargo body, single wooden
driver's seat, and single-horse shafts. Each image shows the state after adding
the indicated component(s), while preserving the same front-left overhead
orthographic camera and scale. The front of the wagon points toward the lower
left of the image.

The cyan silhouette marks the new component. Dashed circles show the actual
locations of the right-side wheels, which are hidden from this viewpoint.
Each axle needs two wheels: small wheels in front, large wheels in back.

| Step | Image | Action |
| --- | --- | --- |
| 1 | `01_frame.png` | Place an extended assembly frame using ordinary placement. |
| 2 | `02_body.png` | Use the cargo body on the frame's top platform. |
| 3 | `03_seat.png` | Install the driver's seat above the front footboard. |
| 4 | `04_shafts.png` | Install shafts at the front center, below the footboard. |
| 5 | `05_front_wheels.png` | Install a small wheel on each end of the front axle. |
| 6 | `06_rear_wheels.png` | Install a large wheel on each end of the rear axle. |

Once all required parts are installed, use the assembly frame to turn the wagon
into an entity. These illustrations cover assembly only; harnessing and driving
can be explained separately.

## Assets

- `assembly_overview_zh_cn.png`: Chinese overview for review, 1440 x 1160.
- Individual images in `docs/handbook/assembly/`: transparent 800 x 800 renders.
- `versions/mc-1.21.1/common/src/main/resources/assets/tm_wagon/textures/gui/handbook/assembly/`: six
  transparent 256 x 256 Patchouli textures; the illustration occupies the
  upper-left 200 x 200 area. They contain no embedded text, so titles and
  explanations can be localized separately.
- `manifest.json`: camera settings, ordered captions, and texture identifiers.

Example Patchouli image page:

```json
{
  "type": "patchouli:image",
  "images": ["tm_wagon:textures/gui/handbook/assembly/02_body.png"],
  "border": false
}
```

The images are ready for handbook entries; this change does not add chapters.

## Regenerate

Run `python tools/render_assembly_guide.py` from the project root with Pillow
installed. The renderer uses the existing Blockbench cuboids, hierarchical
pivots, UV mappings, and current oak component textures. It does not invent or
change geometry, and does not modify models or gameplay code. The Chinese
overview uses the Windows Microsoft YaHei font. Changes to the underlying model
or texture can be reflected by regenerating the illustrations.
