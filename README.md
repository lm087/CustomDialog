# Custom Dialog

An Android app for customizing dialogs and showing them in-app or cross-apps. Supports Android 6.0+.

## Features

- AlertDialog, Items, Single choice, Multi choice, EditText, DatePickerDialog and TimePickerDialog.
- Custom titles, messages, button labels, choices, input hints/defaults and cancelability.
- **In-App** opens an immediate preview without requesting permissions.
- **Cross-App** shows a dialog over other apps, immediately or after a delay of up to 86400 seconds. It requests overlay permission when needed. Return after granting permission and tap Cross-App again.
- Cancel an active countdown from the app or its notification. (optional)

Device sleep can delay a countdown. Lock screens and protected apps may hide overlays. Force-stop, service termination and reboot do not restore a countdown.