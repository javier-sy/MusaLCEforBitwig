# Musa Live Coding Environment for Bitwig Studio

**Bitwig Studio Controller for Musa Live Coding Environment.**

This project is part of **Musa Live Coding Environment**.

Musa Live Coding Environment is a suite of components to add a live coding environment based on [Musa-DSL](https://github.com/javier-sy/musa-dsl) to several DAW's (Ableton Live 11 or higher and Bitwig Studio 5 or higher, for the moment).

The suite is composed of:
- Visual Studio Code (where the user edits the source code of the composition and can submit commands to run in real time).
- [MusaLCEClientForVSCode](https://github.com/javier-sy/MusaLCEClientForVSCode) extension for Visual Studio Code (that allows the editor to communicate with the server).
- [musalce-server](https://github.com/javier-sy/musalce-server) processing server (the server that interprets the source code and the commands submitted by the user).
- [MusaLCEforLive](https://github.com/javier-sy/MusaLCEforLive) Ableton Live MIDI Remote Script (that establishes the communication between Ableton Live and the server).
- [MusaLCEforBitwig](https://github.com/javier-sy/MusaLCEforBitwig) Bitwig Studio Controller Extension (that establishes the communication between Bitwig Studio and the server).

You need also, of course, Ableton Live 11 or greater or Bitwig Studio 5 or greater.

MusaLCEforBitwig (this component) integrates with Bitwig Studio as a MIDI Controller Script.

## Install
**TODO**

## Usage
**TODO**

## Documentation
**TODO**

## Acknowledgements

Thanks to Jürgen Moßgraber ([Youtube Channel](https://www.youtube.com/channel/UCMgtq3iKqYamt9C-xbxwjTA)) for his Bitwig Controller API Tutorial. It has been a great source for understanding Bitwig API and starting this project.


# License

[MusaLCEforBitwig](https://github.com/javier-sy/MusaLCEforBitwig) Copyright (c) 2021-2026 [Javier Sánchez Yeste](https://yeste.studio), licensed under GPL 3.0 License