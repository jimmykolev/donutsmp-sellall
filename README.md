# DonutSMP Sell All

![Build](https://github.com/jimmykolev/donutsmp-sellall/actions/workflows/build.yml/badge.svg)

A client-side Fabric 1.21.1 mod that lists matching unstackable inventory items on the auction house one at a time.

## Requirements

- Minecraft Java Edition 1.21.1
- Fabric Loader
- Fabric API
- Java 21
- DonutSMP Fast Sell enabled

## Usage

1. Enable Fast Sell on DonutSMP with `/ah fastsell` (or the equivalent Fast Sell setting).
2. Hold one of the items you want to list.
3. Run `/sellall 67000`.
4. Use `/sellallcancel` at any time to stop.

An optional delay can be supplied in ticks:

```
/sellall 67000 15
```

The default is 10 ticks (two listings per second). The allowed range is 5–200 ticks.

## Safety

- Only unstackable items are supported, preventing a whole stack being listed at the price intended for one item.
- Matching includes item components, so differently named, enchanted, damaged, or customised items are not included accidentally.
- The job pauses while a screen is open.
- If a listing does not remove the held item within five seconds, the job stops.
- The original selected hotbar slot is restored when the job finishes.

Check DonutSMP's current rules before using automation-assisted features. Server commands and interfaces may change.

## Building

```
gradle build
```

The compiled mod is written to `build/libs/donutsmp-sellall-1.0.0.jar`.
