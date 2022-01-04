# amoquette
A foreground service for Android, wrapping the Moquette MQTT broker and displaying basic diagnostic information about it.

## Getting Started

Just build it using Android Studio.

### Prerequisites

* Android Studio. I used Arctic Fox 2020.3.1 Patch 4 (November 21, 2021)
* Emulator or real device running Android 10 or higher

### Installation

* Open the project and build it (Build variants are debug and release).
* Run the app on the emulator or on real hardware

## Usage

* Use Settings menu to alter various properties. Note that changes do not take effect until the broker is (re)started. Use the power button in the top right of the app's main activity to stop or start the broker.
* The python folder in this repository contains a script that demonstrates communication with the broker, measuring roundtrip delay of 'heartbeat' messages.

## Deployment

The Settings activity contains a wake lock duration property. Note that this duration affects battery life.

### Branches

* main

## Additional Documentation and Acknowledgments

* This project uses Moquette (https://github.com/moquette-io/moquette)