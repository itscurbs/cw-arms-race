# Carbon Fiber Production

Design notes for the carbon fiber chain. Nothing here is implemented yet except the coal crushing recipe.

## Concept

Pitch-based carbon fiber. Coal is destructively distilled into pitch, the pitch is extruded into fiber,
the fiber is oxidized, then carbonized in an oxygen-free kiln. The fiber is woven, impregnated with epoxy,
and pressed into sheets.

## Chain

```
Coal ──[Crushing Wheels]──> Coal Dust
Coal Dust ──[KILN · heated]──> Coal Pitch + Coke Dust
Coal Pitch ──[Press]──> Pitch Fiber
Pitch Fiber ──[Fan + lava]──> Stabilized Fiber
Stabilized Fiber + Coke Dust ──[KILN · heated]──> Carbon Fiber Tow
Carbon Fiber Tow ×N ──[WEAVER]──> Carbon Fiber Cloth
Slime + Cinder Flour ──[Mixer · heated]──> Epoxy
Carbon Fiber Cloth + Epoxy ──[Basin + Press + Heat]──> Carbon Fiber Sheet
Carbon Fiber Sheet ×2 ──[Crafting]──> Drone Frame
```

Only one fluid: epoxy. No prepreg step, no tar fluid.

## Machines

| Machine | Role | Notes |
|---|---|---|
| Crushing Wheels | Coal to dust | Stock Create. Millstone stays the dye machine |
| **Kiln** | Distillation and carbonization | Custom kinetic block with its own inventory |
| Mechanical Press | Extrude pitch into fiber | Stock Create |
| Encased Fan | Stabilize fiber in hot air | Stock Create, fan over lava |
| **Weaver** | Weave tow into cloth | Custom kinetic block, shaft from below |
| Mixer | Make epoxy | Stock Create, heated Basin |
| Basin + Press | Cure cloth into sheet | Stock Create, `create:compacting` with heat |

## Recipes

| Step | Type | Input | Output |
|---|---|---|---|
| Coal to dust | `create:crushing` | `minecraft:coal` | `armsrace:coal_dust` |
| Distill | `armsrace:kiln`, heated | `armsrace:coal_dust` | `armsrace:coal_pitch` + `armsrace:coke_dust` |
| Extrude | `create:pressing` | `armsrace:coal_pitch` | `armsrace:pitch_fiber` |
| Stabilize | `minecraft:blasting` | `armsrace:pitch_fiber` | `armsrace:stabilized_fiber` |
| Carbonize | `armsrace:kiln`, heated | `armsrace:stabilized_fiber` + `armsrace:coke_dust` | `armsrace:carbon_fiber_tow` |
| Graphitize | `armsrace:kiln`, superheated | `armsrace:carbon_fiber_tow` | `armsrace:graphitized_tow` |
| Weave | `armsrace:weaving` | `armsrace:carbon_fiber_tow` | `armsrace:carbon_fiber_cloth` |
| Epoxy | `create:mixing`, heated | `minecraft:slime_ball` + `create:cinder_flour` | 250 mB `armsrace:epoxy` |
| Sheet | `create:compacting`, heated | `armsrace:carbon_fiber_cloth` + 250 mB epoxy | `armsrace:carbon_fiber_sheet` |

## Kiln recipe format

```json
{
  "type": "armsrace:kiln",
  "heat_requirement": "heated",
  "processing_time": 200,
  "ingredients": [
    { "item": "armsrace:coal_dust" }
  ],
  "results": [
    { "id": "armsrace:coal_pitch" },
    { "id": "armsrace:coke_dust" }
  ]
}
```

Heat levels reuse Create's `HeatCondition`: `heated`, `superheated`.

## Numbers

- Distill: 200 ticks, 1 pitch + 2 coke
- Extrude: 1 pitch to 1 fiber
- Stabilize: 100 ticks
- Carbonize: 400 ticks, consumes 1 coke per item
- Net: 1 coal dust becomes 1 tow plus 1 spare coke for the Blaze Burners
- Drone frame: 2 sheets, 1 sheet is 1 cloth, cloth is 4 tow

## Verified facts

- `data/<namespace>/recipe/` is singular in 1.21.1, not `recipes/`
- Create owns `create:milling` for coal and charcoal, both produce black dye, so coal dust must come from crushing
- Fan blasting is not a Create recipe type; fans read `minecraft:blasting`
- `create:compacting` supports `heat_requirement` because `BasinRecipe.canRequireHeat()` returns true by default
- Cinder flour comes from crushing netherrack

## Open questions

- Weaver: single block or multiblock, and how many tow per cloth
- Whether to block the Blast Furnace shortcut for stabilization
- Graphitization in v1 or later
- JEI integration for the custom kiln and weaving recipe types
- Coke Dust has no burn time yet, so the spare coke cannot fuel the Blaze Burners
